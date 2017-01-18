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
import static java.security.AccessController.doPrivileged;
import static org.xnio.IoUtils.safeClose;

import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.zip.InflaterInputStream;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.NoSuchEJBException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.Xid;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKey;
import org.jboss.ejb.client.AttachmentKeys;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.TransactionID;
import org.jboss.ejb.client.UserTransactionID;
import org.jboss.ejb.client.XidTransactionID;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3._private.IntIndexMap;
import org.jboss.remoting3.util.Invocation;
import org.jboss.remoting3.util.InvocationTracker;
import org.jboss.remoting3.util.StreamUtils;
import org.wildfly.common.Assert;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceRegistration;
import org.wildfly.discovery.ServiceRegistry;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.impl.LocalRegistryAndDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.transaction.client.ContextTransactionManager;
import org.wildfly.transaction.client.LocalTransaction;
import org.wildfly.transaction.client.RemoteTransaction;
import org.wildfly.transaction.client.RemoteTransactionContext;
import org.wildfly.transaction.client.XAOutflowHandle;
import org.wildfly.transaction.client.provider.remoting.SimpleIdResolver;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
@SuppressWarnings("deprecation")
class EJBClientChannel {

    private static final Supplier<ServiceRegistry> REGISTRY_SUPPLIER = doPrivileged((PrivilegedAction<Supplier<ServiceRegistry>>) ServiceRegistry.getContextManager()::getPrivilegedSupplier);

    private final MarshallerFactory marshallerFactory;

    private final Channel channel;
    private final int version;

    private final InvocationTracker invocationTracker;

    private final ServiceRegistry serviceRegistry;
    private final MarshallingConfiguration configuration;
    private final ConcurrentMap<DiscKey, ServiceRegistration> registrationsMap = new ConcurrentHashMap<>();
    private final IntIndexMap<UserTransactionID> userTxnIds = new IntIndexHashMap<UserTransactionID>(UserTransactionID::getId);

    private final RemoteTransactionContext transactionContext;
    private final AtomicReference<FutureResult<EJBClientChannel>> futureResultRef;
    private final LocalRegistryAndDiscoveryProvider discoveryProvider = new LocalRegistryAndDiscoveryProvider();

    EJBClientChannel(final Channel channel, final int version, final FutureResult<EJBClientChannel> futureResult) {
        this.channel = channel;
        this.version = version;
        marshallerFactory = Marshalling.getProvidedMarshallerFactory("river");
        MarshallingConfiguration configuration = new MarshallingConfiguration();
        if (version < 3) {
            configuration.setClassTable(ProtocolV1ClassTable.INSTANCE);
            configuration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
            configuration.setObjectResolver(ProtocolV1ObjectResolver.INSTANCE);
            configuration.setVersion(2);
        } else {
            configuration.setObjectTable(ProtocolV3ObjectTable.INSTANCE);
            configuration.setVersion(4);
            // server does not present v3 unless the transaction service is also present
        }
        transactionContext = RemoteTransactionContext.getInstance();
        this.serviceRegistry = ServiceRegistry.create(discoveryProvider);
        this.configuration = configuration;
        invocationTracker = new InvocationTracker(this.channel, channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue(), EJBClientChannel::mask);
        futureResultRef = new AtomicReference<>(futureResult);
    }

    static int mask(int original) {
        return original & 0xffff;
    }

    static class DiscKey {
        private final String appName;
        private final String moduleName;
        private final String distinctName;
        private final int hashCode;

        DiscKey(final String appName, final String moduleName, final String distinctName) {
            this.appName = appName;
            this.moduleName = moduleName;
            this.distinctName = distinctName;
            hashCode = Objects.hash(appName, moduleName, distinctName);
        }

        public boolean equals(final Object obj) {
            return obj instanceof DiscKey && equals((DiscKey) obj);
        }

        private boolean equals(final DiscKey other) {
            return other == this || appName.equals(other.appName) && moduleName.equals(other.moduleName) && distinctName.equals(other.distinctName);
        }

        public int hashCode() {
            return hashCode;
        }
    }

