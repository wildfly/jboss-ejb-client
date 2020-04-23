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
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.PrivilegedAction;

import javax.ejb.CreateException;
import javax.net.ssl.SSLException;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.AbstractInvocationContext;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKey;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.EJBReceiverSessionCreationContext;
import org.jboss.ejb.client.RequestSendFailedException;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.remoting3.ClientServiceHandle;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.common.Assert;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.http.HttpUpgrade;
import org.xnio.http.UpgradeFailedException;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class RemoteEJBReceiver extends EJBReceiver {
    private static final Logs log = Logs.MAIN;
    static final AttachmentKey<EJBClientChannel> EJBCC_KEY = new AttachmentKey<>();

    private final RemoteTransportProvider remoteTransportProvider;
    private final EJBReceiverContext receiverContext;
    private final RemotingEJBDiscoveryProvider discoveredNodeRegistry;

    final ClientServiceHandle<EJBClientChannel> serviceHandle;

    private final RetryExecutorWrapper retryExecutorWrapper = new RetryExecutorWrapper();

    RemoteEJBReceiver(final RemoteTransportProvider remoteTransportProvider, final EJBReceiverContext receiverContext, final RemotingEJBDiscoveryProvider discoveredNodeRegistry) {
        this.remoteTransportProvider = remoteTransportProvider;
        this.receiverContext = receiverContext;
        this.discoveredNodeRegistry = discoveredNodeRegistry;
        serviceHandle = new ClientServiceHandle<>("jboss.ejb", channel -> EJBClientChannel.construct(channel, this.discoveredNodeRegistry, retryExecutorWrapper));
    }

    final IoFuture.HandlingNotifier<ConnectionPeerIdentity, EJBReceiverInvocationContext> notifier = new IoFuture.HandlingNotifier<ConnectionPeerIdentity, EJBReceiverInvocationContext>() {
        public void handleDone(final ConnectionPeerIdentity peerIdentity, final EJBReceiverInvocationContext attachment) {
            serviceHandle.getClientService(peerIdentity.getConnection(), OptionMap.EMPTY).addNotifier((ioFuture, attachment1) -> {
                final EJBClientChannel ejbClientChannel;
                try {

                    ejbClientChannel = ioFuture.getInterruptibly();
                } catch (IOException e) {
                    // should generally not be possible but we should handle it cleanly regardless
                    attachment1.requestFailed(new RequestSendFailedException(e + "@" + peerIdentity.getConnection().getPeerURI(), false), retryExecutorWrapper.getExecutor(peerIdentity.getConnection().getEndpoint().getXnioWorker()));
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    attachment1.requestFailed(new RequestSendFailedException(e + "@" + peerIdentity.getConnection().getPeerURI(), false), retryExecutorWrapper.getExecutor(peerIdentity.getConnection().getEndpoint().getXnioWorker()));
                    return;
                }
                attachment1.getClientInvocationContext().putAttachment(EJBCC_KEY, ejbClientChannel);
                ejbClientChannel.processInvocation(attachment1, peerIdentity);
            }, attachment);

        }

        public void handleCancelled(final EJBReceiverInvocationContext attachment) {
            attachment.requestCancelled();
        }

        public void handleFailed(final IOException exception, final EJBReceiverInvocationContext attachment) {
            URI destination = attachment.getClientInvocationContext().getDestination();
            if (exception instanceof UpgradeFailedException ||
               exception instanceof HttpUpgrade.ConnectionClosedEarlyException) {
                Logs.REMOTING.error("Error in connecting to " + destination + " : Please check if the client and server are configured to use the same protocol and ports.");
            } else if (exception instanceof SSLException && exception.getMessage().equals("Unrecognized SSL message, plaintext connection?")) {
                Logs.REMOTING.error("Error in connecting to " + destination + " : The destination doesn't support SSL. Did you mean to use http protocol instead?");
            }
            attachment.requestFailed(new RequestSendFailedException("Destination @ " + destination, exception, false), retryExecutorWrapper.getExecutor(Endpoint.getCurrent().getXnioWorker()));
        }
    };

    RemoteTransportProvider getRemoteTransportProvider() {
        return remoteTransportProvider;
    }

    RemotingEJBDiscoveryProvider getDiscoveredNodeRegistry() {
        return discoveredNodeRegistry;
    }

    EJBReceiverContext getReceiverContext() {
        return receiverContext;
    }

    EJBClientChannel getClientChannel(final Connection connection) throws IOException {
        try {
            log.tracef("RemoteEJBReceiver %s getting client service channel %s", this, serviceHandle);
            return serviceHandle.getClientService(connection, OptionMap.EMPTY).getInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
    }

    protected void processInvocation(final EJBReceiverInvocationContext receiverContext) throws Exception {
        final AuthenticationContext authenticationContext = receiverContext.getAuthenticationContext();
        final IoFuture<ConnectionPeerIdentity> futureConnection = getConnection(receiverContext.getClientInvocationContext(), receiverContext.getClientInvocationContext().getDestination(), authenticationContext);
        // this actually causes the invocation to move forward
        futureConnection.addNotifier(notifier, receiverContext);
    }

    protected boolean cancelInvocation(final EJBReceiverInvocationContext receiverContext, final boolean cancelIfRunning) {
        try {
            final EJBClientChannel channel = receiverContext.getClientInvocationContext().getAttachment(EJBCC_KEY);
            return channel != null && channel.cancelInvocation(receiverContext, cancelIfRunning);
        } catch (Exception e) {
            return false;
        }
    }

    protected SessionID createSession(final EJBReceiverSessionCreationContext context) throws Exception {
        final StatelessEJBLocator<?> statelessLocator = context.getClientInvocationContext().getLocator().asStateless();
        final AuthenticationContext authenticationContext = context.getAuthenticationContext();
        try {
            IoFuture<ConnectionPeerIdentity> futureConnection = getConnection(context.getClientInvocationContext(), context.getClientInvocationContext().getDestination(), authenticationContext);
            final ConnectionPeerIdentity identity = futureConnection.getInterruptibly();
            final EJBClientChannel ejbClientChannel = getClientChannel(identity.getConnection());
            final StatefulEJBLocator<?> result = ejbClientChannel.openSession(statelessLocator, identity, context.getClientInvocationContext());

            return result.getSessionId();
        } catch (IOException e) {
            final RequestSendFailedException failed = new RequestSendFailedException("Failed to create stateful EJB: " + e.getMessage(), true);
            failed.initCause(e);
            throw failed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CreateException("Stateful EJB creation interrupted");
        }
    }

    protected InetSocketAddress getSourceAddress(final InetSocketAddress destination) {
        return Endpoint.getCurrent().getXnioWorker().getBindAddress(destination.getAddress());
    }

    protected boolean isConnected(final URI uri) {
        final IoFuture<ConnectionPeerIdentity> future = Endpoint.getCurrent().getConnectedIdentityIfExists(uri, "ejb", "jboss", AuthenticationContext.captureCurrent());
        try {
            return future != null && future.getStatus() == IoFuture.Status.DONE && future.get().getConnection().isOpen();
        } catch (IOException e) {
            // impossible
            throw Assert.unreachableCode();
        }
    }

    private IoFuture<ConnectionPeerIdentity> getConnection(final AbstractInvocationContext context, final URI target, @NotNull AuthenticationContext authenticationContext) throws Exception {
        Affinity affinity = context.getLocator().getAffinity();
        String cluster = (affinity instanceof ClusterAffinity) ? ((ClusterAffinity) affinity).getClusterName() : context.getInitialCluster();

        if (cluster != null) {
            if(System.getSecurityManager() == null) {
                return discoveredNodeRegistry.getConnectedIdentityUsingClusterEffective(Endpoint.getCurrent(), target, "ejb", "jboss", authenticationContext, cluster);
            } else {
                return doPrivileged((PrivilegedAction<IoFuture<ConnectionPeerIdentity>>) () ->
                        discoveredNodeRegistry.getConnectedIdentityUsingClusterEffective(Endpoint.getCurrent(), target, "ejb", "jboss", authenticationContext, cluster));
            }
        }

        if(System.getSecurityManager() == null) {
            return Endpoint.getCurrent().getConnectedIdentity(target, "ejb", "jboss", authenticationContext);
        } else {
            return doPrivileged((PrivilegedAction<IoFuture<ConnectionPeerIdentity>>) () -> Endpoint.getCurrent().getConnectedIdentity(target, "ejb", "jboss", authenticationContext));
        }
    }

    public void close() throws Exception {
        serviceHandle.closeChannel();
    }
}
