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

import static java.lang.Math.min;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import javax.transaction.Transaction;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKeys;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBMethodLocator;
import org.jboss.ejb.client.RequestSendFailedException;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.TransactionID;
import org.jboss.invocation.AsynchronousInterceptor;
import org.jboss.marshalling.AbstractClassResolver;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.UTFUtils;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3.util.MessageTracker;
import org.jboss.remoting3.util.StreamUtils;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
public final class RemoteServer {

    private final Channel channel;
    private final int version;
    private final MessageTracker messageTracker;
    private final MarshallerFactory marshallerFactory;
    private final MarshallingConfiguration configuration;
    private final IntIndexHashMap<InProgress> invocations = new IntIndexHashMap<>(InProgress::getInvId);

    RemoteServer(final Channel channel, final int version, final MessageTracker messageTracker) {
        this.channel = channel;
        this.version = version;
        this.messageTracker = messageTracker;
        final MarshallingConfiguration configuration = new MarshallingConfiguration();
        if (version < 3) {
            configuration.setClassTable(ProtocolV1ClassTable.INSTANCE);
            configuration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
            configuration.setVersion(2);
        } else {
            configuration.setClassTable(ProtocolV3ClassTable.INSTANCE);
            configuration.setObjectTable(ProtocolV3ObjectTable.INSTANCE);
            configuration.setVersion(4);
        }
        marshallerFactory = new RiverMarshallerFactory();
        this.configuration = configuration;
    }

