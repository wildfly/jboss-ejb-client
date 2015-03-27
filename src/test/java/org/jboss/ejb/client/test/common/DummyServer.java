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

import org.jboss.ejb.client.AttachmentKeys;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.SessionID;
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
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

import javax.ejb.NoSuchEJBException;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Future;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class DummyServer {

    private static final Logger logger = Logger.getLogger(DummyServer.class);
    public static final String REQUEST_WAS_COMPRESSED = "request-was-compressed";
    public static final String RESPONSE_WAS_COMPRESSED = "Response was compressed";

    private static final String[] supportedMarshallerTypes = new String[]{"river", "java-serial"};
    private static final String CLUSTER_NAME = "dummy-cluster";
    private static final ThreadLocal<Boolean> requestCompressed = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> responseCompressed = new ThreadLocal<Boolean>();

    private Endpoint endpoint;

    private final int port;
    private final String host;
    private final String endpointName;

    private AcceptingChannel<? extends ConnectedStreamChannel> server;
    private Map<EJBModuleIdentifier, Map<String, Object>> registeredEJBs = new ConcurrentHashMap<EJBModuleIdentifier, Map<String, Object>>();

    private final Collection<Channel> openChannels = new CopyOnWriteArraySet<Channel>();

    public DummyServer(final String host, final int port) {
        this(host, port, "default-dummy-server-endpoint");
    }

    public DummyServer(final String host, final int port, final String endpointName) {
        this.host = host;
        this.port = port;
        this.endpointName = endpointName;
    }

    public void start() throws IOException {
        logger.info("Starting " + this);
        final OptionMap options = OptionMap.EMPTY;
        endpoint = Remoting.createEndpoint(this.endpointName, options);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        final NetworkServerProvider serverProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SocketAddress bindAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        final SimpleServerAuthenticationProvider authenticationProvider = new SimpleServerAuthenticationProvider();
        authenticationProvider.addUser("test", "localhost.localdomain", "test".toCharArray());
        final OptionMap serverOptions = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("ANONYMOUS"), Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        this.server = serverProvider.createServer(bindAddress, serverOptions, authenticationProvider, null);

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
                logger.info("Registration terminated for open listener");
            }

            private void sendVersionMessage(final Channel channel) throws IOException {
                final DataOutputStream outputStream = new DataOutputStream(channel.writeMessage());
                // write the version
                outputStream.write(0x02);
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

    public String getClusterName() {
        return this.CLUSTER_NAME;
    }


    class LatestVersionProtocolHandler implements Channel.Receiver {

        private final DummyProtocolHandler dummyProtocolHandler;

        LatestVersionProtocolHandler(final String marshallingType) {
            this.dummyProtocolHandler = new DummyProtocolHandler(marshallingType);
        }

        @Override
        public void handleError(Channel channel, IOException error) {
        }

        @Override
        public void handleEnd(Channel channel) {
        }

        @Override
        public void handleMessage(Channel channel, MessageInputStream messageInputStream) {
            try {
                processMessage(channel, messageInputStream);
            } finally {
                // receive next message
                channel.receiveMessage(this);
                IoUtils.safeClose(messageInputStream);
            }
        }

        private void processMessage(final Channel channel, final InputStream inputStream) {
            try {
                final int header = inputStream.read();
                logger.info("Received message with header 0x" + Integer.toHexString(header));
                switch (header) {
                    case 0x03:
                        final MethodInvocationRequest methodInvocationRequest = this.dummyProtocolHandler.readMethodInvocationRequest(inputStream, this.getClass().getClassLoader());
                        Object methodInvocationResult = null;
                        try {
                            methodInvocationResult = DummyServer.this.handleMethodInvocationRequest(channel, methodInvocationRequest, dummyProtocolHandler);
                        } catch (NoSuchEJBException nsee) {
                            final DataOutputStream outputStream = new DataOutputStream(channel.writeMessage());
                            try {
                                this.dummyProtocolHandler.writeNoSuchEJBFailureMessage(outputStream, methodInvocationRequest.getInvocationId(), methodInvocationRequest.getAppName(),
                                        methodInvocationRequest.getModuleName(), methodInvocationRequest.getDistinctName(), methodInvocationRequest.getBeanName(),
                                        methodInvocationRequest.getViewClassName());
                            } finally {
                                outputStream.close();
                            }
                            return;
                        } catch (Exception e) {
                            final DataOutputStream outputStream = new DataOutputStream(channel.writeMessage());
                            try {
                                this.dummyProtocolHandler.writeException(outputStream, methodInvocationRequest.getInvocationId(), e, methodInvocationRequest.getAttachments());
                            } finally {
                                outputStream.close();
                            }
                            return;
                        }
                        logger.info("Method invocation result on server " + endpointName + ": " + methodInvocationResult);
                        // write the method invocation result
                        final DataOutputStream dataOutputStream = wrapMessgeOutputStream(channel.writeMessage(), methodInvocationRequest);
                        try {
                            Object modifiedResult = methodInvocationResult;
                            if (requestCompressed.get() != null && requestCompressed.get().booleanValue()) {
                                // prefix a string to the result that the request was compressed. This is just to facilitate testing of compressed requests.
                                // Just consider string responses
                                if (methodInvocationResult instanceof String) {
                                    modifiedResult = REQUEST_WAS_COMPRESSED + " " + modifiedResult;
                                }
                            }
                            // prefix a string to the result that the response is compressed (if it is)
                            if (responseCompressed.get() != null && responseCompressed.get().booleanValue()) {
                                // prefix a string to the result that the response was compressed. This is just to facilitate testing of compressed responses.
                                // Just consider string responses
                                if (methodInvocationResult instanceof String) {
                                    modifiedResult = RESPONSE_WAS_COMPRESSED + " " + modifiedResult;
                                }
                            }
                            this.dummyProtocolHandler.writeMethodInvocationResponse(dataOutputStream, methodInvocationRequest.getInvocationId(), modifiedResult, methodInvocationRequest.getAttachments());
                        } finally {
                            dataOutputStream.close();
                        }

                        break;
                    case 0x01:
                        // session open request
                        try {
                            this.handleSessionOpenRequest(channel, inputStream);
                        } catch (Exception e) {
                            // TODO: Let the client know of this exception
                            throw new RuntimeException(e);
                        }

                        break;
                    case 0x1B:
                        // compressed data
                        // create an inflater input stream
                        logger.info("Received compressed data");
                        // setup a thread local so that subsequent part of the invocation will know that the request was compressed. This is just to facilitate the tests and the outcome
                        requestCompressed.set(true);
                        final InputStream inflaterInputStream = new InflaterInputStream(inputStream);
                        this.processMessage(channel, inflaterInputStream);
                        break;
                    default:
                        logger.warn("Not supported message header 0x" + Integer.toHexString(header) + " received by " + this);
                        return;
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                // clear thread locals
                requestCompressed.remove();
                responseCompressed.remove();
            }


        }

        private DataOutputStream wrapMessgeOutputStream(final MessageOutputStream messageOutputStream, final MethodInvocationRequest methodInvocationRequest) throws IOException {
            final Map attachments = methodInvocationRequest.getAttachments();
            final DataOutput dataOutput;
            if (attachments == null || attachments.isEmpty()) {
                return new DataOutputStream(messageOutputStream);
            }
            final Map privateAttachments = (Map) attachments.get(EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY);
            if (privateAttachments == null || privateAttachments.isEmpty()) {
                return new DataOutputStream(messageOutputStream);
            }
            final Boolean compressResponse = (Boolean) privateAttachments.get(AttachmentKeys.COMPRESS_RESPONSE);
            if (compressResponse == null) {
                return new DataOutputStream(messageOutputStream);
            }
            if (!compressResponse) {
                return new DataOutputStream(messageOutputStream);
            }
            // write out a header to indicate that the response will be compressed
            messageOutputStream.write(0x1B);
            // mark a thread local indicating that the response was compressed
            responseCompressed.set(true);
            // now create a deflater
            final Integer compressionLevel = (Integer) attachments.get(AttachmentKeys.RESPONSE_COMPRESSION_LEVEL);
            final Deflater deflater = new Deflater(compressionLevel == null ? Deflater.DEFAULT_COMPRESSION : compressionLevel);
            final DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(messageOutputStream, deflater);
            return new DataOutputStream(deflaterOutputStream);
        }

        private void handleSessionOpenRequest(Channel channel, InputStream inputStream) throws IOException {
            if (inputStream == null) {
                throw new IllegalArgumentException("Cannot read from null inputstream");
            }
            final DataInputStream dataInputStream = new DataInputStream(inputStream);

            // read invocation id
            final short invocationId = dataInputStream.readShort();
            final String appName = dataInputStream.readUTF();
            final String moduleName = dataInputStream.readUTF();
            final String distinctName = dataInputStream.readUTF();
            final String beanName = dataInputStream.readUTF();

            final EJBModuleIdentifier ejbModuleIdentifier = new EJBModuleIdentifier(appName, moduleName, distinctName);
            final Map<String, Object> ejbs = DummyServer.this.registeredEJBs.get(ejbModuleIdentifier);
            if (ejbs == null || ejbs.get(beanName) == null) {
                final DataOutputStream outputStream = new DataOutputStream(channel.writeMessage());
                try {
                    this.dummyProtocolHandler.writeNoSuchEJBFailureMessage(outputStream, invocationId, appName, moduleName, distinctName, beanName, null);
                } finally {
                    outputStream.close();
                }
                return;
            }
            final UUID uuid = UUID.randomUUID();
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            final SessionID sessionID = SessionID.createSessionID(bb.array());
            final DataOutputStream outputStream = new DataOutputStream(channel.writeMessage());
            try {
                final ClusterAffinity hardAffinity = new ClusterAffinity(DummyServer.this.CLUSTER_NAME);
                this.dummyProtocolHandler.writeSessionId(outputStream, invocationId, sessionID, hardAffinity);
            } finally {
                outputStream.close();
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
            this.sendNewModuleReportToClients(new EJBModuleIdentifier[]{moduleID}, true);
        } catch (IOException e) {
            logger.warn("Could not send EJB module availability message to clients, for module " + moduleID, e);
        }
    }

    public void unregister(final String appName, final String moduleName, final String distinctName, final String beanName) {
        this.unregister(appName, moduleName, distinctName, beanName, true);
    }

    public void unregister(final String appName, final String moduleName, final String distinctName, final String beanName, final boolean notifyClients) {

        final EJBModuleIdentifier moduleID = new EJBModuleIdentifier(appName, moduleName, distinctName);
        Map<String, Object> ejbs = this.registeredEJBs.get(moduleID);
        if (ejbs != null) {
            ejbs.remove(beanName);
        }
        if (notifyClients) {
            try {
                this.sendNewModuleReportToClients(new EJBModuleIdentifier[]{moduleID}, false);
            } catch (IOException e) {
                logger.warn("Could not send EJB module un-availability message to clients, for module " + moduleID, e);
            }
        }
    }


    private void sendNewModuleReportToClients(final EJBModuleIdentifier[] modules, final boolean availabilityReport) throws IOException {
        if (modules == null) {
            return;
        }
        if (this.openChannels.isEmpty()) {
            logger.debug("No open channels to send EJB module availability");
        }
        for (final Channel channel : this.openChannels) {
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            try {
                if (availabilityReport) {
                    this.writeModuleAvailability(dataOutputStream, modules);
                } else {
                    this.writeModuleUnAvailability(dataOutputStream, modules);
                }
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
        this.writeModuleReport(output, ejbModuleIdentifiers);
    }

    private void writeModuleUnAvailability(final DataOutput output, final EJBModuleIdentifier[] ejbModuleIdentifiers) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Cannot write to null output");
        }
        if (ejbModuleIdentifiers == null) {
            throw new IllegalArgumentException("EJB module identifiers cannot be null");
        }
        // write the header
        output.write(0x09);
        this.writeModuleReport(output, ejbModuleIdentifiers);
    }

    private void writeModuleReport(final DataOutput output, final EJBModuleIdentifier[] modules) throws IOException {
        // write the count
        PackedInteger.writePackedInteger(output, modules.length);
        // write the module identifiers
        for (int i = 0; i < modules.length; i++) {
            // write the app name
            final String appName = modules[i].getAppName();
            if (appName == null) {
                // write out a empty string
                output.writeUTF("");
            } else {
                output.writeUTF(appName);
            }
            // write the module name
            output.writeUTF(modules[i].getModuleName());
            // write the distinct name
            final String distinctName = modules[i].getDistinctName();
            if (distinctName == null) {
                // write out an empty string
                output.writeUTF("");
            } else {
                output.writeUTF(distinctName);
            }
        }
    }

    private Object handleMethodInvocationRequest(final Channel channel, final MethodInvocationRequest methodInvocationRequest, final DummyProtocolHandler dummyProtocolHandler) throws InvocationTargetException, IllegalAccessException, IOException {
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
        // check if this is an async method
        if (this.isAsyncMethod(method)) {
            final DataOutputStream output = new DataOutputStream(channel.writeMessage());
            try {
                // send a notification to the client that this is an async method
                dummyProtocolHandler.writeAsyncMethodNotification(output, methodInvocationRequest.getInvocationId());
            } finally {
                output.close();
            }
        }
        // invoke on the method
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

    private boolean isAsyncMethod(final Method method) {
        // just check for return type and assume it to be async if it returns Future
        return method.getReturnType().equals(Future.class);
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
                    case 0x02:
                        final LatestVersionProtocolHandler receiver = new LatestVersionProtocolHandler(clientMarshallingType);
                        DummyServer.this.openChannels.add(channel);
                        channel.receiveMessage(receiver);
                        // send module availability report to clients
                        final Collection<EJBModuleIdentifier> availableModules = DummyServer.this.registeredEJBs.keySet();
                        DummyServer.this.sendNewModuleReportToClients(availableModules.toArray(new EJBModuleIdentifier[availableModules.size()]), true);
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
