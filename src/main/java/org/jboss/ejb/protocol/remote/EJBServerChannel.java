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

import static org.xnio.IoUtils.safeClose;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.SocketAddress;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
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
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.RequestSendFailedException;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.TransactionID;
import org.jboss.ejb.client.UserTransactionID;
import org.jboss.ejb.client.XidTransactionID;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.CancelHandle;
import org.jboss.ejb.server.ClusterTopologyListener;
import org.jboss.ejb.server.InvocationRequest;
import org.jboss.ejb.server.ListenerHandle;
import org.jboss.ejb.server.ModuleAvailabilityListener;
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
import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3.util.MessageTracker;
import org.wildfly.common.Assert;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.transaction.client.ImportResult;
import org.wildfly.transaction.client.LocalTransaction;
import org.wildfly.transaction.client.SimpleXid;
import org.wildfly.transaction.client.provider.remoting.RemotingTransactionServer;
import org.wildfly.transaction.client.spi.SubordinateTransactionControl;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
@SuppressWarnings("deprecation")
final class EJBServerChannel {

    private final RemotingTransactionServer transactionServer;
    private final Channel channel;
    private final int version;
    private final MessageTracker messageTracker;
    private final MarshallerFactory marshallerFactory;
    private final MarshallingConfiguration configuration;
    private final IntIndexHashMap<InProgress> invocations = new IntIndexHashMap<>(InProgress::getInvId);