    public static OpenListener createOpenListener(Function<RemoteServer, Association> associationFactory) {
        return new OpenListener() {
            public void channelOpened(final Channel channel) {
                final MessageTracker messageTracker = new MessageTracker(channel, channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue());
                channel.receiveMessage(new Channel.Receiver() {
                    public void handleError(final Channel channel, final IOException error) {
                    }

                    public void handleEnd(final Channel channel) {
                    }

                    public void handleMessage(final Channel channel, final MessageInputStream message) {
                        final int version;
                        try {
                            version = min(3, StreamUtils.readInt8(message));
                            // drain the rest of the message because it's just garbage really
                            while (message.read() != -1) {
                                message.skip(Long.MAX_VALUE);
                            }
                        } catch (IOException e) {
                            safeClose(channel);
                            return;
                        }
                        final RemoteServer remoteServer = new RemoteServer(channel, version, messageTracker);
                        final Association association = associationFactory.apply(remoteServer);
                        channel.receiveMessage(remoteServer.getReceiver(association));
                    }
                });
                try (MessageOutputStream mos = messageTracker.openMessage()) {
                    mos.writeByte(Protocol.LATEST_VERSION);
                    mos.write(Protocol.RIVER_BYTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    safeClose(channel);
                } catch (IOException e) {
                    safeClose(channel);
                }
            }

            public void registrationTerminated() {
            }
        };
    }

    Channel.Receiver getReceiver(final Association association) {
        return new ReceiverImpl(association);
    }

    public void writeClusterTopologyUpdate(Object topology) throws IOException {

    }

    public void writeClusterRemoval(List<String> nodeNames) throws IOException {

    }


    public interface Association {
        /**
         * Map the class loader for the given EJB application and module.
         *
         * @param appName the application name
         * @param moduleName the module name
         * @return the class loader
         */
        ClassLoader mapClassLoader(String appName, String moduleName);

        AsynchronousInterceptor.CancellationHandle receiveInvocationRequest(IncomingInvocation incomingInvocation);

        void receiveSessionOpenRequest(IncomingSessionOpen incomingSessionOpen);

        void closed();

        Transaction lookupLegacyTransaction(TransactionID transactionId);
    }

    class ReceiverImpl implements Channel.Receiver {
        private final Association association;

        ReceiverImpl(final Association association) {
            this.association = association;
        }

        public void handleError(final Channel channel, final IOException error) {
            association.closed();
        }

        public void handleEnd(final Channel channel) {
            association.closed();
        }

        public void handleMessage(final Channel channel, final MessageInputStream message) {
            try {
                final int code = message.readUnsignedByte();
                switch (code) {
                    case Protocol.COMPRESSED_INVOCATION_MESSAGE:
                    case Protocol.INVOCATION_REQUEST: {
                        final int invId = message.readUnsignedShort();
                        // now if we get an error, we can respond.
                        try (InputStream input = code == Protocol.COMPRESSED_INVOCATION_MESSAGE ? new InflaterInputStream(message) : message) {
                            handleInvocationRequest(invId, input);
                        } catch (IOException | ClassNotFoundException e) {
                            // write response back to client
                            writeFailedResponse(invId, e);
                        }
                        break;
                    }
                    case Protocol.OPEN_SESSION_REQUEST: {
                        final int invId = message.readUnsignedShort();
                        try {
                            handleSessionOpenRequest(invId, message);
                        } catch (IOException e) {
                            // write response back to client
                            writeFailedResponse(invId, e);
                        }
                        break;
                    }
                    case Protocol.CANCEL_REQUEST: {
                        final int invId = message.readUnsignedShort();
                        try {
                            handleCancelRequest(invId, message);
                        } catch (IOException e) {
                            // ignored
                        }
                        break;
                    }
                    // TODO: transaction messages should proxy to transaction server handler
                    default: {
                        // unrecognized
                        Logs.REMOTING.invalidMessageReceived(code);
                        break;
                    }
                }
            } catch (IOException e) {
                // nothing we can do.
            } finally {
                safeClose(message);
                channel.receiveMessage(this);
            }
        }

        void handleCancelRequest(final int invId, final MessageInputStream message) throws IOException {
            final boolean cancelIfRunning = version >= 3 && message.readBoolean();
            final InProgress inProgress = invocations.get(invId);
            if (inProgress != null) {
                inProgress.getCancellationHandle().cancel(cancelIfRunning);
            }
        }

        void handleSessionOpenRequest(final int invId, final MessageInputStream inputStream) throws IOException {
            final String appName = inputStream.readUTF();
            final String moduleName = inputStream.readUTF();
            final String distName = inputStream.readUTF();
            final String beanName = inputStream.readUTF();
            final int securityContext;
            final int transactionContext;
            if (version >= 3) {
                securityContext = inputStream.readInt();
                transactionContext = inputStream.readUnsignedShort();
            } else {
                securityContext = 0;
                transactionContext = 0;
            }
            final Connection connection = channel.getConnection();
            // TODO Elytron read transaction by transaction ID from local transaction provider
            final Transaction transaction = null;

            association.receiveSessionOpenRequest(new IncomingSessionOpen(
                invId,
                Affinity.NONE,
                connection.getLocalIdentity(securityContext),
                transaction,
                new EJBIdentifier(appName, moduleName, beanName, distName)
            ));
        }

        void handleInvocationRequest(final int invId, final InputStream input) throws IOException, ClassNotFoundException {
            final MarshallingConfiguration configuration = RemoteServer.this.configuration.clone();
            final ServerClassResolver classResolver = new ServerClassResolver();
            configuration.setClassResolver(classResolver);
            Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(configuration);
            unmarshaller.start(Marshalling.createByteInput(input));
            EJBMethodLocator methodLocator;
            EJBLocator<?> locator;
            Map<String, Object> attachments;
            Affinity weakAffinity = Affinity.NONE;
            int responseCompressLevel = -1;
            SecurityIdentity identity;
            Transaction transaction = null;
            final Connection connection = channel.getConnection();
            if (version >= 3) {
                // read MethodLocator
                String appName = unmarshaller.readObject(String.class);
                String moduleName = unmarshaller.readObject(String.class);
                classResolver.setClassLoader(association.mapClassLoader(appName, moduleName));
                methodLocator = unmarshaller.readObject(EJBMethodLocator.class);
                weakAffinity = unmarshaller.readObject(Affinity.class);
                if (weakAffinity == null) weakAffinity = Affinity.NONE;
                int flags = unmarshaller.readUnsignedByte();
                responseCompressLevel = flags & Protocol.COMPRESS_RESPONSE;
                int identityId = unmarshaller.readInt();
                int transactionId = unmarshaller.readUnsignedShort();
                identity = identityId == 0 ? connection.getLocalIdentity() : connection.getLocalIdentity(identityId);
                transaction = null; // TODO Elytron - look up transaction by transactionId
                locator = unmarshaller.readObject(EJBLocator.class);
                // do identity checks for these strings to guarantee integrity.
                // noinspection StringEquality
                if (appName != locator.getAppName() || moduleName != locator.getModuleName()) {
                    throw Logs.REMOTING.mismatchedMethodLocation();
                }
            } else {
                assert version <= 2;
                String sigString = UTFUtils.readUTFZBytes(unmarshaller);
                String appName = unmarshaller.readObject(String.class);
                String moduleName = unmarshaller.readObject(String.class);
                classResolver.setClassLoader(association.mapClassLoader(appName, moduleName));
                String distinctName = unmarshaller.readObject(String.class);
                String beanName = unmarshaller.readObject(String.class);

                // always use connection identity
                identity = connection.getLocalIdentity();

                locator = unmarshaller.readObject(EJBLocator.class);
                // do identity checks for these strings to guarantee integrity.
                //noinspection StringEquality
                if (appName != locator.getAppName() || moduleName != locator.getModuleName() || beanName != locator.getBeanName() || distinctName != locator.getDistinctName()) {
                    throw Logs.REMOTING.mismatchedMethodLocation();
                }

                // parse out the signature string
                String methodName = "";
                // first count the segments
                int count = 0;
                for (int i = 0; i != -1; i = sigString.indexOf(',', i)) {
                    count ++;
                }
                String[] parameterTypeNames = new String[count];
                // now extract them
                int j = 0, n;
                for (int i = 0; i != -1; i = n + 1) {
                    n = sigString.indexOf(',', i);
                    if (n == -1) {
                        parameterTypeNames[j ++] = sigString.substring(i, n);
                    } else {
                        parameterTypeNames[j ++] = sigString.substring(i);
                    }
                }
                methodLocator = EJBMethodLocator.create(methodName, parameterTypeNames);
            }
            Object[] parameters = new Object[methodLocator.getParameterCount()];
            for (int i = 0; i < parameters.length; i ++) {
                parameters[i] = unmarshaller.readObject();
            }
            int attachmentCount = PackedInteger.readPackedInteger(unmarshaller);
            attachments = new HashMap<>(attachmentCount);
            for (int i = 0; i < attachmentCount; i ++) {
                String attName = unmarshaller.readObject(String.class);
                if (attName.equals(EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY)) {
                    if (version <= 2) {
                        // only supported for protocol v1/2 - read out transaction ID
                        Map<?, ?> map = unmarshaller.readObject(Map.class);
                        final Object transactionIdObject = map.get(AttachmentKeys.TRANSACTION_ID_KEY);
                        if (transactionIdObject != null) {
                            // attach it
                            TransactionID transactionId = (TransactionID) transactionIdObject;
                            // look up the transaction
                            transaction = association.lookupLegacyTransaction(transactionId);
                        }
                        weakAffinity = (Affinity) map.get(AttachmentKeys.WEAK_AFFINITY);
                        if (weakAffinity == null) {
                            weakAffinity = Affinity.NONE;
                        }


                    } else {
                        // discard content for v3
                        unmarshaller.readObject();
                    }
                } else {
                    attachments.put(attName, unmarshaller.readObject());
                }
            }
            unmarshaller.finish();
            final IncomingInvocation incomingInvocation = new IncomingInvocation(invId, weakAffinity, identity, transaction, locator, attachments, methodLocator, parameters, responseCompressLevel);
            final AsynchronousInterceptor.CancellationHandle handle = association.receiveInvocationRequest(incomingInvocation);
            invocations.put(new InProgress(incomingInvocation, handle));
        }
    }

    private void writeFailedResponse(final int invId, final Exception e) {
        try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
            os.writeByte(Protocol.APPLICATION_EXCEPTION);
            os.writeShort(invId);
            final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
            marshaller.start(Marshalling.createByteOutput(os));
            marshaller.writeObject(new RequestSendFailedException(e));
            marshaller.writeByte(0);
            marshaller.finish();
        } catch (IOException e2) {
            // nothing to do at this point; the client doesn't want the response
            Logs.REMOTING.trace("EJB response write failed", e2);
        }
    }

