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
import javax.net.ssl.SSLContext;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKey;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.EJBReceiverSessionCreationContext;
import org.jboss.ejb.client.RequestSendFailedException;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.remoting3.ClientServiceHandle;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class RemoteEJBReceiver extends EJBReceiver {
    static final AttachmentKey<EJBClientChannel> EJBCC_KEY = new AttachmentKey<>();
    private static final AuthenticationContextConfigurationClient CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    private final RemoteTransportProvider remoteTransportProvider;
    private final EJBReceiverContext receiverContext;
    private final RemotingEJBDiscoveryProvider discoveredNodeRegistry;

    final ClientServiceHandle<EJBClientChannel> serviceHandle;

    RemoteEJBReceiver(final RemoteTransportProvider remoteTransportProvider, final EJBReceiverContext receiverContext, final RemotingEJBDiscoveryProvider discoveredNodeRegistry) {
        this.remoteTransportProvider = remoteTransportProvider;
        this.receiverContext = receiverContext;
        this.discoveredNodeRegistry = discoveredNodeRegistry;
        serviceHandle = new ClientServiceHandle<>("jboss.ejb", channel -> EJBClientChannel.construct(channel, this.discoveredNodeRegistry));
    }

    final IoFuture.HandlingNotifier<ConnectionPeerIdentity, EJBReceiverInvocationContext> notifier = new IoFuture.HandlingNotifier<ConnectionPeerIdentity, EJBReceiverInvocationContext>() {
        public void handleDone(final ConnectionPeerIdentity peerIdentity, final EJBReceiverInvocationContext attachment) {
            final EJBClientChannel ejbClientChannel;
            try {
                ejbClientChannel = getClientChannel(peerIdentity.getConnection());
            } catch (IOException e) {
                // should generally not be possible but we should handle it cleanly regardless
                attachment.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new RequestSendFailedException(e, true)));
                return;
            }
            attachment.getClientInvocationContext().putAttachment(EJBCC_KEY, ejbClientChannel);
            ejbClientChannel.processInvocation(attachment, peerIdentity);
        }

        public void handleCancelled(final EJBReceiverInvocationContext attachment) {
            attachment.requestCancelled();
        }

        public void handleFailed(final IOException exception, final EJBReceiverInvocationContext attachment) {
            attachment.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new RequestSendFailedException(exception, true)));
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
            return serviceHandle.getClientService(connection, OptionMap.EMPTY).getInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
    }

    protected void processInvocation(final EJBReceiverInvocationContext receiverContext) throws Exception {
        final EJBClientInvocationContext clientInvocationContext = receiverContext.getClientInvocationContext();
        final EJBLocator<?> locator = clientInvocationContext.getLocator();
        final AuthenticationConfiguration authenticationConfiguration = receiverContext.getAuthenticationConfiguration();
        final SSLContext sslContext = receiverContext.getSSLContext();
        final IoFuture<ConnectionPeerIdentity> futureConnection = getConnection(receiverContext.getClientInvocationContext().getDestination(), authenticationConfiguration, sslContext);
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

    protected StatefulEJBLocator<?> createSession(final EJBReceiverSessionCreationContext context) throws Exception {
        final StatelessEJBLocator<?> statelessLocator = context.getClientInvocationContext().getLocator().asStateless();
        final AuthenticationConfiguration authenticationConfiguration = context.getAuthenticationConfiguration();
        final SSLContext sslContext = context.getSSLContext();
        try {
            IoFuture<ConnectionPeerIdentity> futureConnection = getConnection(context.getClientInvocationContext().getDestination(), authenticationConfiguration, sslContext);
            final ConnectionPeerIdentity identity = futureConnection.getInterruptibly();
            final EJBClientChannel ejbClientChannel = getClientChannel(identity.getConnection());
            return ejbClientChannel.openSession(statelessLocator, identity);
        } catch (IOException e) {
            final CreateException createException = new CreateException("Failed to create stateful EJB: " + e.getMessage());
            createException.initCause(e);
            throw createException;
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

    private <T> IoFuture<ConnectionPeerIdentity> getConnection(final URI target, AuthenticationConfiguration authenticationConfiguration, SSLContext sslContext) throws Exception {
        if (authenticationConfiguration == null) {
            authenticationConfiguration = CLIENT.getAuthenticationConfiguration(target, AuthenticationContext.captureCurrent(), -1, "ejb", "jboss");
        }
        if (sslContext == null) {
            sslContext = CLIENT.getSSLContext(target, AuthenticationContext.captureCurrent(), "ejb", "jboss");
        }
        final SSLContext finalSslContext = sslContext;
        final AuthenticationConfiguration finalAuthenticationConfiguration = authenticationConfiguration;
        return doPrivileged((PrivilegedAction<IoFuture<ConnectionPeerIdentity>>) () -> Endpoint.getCurrent().getConnectedIdentity(target, finalSslContext, finalAuthenticationConfiguration));
    }
}
