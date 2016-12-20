/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKeys;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBMethodLocator;
import org.jboss.ejb.client.RequestSendFailedException;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.TransactionID;
import org.jboss.ejb.client.UserTransactionID;
import org.jboss.ejb.client.XidTransactionID;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.CancelHandle;
import org.jboss.ejb.server.InvocationRequest;
import org.jboss.ejb.server.Request;
import org.jboss.ejb.server.SessionOpenRequest;
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
import org.wildfly.common.Assert;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.transaction.client.ImportResult;
import org.wildfly.transaction.client.LocalTransaction;
import org.wildfly.transaction.client.LocalTransactionContext;
import org.wildfly.transaction.client.SimpleXid;
import org.wildfly.transaction.client.provider.remoting.RemotingTransactionServer;
import org.wildfly.transaction.client.spi.SubordinateTransactionControl;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
@SuppressWarnings("deprecation")
public final class RemoteServer {

    private final RemotingTransactionServer transactionServer;
    private final Channel channel;
    private final int version;
    private final MessageTracker messageTracker;
    private final MarshallerFactory marshallerFactory;
    private final MarshallingConfiguration configuration;
    private final IntIndexHashMap<InProgress> invocations = new IntIndexHashMap<>(InProgress::getInvId);

    RemoteServer(final RemotingTransactionServer transactionServer, final Channel channel, final int version, final MessageTracker messageTracker) {
        this.transactionServer = transactionServer;
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

    public static OpenListener createOpenListener(Function<RemoteServer, Association> associationFactory, RemotingTransactionServer transactionServer) {
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
                        final RemoteServer remoteServer = new RemoteServer(transactionServer, channel, version, messageTracker);
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


    class ReceiverImpl implements Channel.Receiver {
        private final Association association;

        ReceiverImpl(final Association association) {
            this.association = association;
        }

        public void handleError(final Channel channel, final IOException error) {
        }

        public void handleEnd(final Channel channel) {
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
                    case Protocol.TXN_COMMIT_REQUEST:
                    case Protocol.TXN_ROLLBACK_REQUEST:
                    case Protocol.TXN_PREPARE_REQUEST:
                    case Protocol.TXN_FORGET_REQUEST:
                    case Protocol.TXN_BEFORE_COMPLETION_REQUEST: {
                        final int invId = message.readUnsignedShort();
                        try {
                            handleTxnRequest(code, invId, message);
                        } catch (IOException e) {
                            // ignored
                        }
                        break;
                    }
                    case Protocol.TXN_RECOVERY_REQUEST: {
                        final int invId = message.readUnsignedShort();
                        try {
                            handleTxnRecoverRequest(invId, message);
                        } catch (IOException e) {
                            // ignored
                        }
                        break;
                    }
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

        private void writeTxnResponse(final int invId, final int flag) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.TXN_RESPONSE);
                os.writeShort(invId);
                os.writeBoolean(true);
                PackedInteger.writePackedInteger(os, flag);
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB transaction response write failed", e);
            }
        }

        private void writeTxnResponse(final int invId) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.TXN_RESPONSE);
                os.writeShort(invId);
                os.writeBoolean(false);
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB transaction response write failed", e);
            }
        }

        private void handleTxnRequest(final int code, final int invId, final MessageInputStream message) throws IOException {
            final byte[] bytes = new byte[message.readUnsignedShort()];
            message.readFully(bytes);
            final TransactionID transactionID = TransactionID.createTransactionID(bytes);
            if (transactionID instanceof XidTransactionID) try {
                final SubordinateTransactionControl control = transactionServer.getTransactionService().getTransactionContext().findOrImportTransaction(((XidTransactionID) transactionID).getXid(), 0).getControl();
                switch (code) {
                    case Protocol.TXN_COMMIT_REQUEST: {
                        boolean opc = message.readBoolean();
                        control.commit(opc);
                        writeTxnResponse(invId);
                        break;
                    }
                    case Protocol.TXN_ROLLBACK_REQUEST: {
                        control.rollback();
                        writeTxnResponse(invId);
                        break;
                    }
                    case Protocol.TXN_PREPARE_REQUEST: {
                        int res = control.prepare();
                        writeTxnResponse(invId, res);
                        break;
                    }
                    case Protocol.TXN_FORGET_REQUEST: {
                        control.forget();
                        writeTxnResponse(invId);
                        break;
                    }
                    case Protocol.TXN_BEFORE_COMPLETION_REQUEST: {
                        control.beforeCompletion();
                        writeTxnResponse(invId);
                        break;
                    }
                    default: throw Assert.impossibleSwitchCase(code);
                }
            } catch (XAException e) {
                writeFailedResponse(invId, e);
            } else if (transactionID instanceof UserTransactionID) try {
                final LocalTransaction localTransaction = transactionServer.getTransactionIfExists(((UserTransactionID) transactionID).getId());
                switch (code) {
                    case Protocol.TXN_COMMIT_REQUEST: {
                        if (localTransaction != null) localTransaction.commit();
                        writeTxnResponse(invId);
                        break;
                    }
                    case Protocol.TXN_ROLLBACK_REQUEST: {
                        if (localTransaction != null) localTransaction.rollback();
                        writeTxnResponse(invId);
                        break;
                    }
                    case Protocol.TXN_PREPARE_REQUEST:
                    case Protocol.TXN_FORGET_REQUEST:
                    case Protocol.TXN_BEFORE_COMPLETION_REQUEST: {
                        writeFailedResponse(invId, Logs.TXN.userTxNotSupportedByTxContext());
                        break;
                    }
                    default: throw Assert.impossibleSwitchCase(code);
                }
            } catch (SystemException | HeuristicMixedException | RollbackException | HeuristicRollbackException e) {
                writeFailedResponse(invId, e);
            } else {
                throw Assert.unreachableCode();
            }
        }

        void handleTxnRecoverRequest(final int invId, final MessageInputStream message) throws IOException {
            final String parentName = message.readUTF();
            final int flags = message.readInt();
            final Xid[] xids;
            try {
                xids = transactionServer.getTransactionService().getTransactionContext().getRecoveryInterface().recover(flags);
            } catch (XAException e) {
                writeFailedResponse(invId, e);
                return;
            }
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.TXN_RECOVERY_RESPONSE);
                os.writeShort(invId);
                PackedInteger.writePackedInteger(os, xids.length);
                final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                marshaller.start(Marshalling.createByteOutput(os));
                for (Xid xid : xids) {
                    marshaller.writeObject(new XidTransactionID(xid));
                }
                marshaller.finish();
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB transaction response write failed", e);
            }
        }

        void handleCancelRequest(final int invId, final MessageInputStream message) throws IOException {
            final boolean cancelIfRunning = version >= 3 && message.readBoolean();
            final InProgress inProgress = invocations.get(invId);
            if (inProgress != null) {
                inProgress.getCancelHandle().cancel(cancelIfRunning);
            }
        }

        private ExceptionSupplier<ImportResult<?>, SystemException> readTransaction(final DataInput input) throws IOException {
            final int type = input.readUnsignedByte();
            if (type == 0) {
                return null;
            } else if (type == 1) {
                // remote user transaction
                final int id = input.readInt();
                final int timeout = PackedInteger.readPackedInteger(input);
                return () -> new ImportResult<Transaction>(transactionServer.getOrBeginTransaction(id, timeout), SubordinateTransactionControl.EMPTY, false);
            } else if (type == 2) {
                final int fmt = PackedInteger.readPackedInteger(input);
                final byte[] gtid = new byte[input.readUnsignedByte()];
                input.readFully(gtid);
                final byte[] bq = new byte[input.readUnsignedByte()];
                input.readFully(bq);
                final int timeout = PackedInteger.readPackedInteger(input);
                return () -> {
                    try {
                        return transactionServer.getTransactionService().getTransactionContext().findOrImportTransaction(new SimpleXid(fmt, gtid, bq), timeout);
                    } catch (XAException e) {
                        throw new SystemException(e.getMessage());
                    }
                };
            } else {
                throw Logs.REMOTING.invalidTransactionType(type);
            }
        }

        void handleSessionOpenRequest(final int invId, final MessageInputStream inputStream) throws IOException {
            final String appName = inputStream.readUTF();
            final String moduleName = inputStream.readUTF();
            final String distName = inputStream.readUTF();
            final String beanName = inputStream.readUTF();
            final int securityContext;
            final int transactionContext;
            final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier;
            if (version >= 3) {
                securityContext = inputStream.readInt();
                transactionSupplier = readTransaction(inputStream);
            } else {
                securityContext = 0;
                transactionSupplier = null;
            }
            final Connection connection = channel.getConnection();
            final EJBIdentifier identifier = new EJBIdentifier(appName, moduleName, beanName, distName);

            association.receiveSessionOpenRequest(new RemotingSessionOpenRequest<Object>(
                invId,
                identifier,
                Affinity.NONE,
                connection.getLocalIdentity(securityContext),
                transactionSupplier
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
            ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier = null;

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
                transactionSupplier = readTransaction(unmarshaller);
                identity = identityId == 0 ? connection.getLocalIdentity() : connection.getLocalIdentity(identityId);
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
                            final TransactionID transactionId = (TransactionID) transactionIdObject;
                            // look up the transaction
                            if (transactionId instanceof UserTransactionID) {
                                transactionSupplier = () -> new ImportResult<Transaction>(transactionServer.getOrBeginTransaction(((UserTransactionID) transactionId).getId(), 0), SubordinateTransactionControl.EMPTY, false);
                            } else if (transactionId instanceof XidTransactionID) {
                                transactionSupplier = () -> {
                                    try {
                                        return transactionServer.getTransactionService().getTransactionContext().findOrImportTransaction(((XidTransactionID) transactionId).getXid(), 0);
                                    } catch (XAException e) {
                                        throw new SystemException(e.getMessage());
                                    }
                                };
                            } else {
                                throw Assert.impossibleSwitchCase(transactionId);
                            }
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
            final RemotingInvocationRequest<?> incomingInvocation = constructRequest(
                invId,
                locator,
                weakAffinity,
                identity,
                transactionSupplier,
                attachments,
                methodLocator,
                parameters,
                responseCompressLevel
            );
            final CancelHandle handle = association.receiveInvocationRequest(incomingInvocation);
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

    abstract class RemotingRequest implements Request {
        final int invId;
        final Affinity weakAffinity;
        final SecurityIdentity identity;
        final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier;
        int txnCmd = 0; // assume nobody will ask about the transaction
        SessionID sessionId;

        RemotingRequest(final int invId, final Affinity weakAffinity, final SecurityIdentity identity, final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier) {
            this.invId = invId;
            this.weakAffinity = weakAffinity;
            this.identity = identity;
            this.transactionSupplier = transactionSupplier;
        }

        public SocketAddress getPeerAddress() {
            return channel.getConnection().getPeerAddress();
        }

        public SocketAddress getLocalAddress() {
            return channel.getConnection().getLocalAddress();
        }

        @NotNull
        public Affinity getWeakAffinity() {
            return weakAffinity;
        }

        @NotNull
        public SecurityIdentity getIdentity() {
            return identity;
        }

        public boolean hasTransaction() {
            return transactionSupplier != null;
        }

        public Transaction getTransaction() throws SystemException, IllegalStateException {
            final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier = this.transactionSupplier;
            if (transactionSupplier == null) {
                return null;
            }
            if (txnCmd != 0) {
                throw new IllegalStateException();
            }
            final ImportResult<?> importResult = transactionSupplier.get();
            if (importResult.isNew()) {
                txnCmd = 1;
            } else {
                txnCmd = 2;
            }
            return importResult.getTransaction();
        }

        public void convertToStateful(@NotNull final SessionID sessionId) throws IllegalArgumentException, IllegalStateException {
            Assert.checkNotNullParam("sessionId", sessionId);
            final SessionID ourSessionId = this.sessionId;
            if (ourSessionId != null) {
                if (! sessionId.equals(ourSessionId)) {
                    throw new IllegalStateException();
                }
            } else {
                this.sessionId = sessionId;
            }
        }

        protected void writeFailure(Exception reason) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.APPLICATION_EXCEPTION);
                os.writeShort(invId);
                final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                marshaller.start(Marshalling.createByteOutput(os));
                marshaller.writeObject(reason);
                marshaller.writeByte(0);
                marshaller.finish();
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(invId);
            }
        }
    }

    final class RemotingSessionOpenRequest<T> extends RemotingRequest implements SessionOpenRequest {
        private final EJBIdentifier identifier;

        RemotingSessionOpenRequest(final int invId, final EJBIdentifier identifier, final Affinity weakAffinity, final SecurityIdentity identity, final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier) {
            super(invId, weakAffinity, identity, transactionSupplier);
            this.identifier = identifier;
        }

        @NotNull
        public EJBIdentifier getEJBIdentifier() {
            return identifier;
        }

        public void execute(@NotNull final ExceptionRunnable<Exception> resultRunnable) {
            try {
                resultRunnable.run();
            } catch (Exception e) {
                writeFailure(e);
                return;
            }
            final SessionID sessionId = this.sessionId;
            if (sessionId == null) {
                final IllegalStateException e = Logs.REMOTING.noSessionCreated();
                writeFailure(e);
                //noinspection ConstantConditions
                throw e;
            }
            writeResponse(sessionId);
        }

        void writeResponse(SessionID sessionID) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.OPEN_SESSION_RESPONSE);
                os.writeShort(invId);
                final byte[] encodedForm = sessionID.getEncodedForm();
                PackedInteger.writePackedInteger(os, encodedForm.length);
                os.write(encodedForm);
                if (1 <= version && version <= 2) {
                    final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                    marshaller.start(Marshalling.createByteOutput(os));
                    marshaller.writeObject(getWeakAffinity());
                    marshaller.finish();
                } else {
                    assert version >= 3;
                    os.writeByte(txnCmd);
                }
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB session open response write failed", e);
            }
        }
    }

    <T> RemotingInvocationRequest<T> constructRequest(int invId, final EJBLocator<T> locator, final Affinity weakAffinity, final SecurityIdentity identity, final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier, final Map<String, Object> attachments, final EJBMethodLocator methodLocator, final Object[] parameters, final int responseCompressLevel) {
        return new RemotingInvocationRequest<T>(invId, locator, weakAffinity, identity, transactionSupplier, attachments, methodLocator, parameters, responseCompressLevel);
    }

    final class RemotingInvocationRequest<T> extends RemotingRequest implements InvocationRequest<T> {
        final Map<String, Object> attachments;
        final EJBLocator<T> locator;
        final EJBMethodLocator methodLocator;
        final Object[] parameters;
        final int responseCompressLevel;

        RemotingInvocationRequest(final int invId, final EJBLocator<T> locator, final Affinity weakAffinity, final SecurityIdentity identity, final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier, final Map<String, Object> attachments, final EJBMethodLocator methodLocator, final Object[] parameters, final int responseCompressLevel) {
            super(invId, weakAffinity, identity, transactionSupplier);
            this.locator = locator;
            this.attachments = attachments;
            this.methodLocator = methodLocator;
            this.parameters = parameters;
            this.responseCompressLevel = responseCompressLevel;
        }

        @NotNull
        public EJBLocator<T> getEJBLocator() {
            return locator;
        }

        public Map<String, Object> getAttachments() {
            return attachments;
        }

        public EJBMethodLocator getMethodLocator() {
            return methodLocator;
        }

        public Object[] getParameters() {
            return parameters;
        }

        public void execute(@NotNull final ExceptionSupplier<?, Exception> resultSupplier) {
            final Object result;
            try {
                result = resultSupplier.get();
            } catch (CancellationException e) {
                if (version >= 3) {
                    writeCancellation();
                } else {
                    writeFailure(e);
                }
                return;
            } catch (Exception e) {
                writeFailure(e);
                return;
            }
            writeResponse(result);
        }

        public void convertToStateful(@NotNull final SessionID sessionId) throws IllegalArgumentException, IllegalStateException {
            if (! (locator instanceof StatelessEJBLocator<?>)) {
                throw new IllegalArgumentException();
            }
            super.convertToStateful(sessionId);
        }

        void writeResponse(final Object response) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(responseCompressLevel > 0 ? Protocol.COMPRESSED_INVOCATION_MESSAGE : Protocol.INVOCATION_RESPONSE);
                os.writeShort(invId);
                if (version >= 3) {
                    os.writeByte(txnCmd);
                    if (sessionId == null) {
                        os.writeBoolean(false);
                    } else {
                        os.writeBoolean(true);
                        final byte[] bytes = sessionId.getEncodedForm();
                        PackedInteger.writePackedInteger(os, bytes.length);
                        os.write(bytes);
                    }
                }
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
                invocations.removeKey(invId);
            }
        }

        void writeCancellation() {
            if (version >= 3) try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.CANCEL_RESPONSE);
                os.writeShort(invId);
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(invId);
            } else {
                writeFailure(Logs.REMOTING.requestCancelled());
            }
        }
    }

    static final class InProgress {
        private final RemotingInvocationRequest<?> incomingInvocation;
        private final CancelHandle cancelHandle;

        InProgress(final RemotingInvocationRequest<?> incomingInvocation, final CancelHandle cancelHandle) {
            this.incomingInvocation = incomingInvocation;
            this.cancelHandle = cancelHandle;
        }

        int getInvId() {
            return incomingInvocation.invId;
        }

        CancelHandle getCancelHandle() {
            return cancelHandle;
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