    abstract class Incoming {
        private final int invId;
        private final Affinity weakAffinity;
        private final SecurityIdentity identity;
        private final Transaction transaction;

        Incoming(final int invId, final Affinity weakAffinity, final SecurityIdentity identity, final Transaction transaction) {
            this.invId = invId;
            this.weakAffinity = weakAffinity;
            this.identity = identity;
            this.transaction = transaction;
        }

        int getInvId() {
            return invId;
        }

        public Affinity getWeakAffinity() {
            return weakAffinity;
        }

        public SecurityIdentity getIdentity() {
            return identity;
        }

        public Transaction getTransaction() {
            return transaction;
        }
    }

    public final class IncomingSessionOpen extends Incoming {

        private final EJBIdentifier identifier;

        public IncomingSessionOpen(final int invId, final Affinity weakAffinity, final SecurityIdentity identity, final Transaction transaction, final EJBIdentifier identifier) {
            super(invId, weakAffinity, identity, transaction);
            this.identifier = identifier;
        }

        public void writeResponse(SessionID sessionID) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.OPEN_SESSION_RESPONSE);
                os.writeShort(getInvId());
                final byte[] encodedForm = sessionID.getEncodedForm();
                PackedInteger.writePackedInteger(os, encodedForm.length);
                os.write(encodedForm);
                if (1 <= version && version <= 2) {
                    final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                    marshaller.start(Marshalling.createByteOutput(os));
                    marshaller.writeObject(getWeakAffinity());
                    marshaller.finish();
                }
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB session open response write failed", e);
            }
        }

        public EJBIdentifier getIdentifier() {
            return identifier;
        }
    }

    public final class IncomingInvocation extends Incoming {
        private final EJBLocator<?> ejbLocator;
        private final Map<String, Object> attachments;
        private final EJBMethodLocator methodLocator;
        private final Object[] parameters;
        private final int responseCompressLevel;

        IncomingInvocation(final int invId, final Affinity weakAffinity, final SecurityIdentity identity, final Transaction transaction, final EJBLocator<?> ejbLocator, final Map<String, Object> attachments, final EJBMethodLocator methodLocator, final Object[] parameters, final int responseCompressLevel) {
            super(invId, weakAffinity, identity, transaction);
            this.ejbLocator = ejbLocator;
            this.attachments = attachments;
            this.methodLocator = methodLocator;
            this.parameters = parameters;
            this.responseCompressLevel = responseCompressLevel;
        }

        public EJBLocator<?> getEjbLocator() {return ejbLocator;}

        public Map<String, Object> getAttachments() {
            return attachments;
        }

        public EJBMethodLocator getMethodLocator() {
            return methodLocator;
        }

        public Object[] getParameters() {
            return parameters;
        }

        public int getResponseCompressLevel() {
            return responseCompressLevel;
        }

        public void writeCancellation() {
            if (version >= 3) try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.CANCEL_RESPONSE);
                os.writeShort(getInvId());
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(getInvId());
            } else {
                writeThrowable(Logs.REMOTING.requestCancelled());
            }
        }

        public void writeResponse(final Object response) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(responseCompressLevel > 0 ? Protocol.COMPRESSED_INVOCATION_MESSAGE : Protocol.INVOCATION_RESPONSE);
                os.writeShort(getInvId());
                try (OutputStream output = responseCompressLevel > 0 ? new DeflaterOutputStream(os, new Deflater(responseCompressLevel, false)) : os) {
                    final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                    marshaller.start(Marshalling.createByteOutput(output));
                    marshaller.writeObject(response);
                    int count = attachments.size();
                    if (count > 255) {
                        marshaller.writeByte(255);
                    } else {
                        marshaller.writeByte(count);
                    }
                    int i = 0;
                    for (Map.Entry<String, Object> entry : attachments.entrySet()) {
                        marshaller.writeObject(entry.getKey());
                        marshaller.writeObject(entry.getValue());
                        if (i ++ == 255) {
                            break;
                        }
                    }
                    marshaller.finish();
                }
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(getInvId());
            }
        }

        public void writeThrowable(final Throwable response) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.APPLICATION_EXCEPTION);
                os.writeShort(getInvId());
                final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                marshaller.start(Marshalling.createByteOutput(os));
                marshaller.writeObject(response);
                marshaller.writeByte(0);
                marshaller.finish();
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(getInvId());
            }
        }
    }

    static final class InProgress {
        private final IncomingInvocation incomingInvocation;
        private final AsynchronousInterceptor.CancellationHandle cancellationHandle;

        InProgress(final IncomingInvocation incomingInvocation, final AsynchronousInterceptor.CancellationHandle cancellationHandle) {
            this.incomingInvocation = incomingInvocation;
            this.cancellationHandle = cancellationHandle;
        }

        IncomingInvocation getIncomingInvocation() {
            return incomingInvocation;
        }

        AsynchronousInterceptor.CancellationHandle getCancellationHandle() {
            return cancellationHandle;
        }

        int getInvId() {
            return incomingInvocation.getInvId();
        }
    }

    static final class ServerClassResolver extends AbstractClassResolver {
        private ClassLoader classLoader;

        ServerClassResolver() {
            super(true);
        }

        protected ClassLoader getClassLoader() {
            final ClassLoader classLoader = this.classLoader;
            return classLoader == null ? getClass().getClassLoader() : classLoader;
        }

        void setClassLoader(final ClassLoader classLoader) {
            this.classLoader = classLoader == null ? getClass().getClassLoader() : classLoader;
        }
    }
}
