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
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceType;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryRequest;
import org.wildfly.discovery.spi.DiscoveryResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemotingEJBDiscoveryProvider implements DiscoveryProvider {
    static final RemotingEJBDiscoveryProvider INSTANCE = new RemotingEJBDiscoveryProvider();

    private RemotingEJBDiscoveryProvider() {
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
        final Endpoint endpoint = Endpoint.getCurrent();
        final List<EJBClientConnection> connections = ejbClientContext.getConfiguredConnections();
        if (connections.isEmpty()) {
            result.complete();
            return DiscoveryRequest.NULL;
        }
        final AtomicInteger connectionCount = new AtomicInteger(connections.size() + 1);
        final List<Runnable> cancellers = Collections.synchronizedList(new ArrayList<>());
        for (EJBClientConnection connection : connections) {
            final URI uri = connection.getDestination();
            final String scheme = uri.getScheme();
            if (scheme == null || ! ejbReceiver.getRemoteTransportProvider().supportsProtocol(scheme) || ! endpoint.isValidUriScheme(scheme)) {
                continue;
            }
            final IoFuture<Connection> future = doPrivileged((PrivilegedAction<IoFuture<Connection>>) () -> endpoint.getConnection(uri));
            cancellers.add(future::cancel);
            future.addNotifier(new IoFuture.HandlingNotifier<Connection, DiscoveryResult>() {
                public void handleCancelled(final DiscoveryResult discoveryResult) {
                    countDown(connectionCount, discoveryResult);
                }

                public void handleFailed(final IOException exception, final DiscoveryResult discoveryResult) {
                    countDown(connectionCount, discoveryResult);
                }

                public void handleDone(final Connection data, final DiscoveryResult discoveryResult) {
                    final IoFuture<EJBClientChannel> future = ejbReceiver.serviceHandle.getClientService(data, OptionMap.EMPTY);
                    cancellers.add(future::cancel);
                    future.addNotifier(new IoFuture.HandlingNotifier<EJBClientChannel, DiscoveryResult>() {
                        public void handleCancelled(final DiscoveryResult discoveryResult) {
                            countDown(connectionCount, discoveryResult);
                        }

                        public void handleFailed(final IOException exception, final DiscoveryResult discoveryResult) {
                            countDown(connectionCount, discoveryResult);
                        }

                        public void handleDone(final EJBClientChannel clientChannel, final DiscoveryResult discoveryResult) {
                            final DiscoveryRequest request = clientChannel.getDiscoveryProvider().discover(serviceType, filterSpec, new DiscoveryResult() {
                                public void complete() {
                                    countDown(connectionCount, discoveryResult);
                                }

                                public void addMatch(final ServiceURL serviceURL) {
                                    discoveryResult.addMatch(serviceURL);
                                }
                            });
                            cancellers.add(request::cancel);
                        }
                    }, discoveryResult);
                }

            }, result);
        }
        countDown(connectionCount, result);
        return () -> {
            synchronized (cancellers) {
                for (Runnable canceller : cancellers) {
                    canceller.run();
                }
            }
        };
    }

    static void countDown(final AtomicInteger connectionCount, final DiscoveryResult discoveryResult) {
        if (connectionCount.decrementAndGet() == 0) {
            discoveryResult.complete();
        }
    }
}
