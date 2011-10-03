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

import org.jboss.ejb.client.remoting.DummyProtocolHandler;
import org.jboss.ejb.client.remoting.MethodInvocationRequest;
import org.jboss.ejb.client.remoting.PackedInteger;
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

import javax.ejb.NoSuchEJBException;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Security;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
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
    private Map<EJBModuleIdentifier, Map<String, Object>> registeredEJBs = new ConcurrentHashMap<EJBModuleIdentifier, Map<String, Object>>();

    private final Collection<Channel> openChannels = new CopyOnWriteArraySet<Channel>();

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
                outputStream.write(0x01);
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


    class Version1Receiver implements Channel.Receiver {

        private final DummyProtocolHandler dummyProtocolHandler;

        Version1Receiver(final String marshallingType) {
            this.dummyProtocolHandler = new DummyProtocolHandler(marshallingType);
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
        public void handleMessage(Channel channel, MessageInputStream messageInputStream) {
            final DataInputStream inputStream = new DataInputStream(messageInputStream);
            try {
                final byte header = inputStream.readByte();
                logger.info("Received message with header 0x" + Integer.toHexString(header));
                switch (header) {
                    case 0x03:
                        final MethodInvocationRequest methodInvocationRequest = this.dummyProtocolHandler.readMethodInvocationRequest(inputStream, this.getClass().getClassLoader());
                        Object methodInvocationResult = null;
                        try {
                            methodInvocationResult = DummyServer.this.handleMethodInvocationRequest(methodInvocationRequest);
                        } catch (Exception e) {
                            logger.error("Error while invoking method", e);
                            // TODO: Convey this error back to client
                            throw new RuntimeException(e);
                        }
                        logger.info("Method invocation result on server " + methodInvocationResult);
                        // write the method invocation result
                        final DataOutputStream outputStream = new DataOutputStream(channel.writeMessage());
                        try {
                            this.dummyProtocolHandler.writeMethodInvocationResponse(outputStream, methodInvocationRequest.getInvocationId(), methodInvocationResult, methodInvocationRequest.getAttachments());
                        } finally {
                            outputStream.close();
                        }

                        break;
                    default:
                        logger.warn("Not supported message header 0x" + Integer.toHexString(header) + " received by " + this);
                        return;
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                // receive next message
                channel.receiveMessage(this);
                IoUtils.safeClose(inputStream);
            }

        }

    }

    public void register(final String appName, final String moduleName, final String distinctName, final String beanName, final Object instance) {

        final EJBModuleIdentifier moduleID = new EJBModuleIdentifier(appName, moduleName, distinctName);
        Map<String, Object> ejbs = this.registeredEJBs.get(moduleID);
        if (ejbs == null) {
            ejbs = new HashMap<String, Object>();
            this.registeredEJBs.put(moduleID, ejbs);
        }
        ejbs.put(beanName, instance);
        try {
            this.sendNewModuleAvailabilityToClients(new EJBModuleIdentifier[]{moduleID});
        } catch (IOException e) {
            logger.warn("Could not send EJB module availability message to clients, for module " + moduleID, e);
        }
    }

    private void sendNewModuleAvailabilityToClients(final EJBModuleIdentifier[] newModules) throws IOException {
        if (newModules == null) {
            return;
        }
        if (this.openChannels.isEmpty()) {
            logger.debug("No open channels to send EJB module availability");
        }
        for (final Channel channel : this.openChannels) {
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            try {
                this.writeModuleAvailability(dataOutputStream, newModules);
            } catch (IOException e) {
                logger.warn("Could not send module availability message to client", e);
            } finally {
                dataOutputStream.close();
            }

        }
    }

    private void writeModuleAvailability(final DataOutput output, final EJBModuleIdentifier[] ejbModuleIdentifiers) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Cannot write to null output");
        }
        if (ejbModuleIdentifiers == null) {
            throw new IllegalArgumentException("EJB module identifiers cannot be null");
        }
        // write the header
        output.write(0x08);
        // write the count
        PackedInteger.writePackedInteger(output, ejbModuleIdentifiers.length);
        // write the app/module names
        for (int i = 0; i < ejbModuleIdentifiers.length; i++) {
            // write the app name
            final String appName = ejbModuleIdentifiers[i].getAppName();
            if (appName == null) {
                // write out a empty string
                output.writeUTF("");
            } else {
                output.writeUTF(appName);
            }
            // write the module name
            output.writeUTF(ejbModuleIdentifiers[i].getModuleName());
            // write the distinct name
            final String distinctName = ejbModuleIdentifiers[i].getDistinctName();
            if (distinctName == null) {
                // write out an empty string
                output.writeUTF("");
            } else {
                output.writeUTF(distinctName);
            }
        }
    }

    private Object handleMethodInvocationRequest(final MethodInvocationRequest methodInvocationRequest) throws InvocationTargetException, IllegalAccessException {
        final EJBModuleIdentifier ejbModuleIdentifier = new EJBModuleIdentifier(methodInvocationRequest.getAppName(), methodInvocationRequest.getModuleName(), methodInvocationRequest.getDistinctName());
        final Map<String, Object> ejbs = this.registeredEJBs.get(ejbModuleIdentifier);
        final Object beanInstance = ejbs.get(methodInvocationRequest.getBeanName());
        if (beanInstance == null) {
            throw new NoSuchEJBException(methodInvocationRequest.getBeanName() + " EJB not available");
        }
        Method method = null;
        try {
            method = this.getRequiredMethod(beanInstance.getClass(), methodInvocationRequest.getMethodName(), methodInvocationRequest.getParamTypes());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return method.invoke(beanInstance, methodInvocationRequest.getParams());
    }

    private Method getRequiredMethod(final Class<?> klass, final String methodName, final String[] paramTypes) throws NoSuchMethodException {
        final Class<?>[] types = new Class<?>[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            try {
                types[i] = Class.forName(paramTypes[i], false, klass.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return klass.getMethod(methodName, types);
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
                    case 0x01:
                        final Version1Receiver receiver = new Version1Receiver(clientMarshallingType);
                        DummyServer.this.openChannels.add(channel);
                        channel.receiveMessage(receiver);
                        // send module availability report to clients
                        final Collection<EJBModuleIdentifier> availableModules = DummyServer.this.registeredEJBs.keySet();
                        DummyServer.this.sendNewModuleAvailabilityToClients(availableModules.toArray(new EJBModuleIdentifier[availableModules.size()]));
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

    private class EJBModuleIdentifier {
        private final String appName;

        private final String moduleName;

        private final String distinctName;

        EJBModuleIdentifier(final String appname, final String moduleName, final String distinctName) {
            this.appName = appname;
            this.moduleName = moduleName;
            this.distinctName = distinctName;
        }

        String getAppName() {
            return this.appName;
        }

        String getModuleName() {
            return this.moduleName;
        }

        String getDistinctName() {
            return this.distinctName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EJBModuleIdentifier that = (EJBModuleIdentifier) o;

            if (appName != null ? !appName.equals(that.appName) : that.appName != null) return false;
            if (distinctName != null ? !distinctName.equals(that.distinctName) : that.distinctName != null)
                return false;
            if (!moduleName.equals(that.moduleName)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = appName != null ? appName.hashCode() : 0;
            result = 31 * result + moduleName.hashCode();
            result = 31 * result + (distinctName != null ? distinctName.hashCode() : 0);
            return result;
        }
    }
}