    EJBServerChannel(final RemotingTransactionServer transactionServer, final Channel channel, final int version, final MessageTracker messageTracker) {
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
            configuration.setObjectTable(ProtocolV3ObjectTable.INSTANCE);
            configuration.setVersion(4);
        }
        marshallerFactory = new RiverMarshallerFactory();
        this.configuration = configuration;
    }

    Channel.Receiver getReceiver(final Association association, final ListenerHandle handle1, final ListenerHandle handle2) {
        return new ReceiverImpl(association, handle1, handle2);
    }

    ClusterTopologyListener createTopologyListener() {
        return new ClusterTopologyWriter();
    }

    ModuleAvailabilityListener createModuleListener() {
        return new ModuleAvailabilityWriter();
    }

    class ReceiverImpl implements Channel.Receiver {
        private final Association association;
        private final ListenerHandle handle1;
        private final ListenerHandle handle2;

        ReceiverImpl(final Association association, final ListenerHandle handle1, final ListenerHandle handle2) {
            this.association = association;
            this.handle1 = handle1;
            this.handle2 = handle2;
        }

        public void handleError(final Channel channel, final IOException error) {
            handle1.close();
            handle2.close();
        }

        public void handleEnd(final Channel channel) {
            handle1.close();
            handle2.close();
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

            connection.getLocalIdentity(securityContext).runAs((Runnable) () ->
                association.receiveSessionOpenRequest(new RemotingSessionOpenRequest<Object>(
                    invId,
                    identifier,
                    transactionSupplier
                ))
            );
        }

        void handleInvocationRequest(final int invId, final InputStream input) throws IOException, ClassNotFoundException {
            final MarshallingConfiguration configuration = EJBServerChannel.this.configuration.clone();
            final ServerClassResolver classResolver = new ServerClassResolver();
            configuration.setClassResolver(classResolver);
            Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(configuration);
            unmarshaller.start(Marshalling.createByteInput(input));

            final EJBIdentifier identifier;
            final EJBMethodLocator methodLocator;

            final Connection connection = channel.getConnection();
            if (version >= 3) {
                identifier = unmarshaller.readObject(EJBIdentifier.class);
                methodLocator = unmarshaller.readObject(EJBMethodLocator.class);
            } else {
                assert version <= 2;
                String sigString = UTFUtils.readUTFZBytes(unmarshaller);
                String appName = unmarshaller.readObject(String.class);
                String moduleName = unmarshaller.readObject(String.class);
                String distinctName = unmarshaller.readObject(String.class);
                String beanName = unmarshaller.readObject(String.class);
                identifier = new EJBIdentifier(appName, moduleName, beanName, distinctName);

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

            association.receiveInvocationRequest(new RemotingInvocationRequest(
                invId, connection, association, identifier, methodLocator, classResolver, unmarshaller
            ));
        }
    }

    ExceptionSupplier<ImportResult<?>, SystemException> readTransaction(final DataInput input) throws IOException {
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
        SessionID sessionId;

        protected RemotingRequest(final int invId) {
            this.invId = invId;
        }

        public Executor getRequestExecutor() {
            return channel.getConnection().getEndpoint().getXnioWorker();
        }

        public SocketAddress getPeerAddress() {
            return channel.getConnection().getPeerAddress();
        }

        public SocketAddress getLocalAddress() {
            return channel.getConnection().getLocalAddress();
        }

        public String getProtocol() {
            return channel.getConnection().getProtocol();
        }

        public boolean isBlockingCaller() {
            return false;
        }

        public void writeNoSuchEJB() {
            final String message = Logs.REMOTING.remoteMessageNoSuchEJB(getEJBIdentifier());
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.NO_SUCH_EJB);
                os.writeShort(invId);
                os.writeUTF(message);
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(invId);
            }
        }

        public void writeCancelResponse() {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.CANCEL_RESPONSE);
                os.writeShort(invId);
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(invId);
            }
        }

        public void writeNotStateful() {
            final String message = Logs.REMOTING.remoteMessageEJBNotStateful(getEJBIdentifier());
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.EJB_NOT_STATEFUL);
                os.writeShort(invId);
                os.writeUTF(message);
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(invId);
            }
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
        final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier;
        int txnCmd = 0; // assume nobody will ask about the transaction

        RemotingSessionOpenRequest(final int invId, final EJBIdentifier identifier, final ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier) {
            super(invId);
            this.transactionSupplier = transactionSupplier;
            this.identifier = identifier;
        }

        @NotNull
        public EJBIdentifier getEJBIdentifier() {
            return identifier;
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

        public void writeException(@NotNull final Exception exception) {
            Assert.checkNotNullParam("exception", exception);
            writeFailure(exception);
        }

        public void convertToStateful(@NotNull final SessionID sessionId) throws IllegalArgumentException, IllegalStateException {
            super.convertToStateful(sessionId);
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.OPEN_SESSION_RESPONSE);
                os.writeShort(invId);
                final byte[] encodedForm = sessionId.getEncodedForm();
                PackedInteger.writePackedInteger(os, encodedForm.length);
                os.write(encodedForm);
                if (1 <= version && version <= 2) {
                    final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                    marshaller.start(Marshalling.createByteOutput(os));
                    // V2 needs weak affinity to the current node
                    marshaller.writeObject(new NodeAffinity(channel.getConnection().getEndpoint().getName()));
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

    final class RemotingInvocationRequest extends RemotingRequest implements InvocationRequest {
        final Connection connection;
        final Association association;
        final EJBIdentifier identifier;
        final EJBMethodLocator methodLocator;
        final ServerClassResolver classResolver;
        final Unmarshaller remaining;
        int txnCmd = 0; // assume nobody will ask about the transaction

        RemotingInvocationRequest(final int invId, final Connection connection, final Association association, final EJBIdentifier identifier, final EJBMethodLocator methodLocator, final ServerClassResolver classResolver, final Unmarshaller remaining) {
            super(invId);
            this.connection = connection;
            this.association = association;
            this.identifier = identifier;
            this.methodLocator = methodLocator;
            this.classResolver = classResolver;
            this.remaining = remaining;
        }

        public Resolved getRequestContent(final ClassLoader classLoader) throws IOException, ClassNotFoundException {
            classResolver.setClassLoader(classLoader);
            int responseCompressLevel = 0;
            // resolve the rest of everything here
            try (Unmarshaller unmarshaller = remaining) {
                Affinity weakAffinity = Affinity.NONE;
                ExceptionSupplier<ImportResult<?>, SystemException> transactionSupplier = null;
                final SecurityIdentity identity;
                final EJBLocator<?> locator;
                if (version >= 3) {
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
                    if (identifier != locator.getIdentifier()) {
                        throw Logs.REMOTING.mismatchedMethodLocation();
                    }

                } else {
                    assert version <= 2;

                    // always use connection identity
                    identity = connection.getLocalIdentity();

                    locator = unmarshaller.readObject(EJBLocator.class);
                    // do identity checks for these strings to guarantee integrity.  can't check identifier because that class didn't exist in V2
                    //noinspection StringEquality
                    if (identifier.getAppName() != locator.getAppName() ||
                        identifier.getModuleName() != locator.getModuleName() ||
                        identifier.getBeanName() != locator.getBeanName() ||
                        identifier.getDistinctName() != locator.getDistinctName()) {

                        throw Logs.REMOTING.mismatchedMethodLocation();
                    }
                }
                Object[] parameters = new Object[methodLocator.getParameterCount()];
                for (int i = 0; i < parameters.length; i ++) {
                    parameters[i] = unmarshaller.readObject();
                }
                int attachmentCount = PackedInteger.readPackedInteger(unmarshaller);
                final Map<String, Object> attachments = new HashMap<>(attachmentCount);
                for (int i = 0; i < attachmentCount; i ++) {
                    String attName = unmarshaller.readObject(String.class);
                    if (attName.equals(EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY)) {
                        if (version <= 2) {
                            // only supported for protocol v1/2 - read out transaction ID
                            Map<Object, Object> map = (Map<Object, Object>) unmarshaller.readObject();
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
                            weakAffinity = (Affinity) map.getOrDefault(AttachmentKeys.WEAK_AFFINITY, weakAffinity);


                        } else {
                            // discard content for v3
                            unmarshaller.readObject();
                        }
                    } else {
                        attachments.put(attName, unmarshaller.readObject());
                    }
                }
                unmarshaller.finish();

                final CancelHandle handle = identity.runAs((PrivilegedAction<CancelHandle>) () -> association.receiveInvocationRequest(this));
                invocations.put(new InProgress(this, handle));

                final ExceptionSupplier<ImportResult<?>, SystemException> finalTransactionSupplier = transactionSupplier;
                final int finalResponseCompressLevel = responseCompressLevel;
                return new Resolved() {

                    @NotNull
                    public Map<String, Object> getAttachments() {
                        return attachments;
                    }

                    @NotNull
                    public Object[] getParameters() {
                        return parameters;
                    }

                    @NotNull
                    public EJBLocator<?> getEJBLocator() {
                        return locator;
                    }

                    public boolean hasTransaction() {
                        return finalTransactionSupplier != null;
                    }

                    public Transaction getTransaction() throws SystemException, IllegalStateException {
                        if (finalTransactionSupplier == null) {
                            return null;
                        }
                        if (txnCmd != 0) {
                            throw new IllegalStateException();
                        }
                        final ImportResult<?> importResult = finalTransactionSupplier.get();
                        if (importResult.isNew()) {
                            txnCmd = 1;
                        } else {
                            txnCmd = 2;
                        }
                        return importResult.getTransaction();
                    }

                    public void writeInvocationResult(final Object result) {
                        try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                            os.writeByte(finalResponseCompressLevel > 0 ? Protocol.COMPRESSED_INVOCATION_MESSAGE : Protocol.INVOCATION_RESPONSE);
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
                            try (OutputStream output = finalResponseCompressLevel > 0 ? new DeflaterOutputStream(os, new Deflater(finalResponseCompressLevel, false)) : os) {
                                final Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
                                marshaller.start(Marshalling.createByteOutput(output));
                                marshaller.writeObject(result);
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

                };
            }
        }

        @NotNull
        public EJBIdentifier getEJBIdentifier() {
            return identifier;
        }

        public void writeNoSuchMethod() {
            final String message = Logs.REMOTING.remoteMessageNoSuchMethod(methodLocator, identifier);
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.NO_SUCH_METHOD);
                os.writeShort(invId);
                os.writeUTF(message);
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(invId);
            }
        }

        public void writeSessionNotActive() {
            final String message = Logs.REMOTING.remoteMessageSessionNotActive(methodLocator, identifier);
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.NO_SUCH_METHOD);
                os.writeShort(invId);
                os.writeUTF(message);
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB response write failed", e);
            } finally {
                invocations.removeKey(invId);
            }
        }

        public void writeException(@NotNull final Exception exception) {
            Assert.checkNotNullParam("exception", exception);
            if (exception instanceof CancellationException && version >= 3) {
                writeCancellation();
            } else {
                writeFailure(exception);
            }
        }

        public EJBMethodLocator getMethodLocator() {
            return methodLocator;
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
        private final RemotingInvocationRequest incomingInvocation;
        private final CancelHandle cancelHandle;

        InProgress(final RemotingInvocationRequest incomingInvocation, final CancelHandle cancelHandle) {
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

    final class ClusterTopologyWriter implements ClusterTopologyListener {
        ClusterTopologyWriter() {
        }

        public void clusterTopology(final List<ClusterInfo> clusterInfoList) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.CLUSTER_TOPOLOGY_COMPLETE);
                PackedInteger.writePackedInteger(os, clusterInfoList.size());
                for (ClusterInfo clusterInfo : clusterInfoList) {
                    os.writeUTF(clusterInfo.getClusterName());
                    final List<NodeInfo> nodeInfoList = clusterInfo.getNodeInfoList();
                    PackedInteger.writePackedInteger(os, nodeInfoList.size());
                    for (NodeInfo nodeInfo : nodeInfoList) {
                        os.writeUTF(nodeInfo.getNodeName());
                        final List<MappingInfo> mappingInfoList = nodeInfo.getMappingInfoList();
                        PackedInteger.writePackedInteger(os, mappingInfoList.size());
                        for (MappingInfo mappingInfo : mappingInfoList) {
                            boolean is6 = mappingInfo.getSourceAddress() instanceof Inet6Address;
                            if (is6) {
                                PackedInteger.writePackedInteger(os, mappingInfo.getNetmaskBits() << 1);
                            } else {
                                PackedInteger.writePackedInteger(os, mappingInfo.getNetmaskBits() << 1 | 1);
                            }
                            os.write(mappingInfo.getSourceAddress().getAddress());
                            os.writeUTF(mappingInfo.getDestinationAddress());
                            os.writeShort(mappingInfo.getDestinationPort());
                        }
                    }
                }
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB cluster message write failed", e);
            }
        }

        public void clusterRemoval(final List<String> clusterNames) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.CLUSTER_TOPOLOGY_REMOVAL);
                PackedInteger.writePackedInteger(os, clusterNames.size());
                for (String clusterName : clusterNames) {
                    os.writeUTF(clusterName);
                }
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB cluster message write failed", e);
            }
        }

        public void clusterNewNodesAdded(final ClusterInfo clusterInfo) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.CLUSTER_TOPOLOGY_ADDITION);
                PackedInteger.writePackedInteger(os, 1);
                os.writeUTF(clusterInfo.getClusterName());
                final List<NodeInfo> nodeInfoList = clusterInfo.getNodeInfoList();
                PackedInteger.writePackedInteger(os, nodeInfoList.size());
                for (NodeInfo nodeInfo : nodeInfoList) {
                    os.writeUTF(nodeInfo.getNodeName());
                    final List<MappingInfo> mappingInfoList = nodeInfo.getMappingInfoList();
                    PackedInteger.writePackedInteger(os, mappingInfoList.size());
                    for (MappingInfo mappingInfo : mappingInfoList) {
                        boolean is6 = mappingInfo.getSourceAddress() instanceof Inet6Address;
                        if (is6) {
                            PackedInteger.writePackedInteger(os, mappingInfo.getNetmaskBits() << 1);
                        } else {
                            PackedInteger.writePackedInteger(os, mappingInfo.getNetmaskBits() << 1 | 1);
                        }
                        os.write(mappingInfo.getSourceAddress().getAddress());
                        os.writeUTF(mappingInfo.getDestinationAddress());
                        os.writeShort(mappingInfo.getDestinationPort());
                    }
                }
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB cluster message write failed", e);
            }
        }

        public void clusterNodesRemoved(final List<ClusterRemovalInfo> clusterRemovalInfoList) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(Protocol.CLUSTER_TOPOLOGY_NODE_REMOVAL);
                PackedInteger.writePackedInteger(os, clusterRemovalInfoList.size());
                for (ClusterRemovalInfo removalInfo : clusterRemovalInfoList) {
                    os.writeUTF(removalInfo.getClusterName());
                    final List<String> nodeNamesList = removalInfo.getNodeNames();
                    PackedInteger.writePackedInteger(os, nodeNamesList.size());
                    for (String name : nodeNamesList) {
                        os.writeUTF(name);
                    }
                }
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB cluster message write failed", e);
            }
        }
    }

    final class ModuleAvailabilityWriter implements ModuleAvailabilityListener {
        ModuleAvailabilityWriter() {
        }

        public void moduleAvailable(final List<ModuleIdentifier> modules) {
            doWrite(true, modules);
        }

        public void moduleUnavailable(final List<ModuleIdentifier> modules) {
            doWrite(false, modules);
        }

        private void doWrite(final boolean available, final List<ModuleIdentifier> modules) {
            try (MessageOutputStream os = messageTracker.openMessageUninterruptibly()) {
                os.writeByte(available ? Protocol.MODULE_AVAILABLE : Protocol.MODULE_UNAVAILABLE);
                PackedInteger.writePackedInteger(os, modules.size());
                for (ModuleIdentifier module : modules) {
                    final String appName = module.getAppName();
                    os.writeUTF(appName == null ? "" : appName);
                    final String moduleName = module.getModuleName();
                    os.writeUTF(moduleName == null ? "" : moduleName);
                    final String distinctName = module.getDistinctName();
                    os.writeUTF(distinctName == null ? "" : distinctName);
                }
            } catch (IOException e) {
                // nothing to do at this point; the client doesn't want the response
                Logs.REMOTING.trace("EJB availability message write failed", e);
            }
        }
    }
}