    private void processMessage(final MessageInputStream message) {
        boolean leaveOpen = false;
        try {
            final int msg = message.readUnsignedByte();
            switch (msg) {
                case Protocol.TXN_RESPONSE:
                case Protocol.INVOCATION_RESPONSE:
                case Protocol.OPEN_SESSION_RESPONSE:
                case Protocol.APPLICATION_EXCEPTION:
                case Protocol.CANCEL_RESPONSE:
                case Protocol.NO_SUCH_EJB:
                case Protocol.NO_SUCH_METHOD:
                case Protocol.SESSION_NOT_ACTIVE:
                case Protocol.EJB_NOT_STATEFUL:
                case Protocol.PROCEED_ASYNC_RESPONSE: {
                    final int invId = message.readUnsignedShort();
                    leaveOpen = invocationTracker.signalResponse(invId, msg, message, false);
                    break;
                }
                case Protocol.MODULE_AVAILABLE: {
                    int count = StreamUtils.readPackedSignedInt32(message);
                    final Connection connection = channel.getConnection();
                    final URI peerURI = connection.getPeerURI();
//                    builder.setUriSchemeAuthority(connection.getPeerURISchemeAuthority()) XXX todo
                    final ServiceRegistry serviceRegistry = this.serviceRegistry;
                    final ConcurrentMap<DiscKey, ServiceRegistration> registrationsMap = this.registrationsMap;
                    for (int i = 0; i < count; i ++) {
                        String appName = message.readUTF();
                        final String moduleName = message.readUTF();
                        if (appName.isEmpty()) appName = moduleName;
                        final String distinctName = message.readUTF();
                        final DiscKey key = new DiscKey(appName, moduleName, distinctName);
                        final ServiceURL.Builder builder = new ServiceURL.Builder();
                        builder.setUri(peerURI);
                        builder.setAbstractType("ejb");
                        builder.setAbstractTypeAuthority("jboss");
                        if (! appName.isEmpty()) {
                            builder.addAttribute("ejb-app", AttributeValue.fromString(appName));
                        }
                        builder.addAttribute("ejb-module", AttributeValue.fromString(appName + "/" + moduleName));
                        if (! distinctName.isEmpty()) {
                            builder.addAttribute("ejb-distinct", AttributeValue.fromString(distinctName));
                            if (! appName.isEmpty()) {
                                builder.addAttribute("ejb-app-distinct", AttributeValue.fromString(appName + "/" + distinctName));
                            }
                            builder.addAttribute("ejb-module-distinct", AttributeValue.fromString(appName + "/" + moduleName + "/" + distinctName));
                        }
                        final ServiceRegistration registration = serviceRegistry.registerService(builder.create());
                        final ServiceRegistration old = registrationsMap.put(key, registration);
                        if (old != null) {
                            old.close();
                        }
                    }
                    final FutureResult<EJBClientChannel> futureResult = futureResultRef.get();
                    if (futureResult != null && futureResultRef.compareAndSet(futureResult, null)) {
                        // done!
                        futureResult.setResult(this);
                    }
                    break;
                }
                case Protocol.MODULE_UNAVAILABLE: {
                    int count = StreamUtils.readPackedSignedInt32(message);
                    final ConcurrentMap<DiscKey, ServiceRegistration> registrationsMap = this.registrationsMap;
                    for (int i = 0; i < count; i ++) {
                        String appName = message.readUTF();
                        final String moduleName = message.readUTF();
                        if (appName.isEmpty()) appName = moduleName;
                        final String distinctName = message.readUTF();
                        final DiscKey key = new DiscKey(appName, moduleName, distinctName);
                        final ServiceRegistration old = registrationsMap.remove(key);
                        if (old != null) {
                            old.close();
                        }
                    }
                    break;
                }
                case Protocol.CLUSTER_TOPOLOGY_COMPLETE: {
                    // TODO read message
                    break;
                }
                case Protocol.CLUSTER_TOPOLOGY_REMOVAL: {
                    // TODO read message
                    break;
                }
                case Protocol.CLUSTER_TOPOLOGY_ADDITION: {
                    // TODO read message
                    break;
                }
                case Protocol.CLUSTER_TOPOLOGY_NODE_REMOVAL: {
                    // TODO read message
                    break;
                }
                default: {
                    // ignore message
                }
            }
        } catch (IOException e) {
        } finally {
            if (! leaveOpen) {
                safeClose(message);
            }
        }
    }

    private static final AttachmentKey<MethodInvocation> INV_KEY = new AttachmentKey<>();

