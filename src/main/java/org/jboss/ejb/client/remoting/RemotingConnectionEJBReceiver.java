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
import org.jboss.ejb.client.protocol.PackedInteger;
import org.jboss.ejb.client.protocol.ProtocolHandler;
import org.jboss.ejb.client.protocol.ProtocolHandlerFactory;
import org.jboss.logging.Logger;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.SimpleDataInput;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.MessageInputStream;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemotingConnectionEJBReceiver extends EJBReceiver<RemotingAttachments> {

    private static final Logger logger = Logger.getLogger(RemotingConnectionEJBReceiver.class);

    private final Connection connection;

    private final ProtocolHandler protocolHandler;

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
        this.protocolHandler = ProtocolHandlerFactory.getProtocolHandler(clientProtocolVersion, clientMarshallingStrategy);
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
                channel.receiveMessage(new VersionReceiver(context));
            }
        }, context);
    }

    public Future<?> processInvocation(final EJBClientInvocationContext<RemotingAttachments> context) throws Exception {
        return null;
    }

    public byte[] openSession(final String appName, final String moduleName, final String distinctName, final String beanName) throws Exception {
        return new byte[0];
    }

    public void verify(final String appName, final String moduleName, final String distinctName, final String beanName) throws Exception {
    }

    public RemotingAttachments createReceiverSpecific() {
        return new RemotingAttachments();
    }

    ProtocolHandler getProtocolHandler() {
        return this.protocolHandler;
    }

    private class VersionReceiver implements Channel.Receiver {

        private final EJBReceiverContext receiverContext;

        VersionReceiver(EJBReceiverContext receiverContext) {
            this.receiverContext = receiverContext;
        }

        public void handleError(final Channel channel, final IOException error) {
        }

        public void handleEnd(final Channel channel) {
        }

        public void handleMessage(final Channel channel, final MessageInputStream message) {
            // TODO: handle incoming greeting, send our own, set up the connection state,
            // and query the module list
            final SimpleDataInput simpleDataInput = new SimpleDataInput(Marshalling.createByteInput(message));
            byte serverVersion;
            String[] serverMarshallerStrategies;
            try {
                serverVersion = simpleDataInput.readByte();
                final int serverMarshallerCount = PackedInteger.readPackedInteger(simpleDataInput);
                if (serverMarshallerCount <= 0) {
                    // TODO: Handle this
                }
                serverMarshallerStrategies = new String[serverMarshallerCount];
                logger.info("Received server version " + serverVersion + " and marshalling strategies " + serverMarshallerStrategies);

                for (int i = 0; i < serverMarshallerCount; i++) {
                    serverMarshallerStrategies[i] = simpleDataInput.readUTF();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (!this.checkCompatibility(serverVersion, serverMarshallerStrategies)) {
                logger.error("EJB receiver cannot communicate with server, due to version incompatibility");
                // close the context
                this.receiverContext.close();
                return;
            }

            try {
                // send a version message to the server
                this.sendVersionMessage(channel);
                // version message sent, now wait for a module inventory report from the server
                channel.receiveMessage(new ResponseReceiver(RemotingConnectionEJBReceiver.this));
                
            } catch (IOException ioe) {
                // TODO: re-evaluate
                throw new RuntimeException(ioe);
            }


        }

        private boolean checkCompatibility(final byte serverVersion, final String[] serverMarshallingStrategies) {
            if (serverVersion < RemotingConnectionEJBReceiver.this.clientProtocolVersion) {
                return false;
            }
            final Collection<String> supportedStrategies = Arrays.asList(serverMarshallingStrategies);
            if (!supportedStrategies.contains(RemotingConnectionEJBReceiver.this.clientMarshallingStrategy)) {
                return false;
            }
            return true;
        }

        private void sendVersionMessage(final Channel channel) throws IOException {
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            try {
                RemotingConnectionEJBReceiver.this.protocolHandler.writeVersionMessage(dataOutputStream, RemotingConnectionEJBReceiver.this.clientMarshallingStrategy);
            } finally {
                dataOutputStream.close();
            }
        }

    }

}
