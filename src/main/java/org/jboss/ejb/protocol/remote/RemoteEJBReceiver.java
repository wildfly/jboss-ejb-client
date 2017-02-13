/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.ejb.protocol.remote;

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.security.PrivilegedAction;

import javax.ejb.CreateException;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKey;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.RequestSendFailedException;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.remoting3.ClientServiceHandle;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.wildfly.common.Assert;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.remote.RemoteNamingProvider;
import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class RemoteEJBReceiver extends EJBReceiver {
    static final AttachmentKey<EJBClientChannel> EJBCC_KEY = new AttachmentKey<>();

    private final RemoteTransportProvider remoteTransportProvider;
    private final EJBReceiverContext receiverContext;

    final ClientServiceHandle<EJBClientChannel> serviceHandle = new ClientServiceHandle<>("jboss.ejb", channel -> EJBClientChannel.construct(channel, this));

    RemoteEJBReceiver(final RemoteTransportProvider remoteTransportProvider, final EJBReceiverContext receiverContext) {
        this.remoteTransportProvider = remoteTransportProvider;
        this.receiverContext = receiverContext;
    }

    final IoFuture.HandlingNotifier<Connection, EJBReceiverInvocationContext> notifier = new IoFuture.HandlingNotifier<Connection, EJBReceiverInvocationContext>() {
        public void handleDone(final Connection connection, final EJBReceiverInvocationContext attachment) {
            final EJBClientChannel ejbClientChannel;
            try {
                ejbClientChannel = getClientChannel(connection);
            } catch (IOException e) {
                // should generally not be possible but we should handle it cleanly regardless
                attachment.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new RequestSendFailedException(e)));
                return;
            }
            attachment.getClientInvocationContext().putAttachment(EJBCC_KEY, ejbClientChannel);
            ejbClientChannel.processInvocation(attachment);
        }

        public void handleCancelled(final EJBReceiverInvocationContext attachment) {
            attachment.requestCancelled();
        }

        public void handleFailed(final IOException exception, final EJBReceiverInvocationContext attachment) {
            attachment.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new RequestSendFailedException(exception)));
        }
    };

    RemoteTransportProvider getRemoteTransportProvider() {
        return remoteTransportProvider;
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
        final NamingProvider namingProvider = receiverContext.getNamingProvider();
        final IoFuture<Connection> futureConnection = getConnection(locator, namingProvider);
        // this actually causes the invocation to move forward
        futureConnection.addNotifier(notifier, receiverContext);
    }

    protected boolean cancelInvocation(final EJBReceiverInvocationContext receiverContext, final boolean cancelIfRunning) {
        final NamingProvider namingProvider = receiverContext.getNamingProvider();
        final EJBClientInvocationContext clientInvocationContext = receiverContext.getClientInvocationContext();
        final EJBLocator<?> locator = clientInvocationContext.getLocator();
        try {
            final EJBClientChannel channel = receiverContext.getClientInvocationContext().getAttachment(EJBCC_KEY);
            return channel != null && channel.cancelInvocation(receiverContext, cancelIfRunning);
        } catch (Exception e) {
            return false;
        }
    }

    protected <T> StatefulEJBLocator<T> createSession(final StatelessEJBLocator<T> statelessLocator) throws Exception {
        try {
            IoFuture<Connection> futureConnection = getConnection(statelessLocator, null);
            final EJBClientChannel ejbClientChannel = getClientChannel(futureConnection.getInterruptibly());
            return ejbClientChannel.openSession(statelessLocator);
        } catch (IOException e) {
            final CreateException createException = new CreateException("Failed to create stateful EJB: " + e.getMessage());
            createException.initCause(e);
            throw createException;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CreateException("Stateful EJB creation interrupted");
        }
    }

    protected boolean isConnected(final URI uri) {
        final IoFuture<Connection> future = Endpoint.getCurrent().getConnectionIfExists(uri, "ejb", "jboss");
        try {
            return future != null && future.getStatus() == IoFuture.Status.DONE && future.get().isOpen();
        } catch (IOException e) {
            // impossible
            throw Assert.unreachableCode();
        }
    }

    private <T> IoFuture<Connection> getConnection(final EJBLocator<T> locator, final NamingProvider namingProvider) throws Exception {
        final Connection namingConnection = namingProvider instanceof RemoteNamingProvider ? ((RemoteNamingProvider) namingProvider).getPeerIdentity().getConnection() : null;
        final Affinity affinity = locator.getAffinity();
        final URI target;
        if (affinity instanceof URIAffinity) {
            target = affinity.getUri();
            if (namingConnection != null && target.equals(namingConnection.getPeerURI())) {
                return new FinishedIoFuture<>(namingConnection);
            }
        } else {
            throw new IllegalArgumentException("Invalid EJB affinity");
        }
        return doPrivileged((PrivilegedAction<IoFuture<Connection>>) () -> Endpoint.getCurrent().getConnection(target, "ejb", "jboss"));
    }
}
