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

import java.io.IOException;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
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
final class RemotingEJBDiscoveryProvider implements DiscoveryProvider {
    static final RemotingEJBDiscoveryProvider INSTANCE = new RemotingEJBDiscoveryProvider();

    private static final Set<String> JUST_EJB_MODULE = Collections.singleton(EJBClientContext.FILTER_ATTR_EJB_MODULE);
    private static final Set<String> JUST_EJB_MODULE_DISTINCT = Collections.singleton(EJBClientContext.FILTER_ATTR_EJB_MODULE_DISTINCT);
    private static final Set<String> JUST_ATTR_CLUSTER = Collections.singleton(EJBClientContext.FILTER_ATTR_CLUSTER);
    private static final Set<String> JUST_ATTR_NODE = Collections.singleton(EJBClientContext.FILTER_ATTR_NODE);

    private RemotingEJBDiscoveryProvider() {
        Endpoint.getCurrent(); //this will blow up if remoting is not present, preventing this from being registered
    }

    public DiscoveryRequest discover(final ServiceType serviceType, final FilterSpec filterSpec, final DiscoveryResult result) {
        if (! serviceType.implies(ServiceType.of("ejb", "jboss"))) {
            // only respond to requests for JBoss EJB services
            result.complete();
            return DiscoveryRequest.NULL;
        }
        final EJBClientContext ejbClientContext = EJBClientContext.getCurrent();
        final RemoteEJBReceiver ejbReceiver = ejbClientContext.getAttachment(RemoteTransportProvider.ATTACHMENT_KEY);
        if (ejbReceiver == null) {
            // ???
            result.complete();
            return DiscoveryRequest.NULL;
        }
        if (! (filterSpec.mayMatch(JUST_EJB_MODULE) || filterSpec.mayMatch(JUST_EJB_MODULE_DISTINCT) || filterSpec.mayMatch(JUST_ATTR_CLUSTER) || filterSpec.mayMatch(JUST_ATTR_NODE))) {
            // this provider can only find EJB modules by name
            result.complete();
            return DiscoveryRequest.NULL;
        }
        final DiscoveryAttempt discoveryAttempt = new DiscoveryAttempt(serviceType, filterSpec, result, ejbReceiver, AuthenticationContext.captureCurrent());

        for (EJBClientConnection connection : ejbClientContext.getConfiguredConnections()) {
            discoveryAttempt.connectAndDiscover(connection);
        }
        // attempt to obtain connection information for any cluster nodes
        final DiscoveryRequest firstRequest = discoveryAttempt.getClusterProvider().discover(serviceType, FilterSpec.hasAttribute(EJBClientContext.FILTER_ATTR_NODE), new DiscoveryResult() {
            public void complete() {
                discoveryAttempt.countDown();
            }

            public void reportProblem(final Throwable description) {
                discoveryAttempt.reportProblem(description);
            }

            public void addMatch(final ServiceURL serviceURL) {
                EJBClientConnection.Builder nodeConnectionBuilder = new EJBClientConnection.Builder();
                nodeConnectionBuilder.setDestination(serviceURL.getLocationURI());
                discoveryAttempt.connectAndDiscover(nodeConnectionBuilder.build());
            }
        });
        discoveryAttempt.onCancel(firstRequest::cancel);
        return discoveryAttempt;
    }

    static final class DiscoveryAttempt implements DiscoveryRequest, DiscoveryResult {
        private final ServiceType serviceType;
        private final FilterSpec filterSpec;
        private final DiscoveryResult discoveryResult;
        private final RemoteEJBReceiver ejbReceiver;
        private final AuthenticationContext authenticationContext;

        private final Endpoint endpoint;
        private final DiscoveryProvider clusterProvider;
        private final AtomicInteger outstandingCount = new AtomicInteger(1); // this is '1' so that firstRequest above works
        private final List<Runnable> cancellers = Collections.synchronizedList(new ArrayList<>());
        private final IoFuture.HandlingNotifier<ConnectionPeerIdentity, Void> outerNotifier;
        private final IoFuture.HandlingNotifier<EJBClientChannel, Void> innerNotifier;

        DiscoveryAttempt(final ServiceType serviceType, final FilterSpec filterSpec, final DiscoveryResult discoveryResult, final RemoteEJBReceiver ejbReceiver, final AuthenticationContext authenticationContext) {
            this.serviceType = serviceType;
            this.filterSpec = filterSpec;
            this.discoveryResult = discoveryResult;
            this.ejbReceiver = ejbReceiver;

            clusterProvider = ejbReceiver.getRemoteTransportProvider().getClusterDiscoveryProvider();
            this.authenticationContext = authenticationContext;
            endpoint = Endpoint.getCurrent();
            outerNotifier = new IoFuture.HandlingNotifier<ConnectionPeerIdentity, Void>() {
                public void handleCancelled(final Void nothing) {
                    countDown();
                }

                public void handleFailed(final IOException exception, final Void nothing) {
                    DiscoveryAttempt.this.discoveryResult.reportProblem(exception);
                    countDown();
                }

                public void handleDone(final ConnectionPeerIdentity data, final Void nothing) {
                    final IoFuture<EJBClientChannel> future = DiscoveryAttempt.this.ejbReceiver.serviceHandle.getClientService(data.getConnection(), OptionMap.EMPTY);
                    onCancel(future::cancel);
                    future.addNotifier(innerNotifier, null);
                }
            };
            innerNotifier = new IoFuture.HandlingNotifier<EJBClientChannel, Void>() {
                public void handleCancelled(final Void nothing) {
                    countDown();
                }

                public void handleFailed(final IOException exception, final Void nothing) {
                    DiscoveryAttempt.this.discoveryResult.reportProblem(exception);
                    countDown();
                }

                public void handleDone(final EJBClientChannel clientChannel, final Void nothing) {
                    final DiscoveryRequest request = clientChannel.getDiscoveryProvider().discover(
                        DiscoveryAttempt.this.serviceType,
                        DiscoveryAttempt.this.filterSpec,
                        DiscoveryAttempt.this
                    );
                    onCancel(request::cancel);
                }
            };
        }

        void connectAndDiscover(EJBClientConnection connection) {
            if (! connection.isForDiscovery()) {
                return;
            }
            final URI uri = connection.getDestination();
            final String scheme = uri.getScheme();
            if (scheme == null || ! ejbReceiver.getRemoteTransportProvider().supportsProtocol(scheme) || ! endpoint.isValidUriScheme(scheme)) {
                return;
            }
            outstandingCount.getAndIncrement();
            final IoFuture<ConnectionPeerIdentity> future = doPrivileged((PrivilegedAction<IoFuture<ConnectionPeerIdentity>>) () -> endpoint.getConnectedIdentity(uri, "ejb", "jboss", authenticationContext));
            onCancel(future::cancel);
            future.addNotifier(outerNotifier, null);
        }

        void countDown() {
            if (outstandingCount.decrementAndGet() == 0) {
                // we don't need a canceller for this one because it's 100% in-memory
                clusterProvider.discover(serviceType, filterSpec, discoveryResult);
            }
        }

        DiscoveryProvider getClusterProvider() {
            return clusterProvider;
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