    public void processInvocation(final EJBReceiverInvocationContext receiverContext) {
        MethodInvocation invocation = invocationTracker.addInvocation(id -> new MethodInvocation(id, receiverContext));
        final EJBClientInvocationContext invocationContext = receiverContext.getClientInvocationContext();
        invocationContext.putAttachment(INV_KEY, invocation);
        final EJBLocator<?> locator = invocationContext.getLocator();
        final int peerIdentityId;
        if (version >= 3) try {
            peerIdentityId = channel.getConnection().getPeerIdentityId();
        } catch (AuthenticationException e) {
            receiverContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(e));
            return;
        }
        else {
            peerIdentityId = 0; // unused
        }
        try (MessageOutputStream out = invocationTracker.allocateMessage()) {
            try {
                out.write(Protocol.INVOCATION_REQUEST);
                out.writeShort(invocation.getIndex());

                Marshaller marshaller = getMarshaller();
                marshaller.start(Marshalling.createByteOutput(out));

                final Method invokedMethod = invocationContext.getInvokedMethod();
                final Object[] parameters = invocationContext.getParameters();

                if (version < 3) {
                    // method name as UTF string
                    out.writeUTF(invokedMethod.getName());

                    // write the method signature as UTF string
                    out.writeUTF(invocationContext.getMethodSignatureString());

                    // protocol 1 & 2 redundant locator objects
                    marshaller.writeObject(locator.getAppName());
                    marshaller.writeObject(locator.getModuleName());
                    marshaller.writeObject(locator.getDistinctName());
                    marshaller.writeObject(locator.getBeanName());
                } else {

                    // write identifier to allow the peer to find the class loader
                    marshaller.writeObject(locator.getIdentifier());

                    // write method locator
                    marshaller.writeObject(invocationContext.getMethodLocator());

                    // write weak affinity
                    marshaller.writeObject(null); // todo

                    // write response compression info
                    if (invocationContext.isCompressResponse()) {
                        marshaller.writeByte(invocationContext.getCompressionLevel());
                    } else {
                        marshaller.writeByte(0);
                    }

                    // write sec context
                    marshaller.writeInt(peerIdentityId);

                    // write txn context
                    invocation.setOutflowHandle(writeTransaction(invocationContext.getTransaction(), marshaller));
                }
                // write the invocation locator itself
                marshaller.writeObject(locator);

                // and the parameters
                if (parameters != null && parameters.length > 0) {
                    for (final Object methodParam : parameters) {
                        marshaller.writeObject(methodParam);
                    }
                }

                // now, attachments
                // we write out the private (a.k.a JBoss specific) attachments as well as public invocation context data
                // (a.k.a user application specific data)
                final Map<AttachmentKey<?>, ?> privateAttachments = invocationContext.getAttachments();
                final Map<String, Object> contextData = invocationContext.getContextData();

                // write the attachment count which is the sum of invocation context data + 1 (since we write
                // out the private attachments under a single key with the value being the entire attachment map)
                int totalContextData = contextData.size();
                if (version >= 3) {
                    // Just write the attachments.
                    PackedInteger.writePackedInteger(marshaller, totalContextData);

                    for (Map.Entry<String, Object> invocationContextData : contextData.entrySet()) {
                        marshaller.writeObject(invocationContextData.getKey());
                        marshaller.writeObject(invocationContextData.getValue());
                    }
                } else {
                    final Transaction transaction = invocationContext.getTransaction();

                    // We are only marshalling those attachments whose keys are present in the object table
                    final Map<AttachmentKey<?>, Object> marshalledPrivateAttachments = new HashMap<>();
                    for (final Map.Entry<AttachmentKey<?>, ?> entry : privateAttachments.entrySet()) {
                        final AttachmentKey<?> key = entry.getKey();
                        if (key == AttachmentKeys.TRANSACTION_ID_KEY) {
                            // skip!
                        } else if (ProtocolV1ObjectTable.INSTANCE.getObjectWriter(key) != null) {
                            marshalledPrivateAttachments.put(key, entry.getValue());
                        }
                    }

                    if (transaction != null) {
                        marshalledPrivateAttachments.put(AttachmentKeys.TRANSACTION_ID_KEY, calculateTransactionId(transaction));
                    }

                    final boolean hasPrivateAttachments = ! marshalledPrivateAttachments.isEmpty();
                    if (hasPrivateAttachments) {
                        totalContextData++;
                    }
                    // Note: The code here is just for backward compatibility of 1.x and 2.x versions of EJB client project.
                    // Attach legacy transaction ID, if there is an active txn.

                    if (transaction != null) {
                        // we additionally add/duplicate the transaction id under a different attachment key
                        // to preserve backward compatibility. This is here just for 1.0.x backward compatibility
                        totalContextData++;
                    }
                    // backward compatibility code block for transaction id ends here.

                    PackedInteger.writePackedInteger(marshaller, totalContextData);
                    // write out public (application specific) context data
                    for (Map.Entry<String, Object> invocationContextData : contextData.entrySet()) {
                        marshaller.writeObject(invocationContextData.getKey());
                        marshaller.writeObject(invocationContextData.getValue());
                    }
                    if (hasPrivateAttachments) {
                        // now write out the JBoss specific attachments under a single key and the value will be the
                        // entire map of JBoss specific attachments
                        marshaller.writeObject(EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY);
                        marshaller.writeObject(marshalledPrivateAttachments);
                    }

                    // Note: The code here is just for backward compatibility of 1.0.x version of EJB client project
                    // against AS7 7.1.x releases. Discussion here https://github.com/jbossas/jboss-ejb-client/pull/11#issuecomment-6573863
                    if (transaction != null) {
                        // we additionally add/duplicate the transaction id under a different attachment key
                        // to preserve backward compatibility. This is here just for 1.0.x backward compatibility
                        marshaller.writeObject(TransactionID.PRIVATE_DATA_KEY);
                        // This transaction id attachment duplication *won't* cause increase in EJB protocol message payload
                        // since we rely on JBoss Marshalling to use back references for the same transaction id object being
                        // written out
                        marshaller.writeObject(marshalledPrivateAttachments.get(AttachmentKeys.TRANSACTION_ID_KEY));
                    }
                    // backward compatibility code block for transaction id ends here.
                }

                // finished
                marshaller.finish();
            } catch (IOException e) {
                out.cancel();
                throw e;
            }
        } catch (IOException | RollbackException | SystemException | RuntimeException e) {
            receiverContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException(e.getMessage(), e)));
            return;
        }
    }

    private TransactionID calculateTransactionId(final Transaction transaction) throws RollbackException, SystemException, InvalidTransactionException {
        final URI location = channel.getConnection().getPeerURI();
        Assert.assertNotNull(transaction);
        if (transaction instanceof RemoteTransaction) {
            final SimpleIdResolver ir = ((RemoteTransaction) transaction).getProviderInterface(SimpleIdResolver.class);
            if (ir == null) throw Logs.TXN.cannotEnlistTx();
            return new UserTransactionID(channel.getConnection().getRemoteEndpointName(), ir.getTransactionId(channel.getConnection()));
        } else if (transaction instanceof LocalTransaction) {
            final LocalTransaction localTransaction = (LocalTransaction) transaction;
            final XAOutflowHandle outflowHandle = transactionContext.outflowTransaction(location, localTransaction);
            // always verify V1/2 outflows
            outflowHandle.verifyEnlistment();
            return new XidTransactionID(outflowHandle.getXid());
        } else {
            throw Logs.TXN.cannotEnlistTx();
        }
    }

    private XAOutflowHandle writeTransaction(final Transaction transaction, final DataOutput dataOutput) throws IOException, RollbackException, SystemException {
        final URI location = channel.getConnection().getPeerURI();
        if (transaction == null) {
            dataOutput.writeByte(0);
            return null;
        } else if (transaction instanceof RemoteTransaction) {
            dataOutput.writeByte(1);
            final SimpleIdResolver ir = ((RemoteTransaction) transaction).getProviderInterface(SimpleIdResolver.class);
            if (ir == null) throw Logs.TXN.cannotEnlistTx();
            final int id = ir.getTransactionId(channel.getConnection());
            dataOutput.writeInt(id);
            PackedInteger.writePackedInteger(dataOutput, ((RemoteTransaction) transaction).getRemainingTime());
            return null;
        } else if (transaction instanceof LocalTransaction) {
            final LocalTransaction localTransaction = (LocalTransaction) transaction;
            final XAOutflowHandle outflowHandle = transactionContext.outflowTransaction(location, localTransaction);
            final Xid xid = outflowHandle.getXid();
            dataOutput.writeByte(2);
            PackedInteger.writePackedInteger(dataOutput, xid.getFormatId());
            final byte[] gtid = xid.getGlobalTransactionId();
            dataOutput.writeByte(gtid.length);
            dataOutput.write(gtid);
            final byte[] bq = xid.getBranchQualifier();
            // this will normally be zero, but write it anyway just in case we need to change it later
            dataOutput.writeByte(bq.length);
            dataOutput.write(bq);
            PackedInteger.writePackedInteger(dataOutput, outflowHandle.getRemainingTime());
            return outflowHandle;
        } else {
            throw Logs.TXN.cannotEnlistTx();
        }
    }

    boolean cancelInvocation(final EJBReceiverInvocationContext receiverContext, boolean cancelIfRunning) {
        if (version < 3 && ! cancelIfRunning) {
            // keep legacy behavior
            return false;
        }
        final MethodInvocation invocation = receiverContext.getClientInvocationContext().getAttachment(INV_KEY);
        if (invocation.alloc()) try {
            final int index = invocation.getIndex();
            try (MessageOutputStream out = invocationTracker.allocateMessage()) {
                out.write(Protocol.CANCEL_REQUEST);
                out.writeShort(index);
                if (version >= 3) {
                    out.writeBoolean(cancelIfRunning);
                }
            } catch (IOException ignored) {}
        } finally {
            invocation.free();
        }
        // now await the result
        return invocation.receiverInvocationContext.getClientInvocationContext().awaitCancellationResult();
    }

    private Marshaller getMarshaller() throws IOException {
        return marshallerFactory.createMarshaller(configuration);
    }

    public <T> StatefulEJBLocator<T> openSession(final StatelessEJBLocator<T> statelessLocator) throws Exception {
        SessionOpenInvocation<T> invocation = invocationTracker.addInvocation(id -> new SessionOpenInvocation<>(id, statelessLocator));
        try (MessageOutputStream out = invocationTracker.allocateMessage()) {
            out.write(Protocol.OPEN_SESSION_REQUEST);
            out.writeShort(invocation.getIndex());
            writeRawIdentifier(statelessLocator, out);
            if (version >= 3) {
                //TODO Elytron
                final int peerIdentityId = 0; //channel.getConnection().getPeerIdentityId();
                out.writeInt(peerIdentityId);
                writeTransaction(ContextTransactionManager.getInstance().getTransaction(), out);
            }
        } catch (IOException e) {
            CreateException createException = new CreateException(e.getMessage());
            createException.initCause(e);
            throw createException;
        }
        // await the response
        return invocation.getResult();
    }

    private static <T> void writeRawIdentifier(final EJBLocator<T> statelessLocator, final MessageOutputStream out) throws IOException {
        final String appName = statelessLocator.getAppName();
        out.writeUTF(appName == null ? "" : appName);
        out.writeUTF(statelessLocator.getModuleName());
        final String distinctName = statelessLocator.getDistinctName();
        out.writeUTF(distinctName == null ? "" : distinctName);
        out.writeUTF(statelessLocator.getBeanName());
    }

    static IoFuture<EJBClientChannel> construct(final Channel channel, final RemoteEJBReceiver remoteEJBReceiver) {
        FutureResult<EJBClientChannel> futureResult = new FutureResult<>();
        // now perform opening negotiation: receive server greeting
        channel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                futureResult.setException(error);
            }

            public void handleEnd(final Channel channel) {
                futureResult.setCancelled();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                // receive message body
                try {
                    final int version = min(3, StreamUtils.readInt8(message));
                    // drain the rest of the message because it's just garbage really
                    while (message.read() != -1) {
                        message.skip(Long.MAX_VALUE);
                    }
                    // send back result
                    try (MessageOutputStream out = channel.writeMessage()) {
                        out.write(version);
                        out.write(Protocol.RIVER_BYTES);
                    }
                    // almost done; wait for initial module available report
                    final EJBClientChannel ejbClientChannel = new EJBClientChannel(channel, version, futureResult);
                    channel.receiveMessage(new Channel.Receiver() {
                        public void handleError(final Channel channel, final IOException error) {
                            safeClose(channel);
                        }

                        public void handleEnd(final Channel channel) {
                            safeClose(channel);
                        }

                        public void handleMessage(final Channel channel, final MessageInputStream message) {
                            try {
                                ejbClientChannel.processMessage(message);
                            } finally {
                                channel.receiveMessage(this);
                            }
                        }
                    });
                } catch (final IOException e) {
                    channel.closeAsync();
                    channel.addCloseHandler((closed, exception) -> futureResult.setException(e));
                }
            }
        });
        futureResult.addCancelHandler(new Cancellable() {
            public Cancellable cancel() {
                if (futureResult.setCancelled()) {
                    safeClose(channel);
                }
                return this;
            }
        });
        return futureResult.getIoFuture();
    }

    InvocationTracker getInvocationTracker() {
        return invocationTracker;
    }

    final class SessionOpenInvocation<T> extends Invocation {

        private final StatelessEJBLocator<T> statelessLocator;
        private int id;
        private MessageInputStream inputStream;
        private XAOutflowHandle outflowHandle;

        protected SessionOpenInvocation(final int index, final StatelessEJBLocator<T> statelessLocator) {
            super(index);
            this.statelessLocator = statelessLocator;
        }

        public void handleResponse(final int id, final MessageInputStream inputStream) {
            synchronized (this) {
                this.id = id;
                this.inputStream = inputStream;
                notifyAll();
            }
        }

        public void handleClosed() {
            synchronized (this) {
                notifyAll();
            }
        }

        void setOutflowHandle(final XAOutflowHandle outflowHandle) {
            this.outflowHandle = outflowHandle;
        }

        XAOutflowHandle getOutflowHandle() {
            return outflowHandle;
        }

        StatefulEJBLocator<T> getResult() throws Exception {
            Exception e;
            try (ResponseMessageInputStream response = removeInvocationResult()) {
                switch (id) {
                    case Protocol.OPEN_SESSION_RESPONSE: {
                        final Affinity affinity;
                        int size = StreamUtils.readPackedUnsignedInt32(response);
                        byte[] bytes = new byte[size];
                        response.readFully(bytes);
                        // todo: pool unmarshallers?  use very small instance count config?
                        if (1 <= version && version <= 2) {
                            try (final Unmarshaller unmarshaller = createUnmarshaller()) {
                                unmarshaller.start(response);
                                affinity = unmarshaller.readObject(Affinity.class);
                                unmarshaller.finish();
                            }
                        } else {
                            affinity = statelessLocator.getAffinity();
                            final int cmd = response.readUnsignedByte();
                            final XAOutflowHandle outflowHandle = getOutflowHandle();
                            if (outflowHandle != null) {
                                if (cmd == 0) {
                                    outflowHandle.forgetEnlistment();
                                } else if (cmd == 1) {
                                    try {
                                        outflowHandle.verifyEnlistment();
                                    } catch (RollbackException | SystemException e1) {
                                        throw new EJBException(e1);
                                    }
                                } else if (cmd == 2) {
                                    outflowHandle.nonMasterEnlistment();
                                }
                            }
                        }
                        return statelessLocator.withSessionAndAffinity(SessionID.createSessionID(bytes), affinity);
                    }
                    case Protocol.APPLICATION_EXCEPTION: {
                        try (final Unmarshaller unmarshaller = createUnmarshaller()) {
                            unmarshaller.start(response);
                            e = unmarshaller.readObject(Exception.class);
                            unmarshaller.finish();
                            if (version < 3) {
                                // drain off attachments so the server doesn't complain
                                while (response.read() != -1) {
                                    response.skip(Long.MAX_VALUE);
                                }
                            }
                        }
                        // todo: glue stack traces
                        break;
                    }
                    case Protocol.CANCEL_RESPONSE: {
                        throw Logs.REMOTING.requestCancelled();
                    }
                    case Protocol.NO_SUCH_EJB: {
                        final String message = response.readUTF();
                        throw new NoSuchEJBException(message);
                    }
                    case Protocol.EJB_NOT_STATEFUL: {
                        final String message = response.readUTF();
                        // todo: I don't think this is the best exception type for this case...
                        throw new IllegalArgumentException(message);
                    }
                    default: {
                        throw new EJBException("Invalid EJB creation response (id " + id + ")");
                    }
                }
            } catch (ClassNotFoundException | IOException ex) {
                throw new EJBException("Failed to read session create response", ex);
            }
            if (e == null) {
                throw new EJBException("Null exception response");
            }
            // throw the application exception outside of the try block
            throw e;
        }

        private ResponseMessageInputStream removeInvocationResult() {
            MessageInputStream mis;
            int id;
            try {
                synchronized (this) {
                    for (; ; ) {
                        id = this.getIndex();
                        if (inputStream != null) {
                            mis = inputStream;
                            inputStream = null;
                            break;
                        }
                        if (id == -1) {
                            throw new EJBException("Connection closed");
                        }
                        wait();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EJBException("Session creation interrupted");
            }
            return new ResponseMessageInputStream(mis, id);
        }
    }

    Unmarshaller createUnmarshaller() throws IOException {
        return marshallerFactory.createUnmarshaller(configuration);
    }

    Channel getChannel() {
        return channel;
    }

    UserTransactionID allocateUserTransactionID() {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final String nodeName = getChannel().getConnection().getRemoteEndpointName();
        final byte[] nameBytes;
        try {
            nameBytes = nodeName.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw Assert.unreachableCode();
        }
        final int length = nameBytes.length;
        if (length > 255) {
            throw Assert.unreachableCode();
        }
        final byte[] target = new byte[6 + length];
        target[0] = 0x01;
        target[1] = (byte) length;
        System.arraycopy(nameBytes, 0, target, 2, length);
        for (;;) {
            int id = random.nextInt();
            if (! userTxnIds.containsKey(id)) {
                target[2 + length] = (byte) (id >> 24);
                target[3 + length] = (byte) (id >> 16);
                target[4 + length] = (byte) (id >> 8);
                target[5 + length] = (byte) id;
                final UserTransactionID uti = (UserTransactionID) TransactionID.createTransactionID(target);
                if (userTxnIds.putIfAbsent(uti) == null) {
                    return uti;
                }
            }
        }
    }

    DiscoveryProvider getDiscoveryProvider() {
        return discoveryProvider;
    }

    final class MethodInvocation extends Invocation {
        private final EJBReceiverInvocationContext receiverInvocationContext;
        private final AtomicInteger refCounter = new AtomicInteger(1);
        private XAOutflowHandle outflowHandle;

        MethodInvocation(final int index, final EJBReceiverInvocationContext receiverInvocationContext) {
            super(index);
            this.receiverInvocationContext = receiverInvocationContext;
        }

        boolean alloc() {
            final AtomicInteger refCounter = this.refCounter;
            int oldVal;
            do {
                oldVal = refCounter.get();
                if (oldVal == 0) {
                    return false;
                }
            } while (! refCounter.compareAndSet(oldVal, oldVal + 1));
            return true;
        }

        void free() {
            final AtomicInteger refCounter = this.refCounter;
            final int newVal = refCounter.decrementAndGet();
            if (newVal == 0) {
                invocationTracker.remove(this);
            }
        }

        public void handleResponse(final int id, final MessageInputStream inputStream) {
            switch (id) {
                case Protocol.COMPRESSED_INVOCATION_MESSAGE: {
                    free();
                    receiverInvocationContext.resultReady(new MethodCallResultProducer(new InflaterInputStream(inputStream), id));
                    break;
                }
                case Protocol.INVOCATION_RESPONSE: {
                    free();
                    if (version >= 3) try {
                        final int cmd = inputStream.readUnsignedByte();
                        final XAOutflowHandle outflowHandle = getOutflowHandle();
                        if (outflowHandle != null) {
                            if (cmd == 0) {
                                outflowHandle.forgetEnlistment();
                            } else if (cmd == 1) {
                                outflowHandle.verifyEnlistment();
                            } else if (cmd == 2) {
                                outflowHandle.nonMasterEnlistment();
                            }
                        }
                        if (inputStream.readBoolean()) {
                            byte[] encoded = new byte[PackedInteger.readPackedInteger(inputStream)];
                            inputStream.read(encoded);
                            final SessionID sessionID = SessionID.createSessionID(encoded);
                            final EJBClientInvocationContext context = receiverInvocationContext.getClientInvocationContext();
                            EJBClient.convertToStateful(context.getInvokedProxy(), sessionID);
                        }
                    } catch (RuntimeException | IOException | RollbackException | SystemException e) {
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException(e)));
                        safeClose(inputStream);
                        break;
                    }
                    receiverInvocationContext.resultReady(new MethodCallResultProducer(inputStream, id));
                    break;
                }
                case Protocol.CANCEL_RESPONSE: {
                    free();
                    receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(Logs.REMOTING.requestCancelled()));
                    break;
                }
                case Protocol.APPLICATION_EXCEPTION: {
                    free();
                    receiverInvocationContext.resultReady(new ExceptionResultProducer(inputStream, id));
                    break;
                }
                case Protocol.NO_SUCH_EJB: {
                    free();
                    try {
                        final String message = inputStream.readUTF();
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new NoSuchEJBException(message)));
                    } catch (IOException e) {
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException("Failed to read 'No such EJB' response", e)));
                    } finally {
                        safeClose(inputStream);
                    }
                    break;
                }
                case Protocol.NO_SUCH_METHOD: {
                    free();
                    try {
                        final String message = inputStream.readUTF();
                        // todo: I don't think this is the best exception type for this case...
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new IllegalArgumentException(message)));
                    } catch (IOException e) {
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException("Failed to read 'No such EJB method' response", e)));
                    } finally {
                        safeClose(inputStream);
                    }
                    break;
                }
                case Protocol.SESSION_NOT_ACTIVE: {
                    free();
                    try {
                        final String message = inputStream.readUTF();
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException(message)));
                    } catch (IOException e) {
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException("Failed to read 'Session not active' response", e)));
                    } finally {
                        safeClose(inputStream);
                    }
                    break;
                }
                case Protocol.EJB_NOT_STATEFUL: {
                    free();
                    try {
                        final String message = inputStream.readUTF();
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException(message)));
                    } catch (IOException e) {
                        receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException("Failed to read 'EJB not stateful' response", e)));
                    } finally {
                        safeClose(inputStream);
                    }
                    break;
                }
                case Protocol.PROCEED_ASYNC_RESPONSE: {
                    // do not free; response is forthcoming
                    safeClose(inputStream);
                    receiverInvocationContext.proceedAsynchronously();
                    break;
                }
                default: {
                    free();
                    safeClose(inputStream);
                    receiverInvocationContext.resultReady(new EJBReceiverInvocationContext.ResultProducer.Failed(new EJBException("Unknown protocol response")));
                    break;
                }
            }
        }

        public void handleClosed() {
        }

        XAOutflowHandle getOutflowHandle() {
            return outflowHandle;
        }

        void setOutflowHandle(final XAOutflowHandle outflowHandle) {
            this.outflowHandle = outflowHandle;
        }

        class MethodCallResultProducer implements EJBReceiverInvocationContext.ResultProducer {

            private final InputStream inputStream;
            private final int id;

            MethodCallResultProducer(final InputStream inputStream, final int id) {
                this.inputStream = inputStream;
                this.id = id;
            }

            public Object getResult() throws Exception {
                final ResponseMessageInputStream response = new ResponseMessageInputStream(inputStream, id);
                Object result;
                try (final Unmarshaller unmarshaller = createUnmarshaller()) {
                    unmarshaller.start(response);
                    result = unmarshaller.readObject();
                    int attachments = unmarshaller.readUnsignedByte();
                    for (int i = 0; i < attachments; i ++) {
                        String key = unmarshaller.readObject(String.class);
                        if (key.equals(Affinity.WEAK_AFFINITY_CONTEXT_KEY)) {
                            receiverInvocationContext.getClientInvocationContext().putAttachment(AttachmentKeys.WEAK_AFFINITY, unmarshaller.readObject(Affinity.class));
                        } else {
                            // discard
                            unmarshaller.readObject();
                        }
                    }
                    unmarshaller.finish();
                } catch (IOException | ClassNotFoundException ex) {
                    throw new EJBException("Failed to read response", ex);
                }
                return result;
            }

            public void discardResult() {
                safeClose(inputStream);
            }
        }

        class ExceptionResultProducer implements EJBReceiverInvocationContext.ResultProducer {

            private final InputStream inputStream;
            private final int id;

            ExceptionResultProducer(final InputStream inputStream, final int id) {
                this.inputStream = inputStream;
                this.id = id;
            }

            public Object getResult() throws Exception {
                final ResponseMessageInputStream response = new ResponseMessageInputStream(inputStream, id);
                Exception e;
                try (final Unmarshaller unmarshaller = createUnmarshaller()) {
                    unmarshaller.start(response);
                    e = unmarshaller.readObject(Exception.class);
                    if (version < 3) {
                        // discard attachment data, if any
                        int attachments = unmarshaller.readUnsignedByte();
                        for (int i = 0; i < attachments; i ++) {
                            unmarshaller.readObject();
                            unmarshaller.readObject();
                        }
                    }
                    unmarshaller.finish();
                } catch (IOException | ClassNotFoundException ex) {
                    throw new EJBException("Failed to read response", ex);
                }
                if (e == null) {
                    throw new EJBException("Null exception response");
                }
                // todo: glue stack traces
                throw e;
            }

            public void discardResult() {
                safeClose(inputStream);
            }
        }
    }

    static class ResponseMessageInputStream extends MessageInputStream implements ByteInput {
        private final InputStream delegate;
        private final int id;

        ResponseMessageInputStream(final InputStream delegate, final int id) {
            this.delegate = delegate;
            this.id = id;
        }

        public int read() throws IOException {
            return delegate.read();
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            return delegate.read(b, off, len);
        }

        public long skip(final long n) throws IOException {
            return delegate.skip(n);
        }

        public void close() throws IOException {
            delegate.close();
        }

        public int getId() {
            return id;
        }
    }
}
