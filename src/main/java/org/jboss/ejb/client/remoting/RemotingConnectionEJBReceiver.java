/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client.remoting;

import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.NoSessionID;
import org.jboss.ejb.client.SessionID;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemotingConnectionEJBReceiver extends EJBReceiver<RemotingAttachments> {

    private static final Logger logger = Logger.getLogger(RemotingConnectionEJBReceiver.class);

    private final Connection connection;

    private final Map<EJBReceiverContext, Channel> perReceiverContextChannels = new IdentityHashMap<EJBReceiverContext, Channel>();

    // TODO: The version and the marshalling strategy shouldn't be hardcoded here
    private final byte clientProtocolVersion = 0x00;
    private final String clientMarshallingStrategy = "river";

    /**
     * Construct a new instance.
     *
     * @param connection the connection to associate with
     */
    public RemotingConnectionEJBReceiver(final Connection connection) {
        this.connection = connection;
    }

    @Override
    public void associate(final EJBReceiverContext context) {
        final IoFuture<Channel> futureChannel = connection.openChannel("jboss.ejb", OptionMap.EMPTY);
        futureChannel.addNotifier(new IoFuture.HandlingNotifier<Channel, EJBReceiverContext>() {
            public void handleCancelled(final EJBReceiverContext context) {
                context.close();
            }

            public void handleFailed(final IOException exception, final EJBReceiverContext context) {
                // todo: log?
                context.close();
            }

            public void handleDone(final Channel channel, final EJBReceiverContext context) {
                channel.addCloseHandler(new CloseHandler<Channel>() {
                    public void handleClose(final Channel closed, final IOException exception) {
                        context.close();
                    }
                });
                // receive version message from server
                channel.receiveMessage(new VersionReceiver(RemotingConnectionEJBReceiver.this, context,
                        RemotingConnectionEJBReceiver.this.clientProtocolVersion, RemotingConnectionEJBReceiver.this.clientMarshallingStrategy));
            }
        }, context);
    }

    @Override
    public Future<?> processInvocation(final EJBClientInvocationContext<RemotingAttachments> clientInvocationContext, final EJBReceiverContext ejbReceiverContext) throws Exception {
        // TODO: Implement this - Check receiver status and then send out a method invocation request
        // via the channel to the server
        FutureResult futureResult = new FutureResult();
        futureResult.setResult(null);
        return IoFutureHelper.future(futureResult.getIoFuture());
    }

    @Override
    public SessionID openSession(final EJBReceiverContext receiverContext, final String appName, final String moduleName, final String distinctName, final String beanName) throws Exception {
        // todo
        return NoSessionID.INSTANCE;
    }

    public void verify(final String appName, final String moduleName, final String distinctName, final String beanName) throws Exception {
    }

    public RemotingAttachments createReceiverSpecific() {
        return new RemotingAttachments();
    }

    void onSuccessfulVersionHandshake(final EJBReceiverContext receiverContext, final Channel channel) {
        // TODO: Handle the case where the receiver context might already be associated with a
        // channel previously.
        synchronized (this.perReceiverContextChannels) {
            this.perReceiverContextChannels.put(receiverContext, channel);
        }
        // register a receiver for messages from the server on this channel
        channel.receiveMessage(new ResponseReceiver(this, receiverContext));
    }

    ProtocolMessageHandler getProtocolMessageHandler(final EJBReceiverContext ejbReceiverContext, final byte header) {
        return null;
    }


}
