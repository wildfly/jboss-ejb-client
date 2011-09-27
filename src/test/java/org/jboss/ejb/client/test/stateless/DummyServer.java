/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client.test.stateless;

import org.jboss.ejb.client.protocol.MessageType;
import org.jboss.ejb.client.protocol.PackedInteger;
import org.jboss.ejb.client.protocol.ProtocolHandler;
import org.jboss.ejb.client.protocol.VersionZeroProtocolHandler;
import org.jboss.logging.Logger;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.SimpleDataInput;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.jboss.sasl.JBossSaslProvider;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
class DummyServer {

    private static final Logger logger = Logger.getLogger(DummyServer.class);

    private static final String[] supportedMarshallerTypes = new String[] {"java-serial"};

    static {
        Security.addProvider(new JBossSaslProvider());
    }

    private Map<String, Object> remoteInstances = new HashMap<String, Object>();

    class VersionReceiver extends AbstractReceiver {
        @Override
        public void handleMessage(Channel channel, MessageInputStream message) {
            final SimpleDataInput input = new SimpleDataInput(Marshalling.createByteInput(message));
            try {
                final byte version = input.readByte();
                final String clientMarshallingType = input.readUTF();
                input.close();
                switch (version) {
                    case 0x00:
                        channel.receiveMessage(new Version0Receiver(clientMarshallingType));
                        break;
                    default:
                        channel.close();
                        break;
                }
            } catch (IOException e) {
                // log it
                e.printStackTrace();
                try {
                    channel.writeShutdown();
                } catch (IOException e1) {
                    // ignore
                }
            }
        }
    }

    class Version0Receiver extends AbstractReceiver {

        private String marshallingType;

        private ProtocolHandler protocolHandler;

        Version0Receiver(final String marshallingType) {
            this.marshallingType = marshallingType;
            this.protocolHandler = new VersionZeroProtocolHandler(this.marshallingType);
        }

        @Override
        public void handleMessage(Channel channel, MessageInputStream message) {
            try {
                final DataInputStream inputStream = new DataInputStream(message);
                // read the first byte to see what type of a message it is
                final MessageType messageType = this.protocolHandler.getMessageType(inputStream);
                if (messageType != MessageType.INVOCATION_REQUEST) {
                    throw new RuntimeException("Unsupported message type 0x" + Integer.toHexString(messageType.getHeader()));
                }/*
                //final MethodInvocationRequest invocationRequest = this.protocolHandler.readMethodInvocationRequest(inputStream, new DummyEJBViewResolver());
                // in this dummy server we process the request within the remoting thread, this is not
                // how it is supposed to work in the real server
                final String fqBeanName = invocationRequest.getAppName() + "/" + invocationRequest.getModuleName() + "/"
                        + invocationRequest.getBeanName();
                final Object bean = remoteInstances.get(fqBeanName);
                if (bean == null)
                    throw new RuntimeException("Unknown bean registration " + fqBeanName);
                final Class<?>[] methodParamTypes = this.loadParamTypes(invocationRequest.getParamTypes(), DummyServer.class.getClassLoader());
                final DataOutputStream outputStream = new DataOutputStream(channel.writeMessage());
                try {
                    final Method method = bean.getClass().getMethod(invocationRequest.getMethodName(), methodParamTypes);
                    final Object result = method.invoke(bean, invocationRequest.getParams());
                    this.protocolHandler.writeMethodInvocationResponse(outputStream, invocationRequest.getInvocationId(), result, null, null);
                } catch (Throwable t) {
                    this.protocolHandler.writeMethodInvocationResponse(outputStream, invocationRequest.getInvocationId(), null, t, null);
                } finally {
                    IoUtils.safeClose(outputStream);
                }
*/
            } catch (IOException e) {
                // log it
                logger.errorf(e, "Exception on channel %s from message %s", channel, message);
                try {
                    // press the panic button
                    channel.writeShutdown();
                } catch (IOException e1) {
                    // ignore
                }
            }
        }

        private Class<?>[] loadParamTypes(final String[] paramTypes, final ClassLoader cl) {
            if (paramTypes == null) {
                return new Class<?>[0];
            }
            final Class<?>[] types = new Class<?>[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                try {
                    types[i] = Class.forName(paramTypes[i], false, cl);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            return types;
        }
    }

    DummyServer() {
    }

    void register(final String fqBeanName, final Object instance) {
        remoteInstances.put(fqBeanName, instance);
    }

    void start() throws IOException {
        logger.info("Starting " + this);
        final ExecutorService serverExecutor = Executors.newFixedThreadPool(4);
        final OptionMap options = OptionMap.EMPTY;
        final Endpoint endpoint = Remoting.createEndpoint("endpoint", serverExecutor, options);
        final Xnio xnio = Xnio.getInstance();
        final Registration registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(xnio), OptionMap.create(Options.SSL_ENABLED, false));
        final NetworkServerProvider serverProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SocketAddress bindAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 6999);
        final SimpleServerAuthenticationProvider authenticationProvider = new SimpleServerAuthenticationProvider();
        authenticationProvider.addUser("test", "localhost.localdomain", "test".toCharArray());
        final OptionMap serverOptions = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("ANONYMOUS"), Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        final AcceptingChannel<? extends ConnectedStreamChannel> server = serverProvider.createServer(bindAddress, serverOptions, authenticationProvider);

        endpoint.registerService("ejb3", new OpenListener() {
            @Override
            public void channelOpened(Channel channel) {
                logger.info("Channel opened " + channel);
                channel.addCloseHandler(new CloseHandler<Channel>() {
                    @Override
                    public void handleClose(Channel closed, IOException exception) {
                        logger.info("Bye " + closed);
                    }
                });
                try {
                    this.sendVersionMessage(channel);
                } catch (IOException e) {
                    logger.error("Could not send version message to channel " + channel + " Closing the channel");
                    IoUtils.safeClose(channel);
                }
                Channel.Receiver handler = new VersionReceiver();
                channel.receiveMessage(handler);
            }

            @Override
            public void registrationTerminated() {
                throw new RuntimeException("NYI: .registrationTerminated");
            }

            private void sendVersionMessage(final Channel channel) throws IOException {
                final DataOutputStream outputStream = new DataOutputStream(channel.writeMessage());
                // write the version
                outputStream.write(0x00);
                // write the marshaller type count
                PackedInteger.writePackedInteger(outputStream, supportedMarshallerTypes.length);
                // write the marshaller types
                for (int i = 0; i < supportedMarshallerTypes.length; i++) {
                    outputStream.writeUTF(supportedMarshallerTypes[i]);
                }
                outputStream.flush();
                outputStream.close();
            }

        }, OptionMap.EMPTY);
    }
}
