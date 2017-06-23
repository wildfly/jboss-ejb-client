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
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBModuleIdentifier;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
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
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * Provides discovery service based on all known EJBClientChannel service registry entries.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemotingEJBDiscoveryProvider implements DiscoveryProvider, DiscoveredNodeRegistry {

    private final ConcurrentHashMap<String, NodeInformation> nodes = new ConcurrentHashMap<>();

    private final Set<URI> failedDestinations = Collections.newSetFromMap(new ConcurrentHashMap<URI, Boolean>());

    private final ConcurrentHashMap<String, Set<String>> clusterNodes = new ConcurrentHashMap<>();

    public RemotingEJBDiscoveryProvider() {
        Endpoint.getCurrent(); //this will blow up if remoting is not present, preventing this from being registered
    }

    public NodeInformation getNodeInformation(final String nodeName) {
        return nodes.computeIfAbsent(nodeName, NodeInformation::new);
    }

    public List<NodeInformation> getAllNodeInformation() {
        return new ArrayList<>(nodes.values());
    }

    public void addNode(final String clusterName, final String nodeName) {
        clusterNodes.computeIfAbsent(clusterName, ignored -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(nodeName);
    }

    public void removeNode(final String clusterName, final String nodeName) {
        clusterNodes.getOrDefault(clusterName, Collections.emptySet()).remove(nodeName);
    }

    public void removeCluster(final String clusterName) {
        final Set<String> removed = clusterNodes.remove(clusterName);
        if (removed != null) removed.clear();
    }

    public DiscoveryRequest discover(final ServiceType serviceType, final FilterSpec filterSpec, final DiscoveryResult result) {
        if (! serviceType.implies(ServiceType.of("ejb", "jboss"))) {
            // only respond to requests for JBoss EJB services
            result.complete();
            return DiscoveryRequest.NULL;
        }
        final EJBClientContext ejbClientContext = getCurrent();
        final RemoteEJBReceiver ejbReceiver = ejbClientContext.getAttachment(RemoteTransportProvider.ATTACHMENT_KEY);
        if (ejbReceiver == null) {
            // ???
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
                continue;
            }
            discoveryConnections = true;
            final URI uri = connection.getDestination();
            if (failedDestinations.contains(uri)) {
                continue;
            }
            ok = true;
            discoveryAttempt.connectAndDiscover(uri);
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
                                final InetSocketAddress destination = mapping.getValue();
                                final InetSocketAddress source = ejbReceiver.getSourceAddress(destination);
                                if (source == null ? mapping.getRange().getNetmaskBits() == 0 : source.equals(destination)) {
                                    try {
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
                                        if (! failedDestinations.contains(uri)) {
                                            maxConnections--;
                                            discoveryAttempt.connectAndDiscover(uri);
                                            ok = true;
                                            continue nodeLoop;
                                        }
                                    } catch (URISyntaxException e) {
                                        // ignore URI and try the next one
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // special second pass - retry everything because all were marked failed
        if (discoveryConnections && ! ok) {
            for (EJBClientConnection connection : configuredConnections) {
                if (! connection.isForDiscovery()) {
                    continue;
                }
                discoveryAttempt.connectAndDiscover(connection.getDestination());
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
                    failedDestinations.add(destination);
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
                    failedDestinations.add(destination);
                    countDown();
                }

                public void handleDone(final EJBClientChannel clientChannel, final URI destination) {
                    failedDestinations.remove(destination);
                    countDown();
                }
            };
        }

        void connectAndDiscover(URI uri) {
            final String scheme = uri.getScheme();
            if (scheme == null || ! ejbReceiver.getRemoteTransportProvider().supportsProtocol(scheme) || ! endpoint.isValidUriScheme(scheme)) {
                countDown();
                return;
            }
            outstandingCount.getAndIncrement();
            final IoFuture<ConnectionPeerIdentity> future = doPrivileged((PrivilegedAction<IoFuture<ConnectionPeerIdentity>>) () -> endpoint.getConnectedIdentity(uri, "ejb", "jboss", authenticationContext));
            onCancel(future::cancel);
            future.addNotifier(outerNotifier, uri);
        }

        void countDown() {
            if (outstandingCount.decrementAndGet() == 0) {
                final DiscoveryResult result = this.discoveryResult;
                final String node = filterSpec.accept(NODE_EXTRACTOR);
                final EJBModuleIdentifier module = filterSpec.accept(MI_EXTRACTOR);
                if (phase2) {
                    if (node != null) {
                        final NodeInformation information = nodes.get(node);
                        if (information != null) information.discover(serviceType, filterSpec, result);
                    } else for (NodeInformation information : nodes.values()) {
                        information.discover(serviceType, filterSpec, result);
                    }
                    result.complete();
                } else {
                    boolean ok = false;
                    // optimize for simple module identifier and node name queries
                    if (node != null) {
                        final NodeInformation information = nodes.get(node);
                        if (information != null) {
                            if (information.discover(serviceType, filterSpec, result)) {
                                ok = true;
                            }
                        }
                    } else for (NodeInformation information : nodes.values()) {
                        if (information.discover(serviceType, filterSpec, result)) {
                            ok = true;
                        }
                    }
                    if (ok) {
                        result.complete();
                    } else {
                        // everything failed.  We have to reconnect everything.
                        Set<URI> everything = new HashSet<>();
                        for (EJBClientConnection connection : ejbReceiver.getReceiverContext().getClientContext().getConfiguredConnections()) {
                            if (connection.isForDiscovery()) {
                                everything.add(connection.getDestination());
                            }
                        }
                        outer: for (NodeInformation information : nodes.values()) {
                            for (NodeInformation.ClusterNodeInformation cni : information.getClustersByName().values()) {
                                final Map<String, CidrAddressTable<InetSocketAddress>> atm = cni.getAddressTablesByProtocol();
                                for (Map.Entry<String, CidrAddressTable<InetSocketAddress>> entry2 : atm.entrySet()) {
                                    final String protocol = entry2.getKey();
                                    final CidrAddressTable<InetSocketAddress> addressTable = entry2.getValue();
                                    for (CidrAddressTable.Mapping<InetSocketAddress> mapping : addressTable) {
                                        final InetSocketAddress destination = mapping.getValue();
                                        final InetSocketAddress source = ejbReceiver.getSourceAddress(destination);
                                        if (source == null ? mapping.getRange().getNetmaskBits() == 0 : source.equals(destination)) {
                                            try {
                                                final InetAddress destinationAddress = destination.getAddress();
                                                String hostName = Inet.getHostNameIfResolved(destinationAddress);
                                                if (hostName == null) {
                                                    if (destinationAddress instanceof Inet6Address) {
                                                        hostName = '[' + Inet.toOptimalString(destinationAddress) + ']';
                                                    } else {
                                                        hostName = Inet.toOptimalString(destinationAddress);
                                                    }
                                                }
                                                everything.add(new URI(protocol, null, hostName, destination.getPort(), null, null, null));
                                                continue outer;
                                            } catch (URISyntaxException e) {
                                                // ignore URI and try the next one
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // now connect them ALL
                        phase2 = true;
                        outstandingCount.incrementAndGet();
                        for (URI uri : everything) {
                            connectAndDiscover(uri);
                        }
                        countDown();
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
