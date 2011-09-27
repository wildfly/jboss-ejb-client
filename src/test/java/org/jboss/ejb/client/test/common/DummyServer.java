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
package org.jboss.ejb.client.test.common;

import org.jboss.ejb.client.ModuleID;
import org.jboss.ejb.client.protocol.PackedInteger;
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

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Security;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class DummyServer {

    private static final Logger logger = Logger.getLogger(DummyServer.class);

    private static final String[] supportedMarshallerTypes = new String[]{"river", "java-serial"};

    static {
        Security.addProvider(new JBossSaslProvider());
    }

    private Endpoint endpoint;

    private final int port;
    private final String host;


    private AcceptingChannel<? extends ConnectedStreamChannel> server;
    private Map<ModuleID, Map<String, Object>> registeredEJBs = new ConcurrentHashMap<ModuleID, Map<String, Object>>();

    private final Map<Channel, DummyVersionZeroServerProtocolHandler> openChannels = new ConcurrentHashMap<Channel, DummyVersionZeroServerProtocolHandler>();

    public DummyServer(final String host, final int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        logger.info("Starting " + this);
        final ExecutorService serverExecutor = Executors.newFixedThreadPool(4);
        final OptionMap options = OptionMap.EMPTY;
        endpoint = Remoting.createEndpoint("endpoint", serverExecutor, options);
        final Xnio xnio = Xnio.getInstance();
        final Registration registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(xnio), OptionMap.create(Options.SSL_ENABLED, false));
        final NetworkServerProvider serverProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SocketAddress bindAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        final SimpleServerAuthenticationProvider authenticationProvider = new SimpleServerAuthenticationProvider();
        authenticationProvider.addUser("test", "localhost.localdomain", "test".toCharArray());
        final OptionMap serverOptions = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("ANONYMOUS"), Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        this.server = serverProvider.createServer(bindAddress, serverOptions, authenticationProvider);

        endpoint.registerService("jboss.ejb", new OpenListener() {
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

    public void stop() throws IOException {
        this.server.close();
        this.server = null;
        IoUtils.safeClose(this.endpoint);
    }



    class Version0Receiver implements Channel.Receiver {

        private final DummyVersionZeroServerProtocolHandler protocolHandler;

        Version0Receiver(final String marshallingType) {
            this.protocolHandler = new DummyVersionZeroServerProtocolHandler(marshallingType);
        }

        @Override
        public void handleError(Channel channel, IOException error) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void handleEnd(Channel channel) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void handleMessage(Channel channel, MessageInputStream message) {
            logger.info("TODO: message handling not yet implemented");
        }

    }

    public void register(final String appName, final String moduleName, final String distinctName, final String beanName, final Object instance) {

        final StringBuilder sb = new StringBuilder();
        if (appName != null) {
            sb.append(appName).append("/");
        }
        sb.append(moduleName).append("/");
        if (distinctName != null) {
            sb.append(distinctName).append("/");
        }
        sb.append(beanName);
        final ModuleID moduleID = new ModuleID(appName, moduleName, distinctName);
        Map<String, Object> ejbs = this.registeredEJBs.get(moduleID);
        if (ejbs == null) {
            ejbs = new HashMap<String, Object>();
            this.registeredEJBs.put(moduleID, ejbs);
        }
        ejbs.put(beanName, instance);
        try {
            this.sendNewModuleAvailabilityToClients(new ModuleID[]{moduleID});
        } catch (IOException e) {
            logger.warn("Could not send EJB module availability message to clients, for module " + moduleID, e);
        }
    }

    private void sendNewModuleAvailabilityToClients(final ModuleID[] newModules) throws IOException {
        if (newModules == null) {
            return;
        }
        if (this.openChannels.isEmpty()) {
            logger.debug("No open channels to send EJB module availability");
        }
        for (Map.Entry<Channel, DummyVersionZeroServerProtocolHandler> entry : this.openChannels.entrySet()) {
            final Channel channel = entry.getKey();
            final DummyVersionZeroServerProtocolHandler protocolHandler = entry.getValue();
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            try {
                protocolHandler.writeModuleAvailability(dataOutputStream, newModules);
            } catch (IOException e) {
                logger.warn("Could not send module availability message to client", e);
            } finally {
                dataOutputStream.close();
            }

        }
    }

    class VersionReceiver implements Channel.Receiver {
        @Override
        public void handleError(Channel channel, IOException error) {
            try {
                channel.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException("NYI: .handleError");
        }

        @Override
        public void handleEnd(Channel channel) {
            try {
                channel.close();
            } catch (IOException e) {
                // ignore
            }
        }


        @Override
        public void handleMessage(Channel channel, MessageInputStream message) {
            final SimpleDataInput input = new SimpleDataInput(Marshalling.createByteInput(message));
            try {
                final byte version = input.readByte();
                final String clientMarshallingType = input.readUTF();
                input.close();
                switch (version) {
                    case 0x00:
                        final Version0Receiver receiver = new Version0Receiver(clientMarshallingType);
                        DummyServer.this.openChannels.put(channel, receiver.protocolHandler);
                        channel.receiveMessage(receiver);
                        // send module availability report to clients
                        final Collection<ModuleID> availableModules = DummyServer.this.registeredEJBs.keySet();
                        DummyServer.this.sendNewModuleAvailabilityToClients(availableModules.toArray(new ModuleID[availableModules.size()]));
                        break;
                    default:
                        logger.info("Received unsupported version 0x" + Integer.toHexString(version) + " from client, on channel " + channel);
                        channel.close();
                        break;
                }
            } catch (IOException e) {
                logger.error("Exception on channel " + channel, e);
                try {
                    logger.info("Shutting down channel " + channel);
                    channel.writeShutdown();
                } catch (IOException e1) {
                    // ignore
                    if (logger.isTraceEnabled()) {
                        logger.trace("Ignoring exception that occurred during channel shutdown", e1);
                    }
                }
            }
        }
    }

}
