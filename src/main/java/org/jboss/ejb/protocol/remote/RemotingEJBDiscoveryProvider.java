/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.ejb.protocol.remote;

import static java.security.AccessController.doPrivileged;
import static org.jboss.ejb.client.EJBClientContext.FILTER_ATTR_EJB_MODULE;
import static org.jboss.ejb.client.EJBClientContext.FILTER_ATTR_EJB_MODULE_DISTINCT;
import static org.jboss.ejb.client.EJBClientContext.FILTER_ATTR_NODE;
import static org.jboss.ejb.client.EJBClientContext.getCurrent;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb._private.SystemProperties;
import org.jboss.ejb.client.DiscoveryEJBClientInterceptor;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBModuleIdentifier;
import org.jboss.logging.Logger;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.common.Assert;
import org.wildfly.common.net.CidrAddressTable;
import org.wildfly.common.net.Inet;
import org.wildfly.discovery.AllFilterSpec;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.EqualsFilterSpec;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceType;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryRequest;
import org.wildfly.discovery.spi.DiscoveryResult;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.xnio.FailedIoFuture;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * Provides discovery service based on all known EJBClientChannel service registry entries.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemotingEJBDiscoveryProvider implements DiscoveryProvider, DiscoveredNodeRegistry {

    static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    private final ConcurrentHashMap<String, NodeInformation> nodes = new ConcurrentHashMap<>();

    private final Map<URI, Long> failedDestinations = new ConcurrentHashMap<URI, Long>();

    private final ConcurrentHashMap<String, Set<String>> clusterNodes = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, URI> effectiveAuthURIs = new ConcurrentHashMap<>();
    
    private static final long DESTINATION_RECHECK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(SystemProperties.getLong(SystemProperties.DESTINATION_RECHECK_INTERVAL, 5000L));

    public RemotingEJBDiscoveryProvider() {
        Endpoint.getCurrent(); //this will blow up if remoting is not present, preventing this from being registered
    }

    public NodeInformation getNodeInformation(final String nodeName) {
        return nodes.computeIfAbsent(nodeName, NodeInformation::new);
    }

    public List<NodeInformation> getAllNodeInformation() {
        return new ArrayList<>(nodes.values());
    }

    public void addNode(final String clusterName, final String nodeName, URI registeredBy) {
        effectiveAuthURIs.putIfAbsent(clusterName, registeredBy);
        clusterNodes.computeIfAbsent(clusterName, ignored -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(nodeName);
    }

    public void removeNode(final String clusterName, final String nodeName) {
        clusterNodes.getOrDefault(clusterName, Collections.emptySet()).remove(nodeName);
    }

    public void removeCluster(final String clusterName) {
        final Set<String> removed = clusterNodes.remove(clusterName);
        if (removed != null) removed.clear();
        effectiveAuthURIs.remove(clusterName);
    }
    
    private boolean haveNotExpiredFailedDestination(URI uri) {
    	if(!failedDestinations.containsKey(uri))
    		return false;
    	else {
    		long failureTimestamp = failedDestinations.get(uri);
    		long delta = System.nanoTime() - failureTimestamp;
    		return delta < DESTINATION_RECHECK_INTERVAL;
    	}
    }

    public DiscoveryRequest discover(final ServiceType serviceType, final FilterSpec filterSpec, final DiscoveryResult result) {
        if (! serviceType.implies(ServiceType.of("ejb", "jboss"))) {
            // only respond to requests for JBoss EJB services
            Logs.INVOCATION.tracef("EJB discovery provider: wrong service type(%s), returning!", serviceType.toString());
            result.complete();
            return DiscoveryRequest.NULL;
        }
        final EJBClientContext ejbClientContext = getCurrent();
        final RemoteEJBReceiver ejbReceiver = ejbClientContext.getAttachment(RemoteTransportProvider.ATTACHMENT_KEY);
        if (ejbReceiver == null) {
            // ???
            Logs.INVOCATION.tracef("EJB discovery provider: no EJBReceiver available, returning!");
            result.complete();
            return DiscoveryRequest.NULL;
        }

        final List<EJBClientConnection> configuredConnections = ejbClientContext.getConfiguredConnections();

        final DiscoveryAttempt discoveryAttempt = new DiscoveryAttempt(serviceType, filterSpec, result, ejbReceiver, AuthenticationContext.captureCurrent());

        boolean ok = false;
        boolean discoveryConnections = false;
        // first pass
        for (EJBClientConnection connection : configuredConnections) {
            if (! connection.isForDiscovery()) {
                Logs.INVOCATION.tracef("EJB discovery provider: found non-discovery connection, skipping");
                continue;
            }
            discoveryConnections = true;
            final URI uri = connection.getDestination();
            if (haveNotExpiredFailedDestination(uri)) {
                Logs.INVOCATION.tracef("EJB discovery provider: attempting to connect to configured connection %s, skipping because marked as failed", uri);
                continue;
            }
            ok = true;
            Logs.INVOCATION.tracef("EJB discovery provider: attempting to connect to configured connection %s", uri);
            discoveryAttempt.connectAndDiscover(uri, null);
        }
        // also establish cluster nodes if known
        for (Map.Entry<String, Set<String>> entry : clusterNodes.entrySet()) {
            final String clusterName = entry.getKey();
            final Set<String> nodeSet = entry.getValue();
            int maxConnections = ejbClientContext.getMaximumConnectedClusterNodes();
            nodeLoop: for (String nodeName : nodeSet) {
                if (maxConnections <= 0) break;
                final NodeInformation nodeInformation = nodes.get(nodeName);
                if (nodeInformation != null) {
                    final NodeInformation.ClusterNodeInformation clusterInfo = nodeInformation.getClustersByName().get(clusterName);
                    if (clusterInfo != null) {
                        final Map<String, CidrAddressTable<InetSocketAddress>> tables = clusterInfo.getAddressTablesByProtocol();
                        for (Map.Entry<String, CidrAddressTable<InetSocketAddress>> entry2 : tables.entrySet()) {
                            final String protocol = entry2.getKey();
                            final CidrAddressTable<InetSocketAddress> addressTable = entry2.getValue();
                            for (CidrAddressTable.Mapping<InetSocketAddress> mapping : addressTable) {
                                try {
                                    final InetSocketAddress destination = Inet.getResolved(mapping.getValue());
                                    final InetSocketAddress source = ejbReceiver.getSourceAddress(destination);
                                    if (source == null ? mapping.getRange().getNetmaskBits() == 0 : source.equals(destination)) {
                                        final InetAddress destinationAddress = destination.getAddress();
                                        String hostName = Inet.getHostNameIfResolved(destinationAddress);
                                        if (hostName == null) {
                                            if (destinationAddress instanceof Inet6Address) {
                                                hostName = '[' + Inet.toOptimalString(destinationAddress) + ']';
                                            } else {
                                                hostName = Inet.toOptimalString(destinationAddress);
                                            }
                                        }
                                        final URI uri = new URI(protocol, null, hostName, destination.getPort(), null, null, null);
                                        if (haveNotExpiredFailedDestination(uri)) {
                                            Logs.INVOCATION.tracef("EJB discovery provider: attempting to connect to cluster connection %s, skipping because marked as failed", uri);
                                        } else {
                                            maxConnections--;
                                            Logs.INVOCATION.tracef("EJB discovery provider: attempting to connect to cluster %s connection %s", clusterName, uri);
                                            discoveryAttempt.connectAndDiscover(uri, clusterName);
                                            ok = true;
                                            continue nodeLoop;
                                        }
                                    }
                                } catch (URISyntaxException e) {
                                    // ignore URI and try the next one
                                } catch (UnknownHostException e) {
                                    Logs.MAIN.logf(Logger.Level.DEBUG, "Cannot resolve %s host during discovery attempt, skipping", mapping.getValue());
                                }

                            }
                        }
                    }
                }
            }
        }
        // special second pass - retry everything because all were marked failed
        if (discoveryConnections && ! ok) {
            Logs.INVOCATION.tracef("EJB discovery provider: all discovery-enabled configured connections marked failed, retrying configured connections ...");
            for (EJBClientConnection connection : configuredConnections) {
                if (! connection.isForDiscovery()) {
                    continue;
                }
                URI destination = connection.getDestination();
                Logs.INVOCATION.tracef("EJB discovery provider: attempting to connect to connection %s", destination);
                discoveryAttempt.connectAndDiscover(destination, null);
            }
        }

        discoveryAttempt.countDown();
        return discoveryAttempt;
    }

    static EJBModuleIdentifier getIdentifierForAttribute(String attribute, AttributeValue value) {
        if (! value.isString()) {
            return null;
        }
        final String stringVal = value.toString();
        switch (attribute) {
            case FILTER_ATTR_EJB_MODULE: {
                final String[] segments = stringVal.split("/");
                final String app, module;
                if (segments.length == 2) {
                    app = segments[0];
                    module = segments[1];
                } else if (segments.length == 1) {
                    app = "";
                    module = segments[0];
                } else {
                    return null;
                }
                return new EJBModuleIdentifier(app, module, "");
            }
            case FILTER_ATTR_EJB_MODULE_DISTINCT: {
                final String[] segments = stringVal.split("/");
                final String app, module, distinct;
                if (segments.length == 3) {
                    app = segments[0];
                    module = segments[1];
                    distinct = segments[2];
                } else if (segments.length == 2) {
                    app = "";
                    module = segments[0];
                    distinct = segments[1];
                } else {
                    return null;
                }
                return new EJBModuleIdentifier(app, module, distinct);
            }
            default: {
                return null;
            }
        }
    }

    static final FilterSpec.Visitor<Void, EJBModuleIdentifier, RuntimeException> MI_EXTRACTOR = new FilterSpec.Visitor<Void, EJBModuleIdentifier, RuntimeException>() {
        public EJBModuleIdentifier handle(final EqualsFilterSpec filterSpec, final Void parameter) throws RuntimeException {
            return getIdentifierForAttribute(filterSpec.getAttribute(), filterSpec.getValue());
        }

        public EJBModuleIdentifier handle(final AllFilterSpec filterSpec, final Void parameter) throws RuntimeException {
            for (FilterSpec child : filterSpec) {
                final EJBModuleIdentifier match = child.accept(this);
                if (match != null) {
                    return match;
                }
            }
            return null;
        }
    };

    static final FilterSpec.Visitor<Void, String, RuntimeException> NODE_EXTRACTOR = new FilterSpec.Visitor<Void, String, RuntimeException>() {
        public String handle(final EqualsFilterSpec filterSpec, final Void parameter) throws RuntimeException {
            final AttributeValue value = filterSpec.getValue();
            return filterSpec.getAttribute().equals(FILTER_ATTR_NODE) && value.isString() ? value.toString() : null;
        }

        public String handle(final AllFilterSpec filterSpec, final Void parameter) throws RuntimeException {
            for (FilterSpec child : filterSpec) {
                final String match = child.accept(this);
                if (match != null) {
                    return match;
                }
            }
            return null;
        }
    };

    /**
     * This method gets a ConnectionPeerIdentity object for a remote connection to a destination, similar to Endpoint.getConnectedIdentity().
     * However, if we call it with a cluster name, indicating that we are connecting to a cluster, instead of looking up the AuthenticationContext
     * for the cluster member destination, it instead uses the AuthenticationContext for the URI which first connected to the cluster.
     * This is the cluster effective URI, in the sense that it is an effective URI (i.e. resolved) for a cluster - the same credentials will be used
     * no matter which cluster member we connect to.
     */
    IoFuture<ConnectionPeerIdentity> getConnectedIdentityUsingClusterEffective(Endpoint endpoint, URI destination, String abstractType, String abstractTypeAuthority, AuthenticationContext context, String clusterName) {
        Assert.checkNotNullParam("destination", destination);
        Assert.checkNotNullParam("context", context);

        URI effectiveAuth = clusterName != null ? effectiveAuthURIs.get(clusterName) : null;
        boolean updateAuth = effectiveAuth != null;

        if (!updateAuth) {
            effectiveAuth = destination;
        }

        final AuthenticationContextConfigurationClient client = AUTH_CONFIGURATION_CLIENT;
        final SSLContext sslContext;
        try {
            sslContext = client.getSSLContext(destination, context);
        } catch (GeneralSecurityException e) {
            return new FailedIoFuture<>(Logs.REMOTING.failedToConfigureSslContext(e));
        }
        final AuthenticationConfiguration authenticationConfiguration = client.getAuthenticationConfiguration(effectiveAuth, context, -1, abstractType, abstractTypeAuthority);
        return endpoint.getConnectedIdentity(destination, sslContext, updateAuth ? fixupOverrides(authenticationConfiguration, destination) : authenticationConfiguration);
    }

    // TODO remove this hack once ELY-1399 is fully completed, and nothing else
    // (e.g. naming) registers an override. This also works around REM3-315.
    private AuthenticationConfiguration fixupOverrides(AuthenticationConfiguration config, URI target) {
        return config.useProtocol(target.getScheme()).useHost(target.getHost()).usePort(target.getPort());
    }

    final class DiscoveryAttempt implements DiscoveryRequest, DiscoveryResult {
        private final ServiceType serviceType;
        private final FilterSpec filterSpec;
        private final DiscoveryResult discoveryResult;
        private final RemoteEJBReceiver ejbReceiver;
        private final AuthenticationContext authenticationContext;

        private final Endpoint endpoint;
        private final AtomicInteger outstandingCount = new AtomicInteger(1); // this is '1' so that we don't finish until all connections are searched
        private volatile boolean phase2;
        private final List<Runnable> cancellers = Collections.synchronizedList(new ArrayList<>());
        private final IoFuture.HandlingNotifier<ConnectionPeerIdentity, URI> outerNotifier;
        private final IoFuture.HandlingNotifier<EJBClientChannel, URI> innerNotifier;
        // keep a record of URIs we try to connect to for each cluster
        private final ConcurrentHashMap<String, Set<URI>> urisByCluster = new ConcurrentHashMap<>();
        private final Set<URI> connectFailedURIs = new HashSet<>();
        /**
         * nodes that have already been provided to the discovery provider eagerly
         */
        private final Set<String> eagerNodes;

        DiscoveryAttempt(final ServiceType serviceType, final FilterSpec filterSpec, final DiscoveryResult discoveryResult, final RemoteEJBReceiver ejbReceiver, final AuthenticationContext authenticationContext) {
            this.serviceType = serviceType;
            this.filterSpec = filterSpec;
            this.discoveryResult = discoveryResult;
            this.ejbReceiver = ejbReceiver;

            this.authenticationContext = authenticationContext;
            endpoint = Endpoint.getCurrent();
            outerNotifier = new IoFuture.HandlingNotifier<ConnectionPeerIdentity, URI>() {
                public void handleCancelled(final URI destination) {
                    countDown();
                }

                public void handleFailed(final IOException exception, final URI destination) {
                    DiscoveryAttempt.this.discoveryResult.reportProblem(exception);
                    failedDestinations.put(destination, System.nanoTime());
                    if (exception instanceof ConnectException) {
                        connectFailedURIs.add(destination);
                        Logs.INVOCATION.tracef("DiscoveryAttempt: got ConnectException on node with destination = %s", destination);
                    }
                    countDown();
                }

                public void handleDone(final ConnectionPeerIdentity data, final URI destination) {
                    final IoFuture<EJBClientChannel> future = DiscoveryAttempt.this.ejbReceiver.serviceHandle.getClientService(data.getConnection(), OptionMap.EMPTY);
                    onCancel(future::cancel);
                    future.addNotifier(innerNotifier, destination);
                }
            };
            innerNotifier = new IoFuture.HandlingNotifier<EJBClientChannel, URI>() {
                public void handleCancelled(final URI destination) {
                    countDown();
                }

                public void handleFailed(final IOException exception, final URI destination) {
                    DiscoveryAttempt.this.discoveryResult.reportProblem(exception);
                    failedDestinations.put(destination, System.nanoTime());
                    countDown();
                }

                public void handleDone(final EJBClientChannel clientChannel, final URI destination) {
                    failedDestinations.remove(destination);
                    countDown();
                }
            };

            eagerNodes = DiscoveryEJBClientInterceptor.getDiscoveryAdditionalTimeout() == 0 ? null : Collections.synchronizedSet(new HashSet<>());
        }

        void connectAndDiscover(URI uri, String clusterEffective) {
            final String scheme = uri.getScheme();
            if (scheme == null || ! ejbReceiver.getRemoteTransportProvider().supportsProtocol(scheme) || ! endpoint.isValidUriScheme(scheme)) {
                countDown();
                return;
            }
            outstandingCount.getAndIncrement();

            // keep a record of this URI if it has an associated cluster (EJBCLIENT-325)
            if (clusterEffective != null) {
                urisByCluster.computeIfAbsent(clusterEffective, ignored -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(uri);
            }

            final IoFuture<ConnectionPeerIdentity> future;
            if(System.getSecurityManager() == null) {
                future = getConnectedIdentityUsingClusterEffective(endpoint, uri, "ejb", "jboss", authenticationContext, clusterEffective);
            } else {
                future = doPrivileged((PrivilegedAction<IoFuture<ConnectionPeerIdentity>>) () -> getConnectedIdentityUsingClusterEffective(endpoint, uri, "ejb", "jboss", authenticationContext, clusterEffective));
            }
            onCancel(future::cancel);
            future.addNotifier(outerNotifier, uri);
        }

        void countDown() {
            if (outstandingCount.decrementAndGet() == 0) {

                // before calculating the search result, update DNR to remove crashed last members of clusters (EJBCLIENT-325)
                for (String cluster : urisByCluster.keySet()) {
                    Set<URI> uris = urisByCluster.get(cluster);
                      if (uris != null && uris.size() == 1) {
                        // get the URI and check if it represents a failed last cluster member
                        URI uri = uris.iterator().next();
                        if (connectFailedURIs.contains(uri)) {
                            Logs.INVOCATION.tracef("DiscoveryAttempt: countDown() found a cluster %s with one failed destination, %s, removing cluster", cluster, uri);
                            removeCluster(cluster);
                            for (NodeInformation nodeInformation : getAllNodeInformation()) {
                                nodeInformation.removeCluster(cluster);
                            }
                        }
                    }
                }

                final DiscoveryResult result = this.discoveryResult;
                final String node = filterSpec.accept(NODE_EXTRACTOR);
                final EJBModuleIdentifier module = filterSpec.accept(MI_EXTRACTOR);
                if (phase2) {
                    if (node != null) {
                        if (eagerNodes == null || !eagerNodes.contains(node)) {
                            final NodeInformation information = nodes.get(node);
                            if (information != null) information.discover(serviceType, filterSpec, result);
                        }
                    } else for (NodeInformation information : nodes.values()) {
                        if (eagerNodes == null || !eagerNodes.contains(information.getNodeName())) {
                            information.discover(serviceType, filterSpec, result);
                        }
                    }
                    result.complete();
                } else {
                    boolean ok = false;
                    // optimize for simple module identifier and node name queries
                    if (node != null) {
                        if (eagerNodes == null || !eagerNodes.contains(node)) {
                            final NodeInformation information = nodes.get(node);
                            if (information != null) {
                                if (information.discover(serviceType, filterSpec, result)) {
                                    ok = true;
                                }
                            }
                        }
                    } else for (NodeInformation information : nodes.values()) {
                        if (eagerNodes == null || !eagerNodes.contains(information.getNodeName())) {
                            if (information.discover(serviceType, filterSpec, result)) {
                                ok = true;
                            }
                        }
                    }
                    if (ok || (eagerNodes != null && !eagerNodes.isEmpty())) {
                        result.complete();
                    } else {
                        // everything failed.  We have to reconnect everything.
                        Set<URI> everything = new HashSet<>();
                        Map<URI, String> effectiveAuthMappings = new HashMap<>();
                        for (EJBClientConnection connection : ejbReceiver.getReceiverContext().getClientContext().getConfiguredConnections()) {
                            if (connection.isForDiscovery()) {
                                everything.add(connection.getDestination());
                            }
                        }
                        outer: for (NodeInformation information : nodes.values()) {
                            for (Map.Entry<String, NodeInformation.ClusterNodeInformation> entry : information.getClustersByName().entrySet()) {
                                String clusterName = entry.getKey();
                                NodeInformation.ClusterNodeInformation cni = entry.getValue();
                                final Map<String, CidrAddressTable<InetSocketAddress>> atm = cni.getAddressTablesByProtocol();
                                for (Map.Entry<String, CidrAddressTable<InetSocketAddress>> entry2 : atm.entrySet()) {
                                    final String protocol = entry2.getKey();
                                    final CidrAddressTable<InetSocketAddress> addressTable = entry2.getValue();
                                    for (CidrAddressTable.Mapping<InetSocketAddress> mapping : addressTable) {
                                        try {
                                            final InetSocketAddress destination = Inet.getResolved(mapping.getValue());
                                            final InetSocketAddress source = ejbReceiver.getSourceAddress(destination);
                                            if (source == null ? mapping.getRange().getNetmaskBits() == 0 : source.equals(destination)) {

                                                final InetAddress destinationAddress = destination.getAddress();
                                                String hostName = Inet.getHostNameIfResolved(destinationAddress);
                                                if (hostName == null) {
                                                    if (destinationAddress instanceof Inet6Address) {
                                                        hostName = '[' + Inet.toOptimalString(destinationAddress) + ']';
                                                    } else {
                                                        hostName = Inet.toOptimalString(destinationAddress);
                                                    }
                                                }
                                                URI location = new URI(protocol, null, hostName, destination.getPort(), null, null, null);
                                                effectiveAuthMappings.put(location, clusterName);
                                                everything.add(location);
                                                continue outer;
                                            }
                                        } catch (URISyntaxException | UnknownHostException e) {
                                            // ignore URI and try the next one
                                        }
                                    }
                                }
                            }
                        }
                        // now connect them ALL
                        phase2 = true;
                        outstandingCount.incrementAndGet();
                        for (URI uri : everything) {
                            if(!failedDestinations.containsKey(uri)) {
                                connectAndDiscover(uri, effectiveAuthMappings.get(uri));
                            }
                        }
                        countDown();
                    }
                }
            } else if (eagerNodes != null) {
                final DiscoveryResult result = this.discoveryResult;
                final String node = filterSpec.accept(NODE_EXTRACTOR);
                if (node != null) {
                    if (!eagerNodes.contains(node)) {
                        final NodeInformation information = nodes.get(node);
                        if (information != null) {
                            if (information.discover(serviceType, filterSpec, result)) {
                                eagerNodes.add(node);
                            }
                        }
                    }
                } else for (NodeInformation information : nodes.values()) {
                    if (!eagerNodes.contains(information.getNodeName())) {
                        if (information.discover(serviceType, filterSpec, result)) {
                            eagerNodes.add(information.getNodeName());
                        }
                    }
                }
            }
        }

        // discovery result methods

        public void complete() {
            countDown();
        }

        public void reportProblem(final Throwable description) {
            discoveryResult.reportProblem(description);
        }

        public void addMatch(final ServiceURL serviceURL) {
            discoveryResult.addMatch(serviceURL);
        }

        // discovery request methods

        public void cancel() {
            final List<Runnable> cancellers = this.cancellers;
            synchronized (cancellers) {
                for (Runnable canceller : cancellers) {
                    canceller.run();
                }
            }
        }

        void onCancel(final Runnable action) {
            final List<Runnable> cancellers = this.cancellers;
            synchronized (cancellers) {
                cancellers.add(action);
            }
        }
    }
}
