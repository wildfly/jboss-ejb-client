/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.ejb.protocol.remote;

import static java.lang.Math.min;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.IoUtils.safeClose;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
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
import org.jboss.ejb.client.EJBModuleIdentifier;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.RequestSendFailedException;
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
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3._private.IntIndexMap;
import org.jboss.remoting3.util.Invocation;
import org.jboss.remoting3.util.InvocationTracker;
import org.jboss.remoting3.util.StreamUtils;
import org.wildfly.common.Assert;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.transaction.client.ContextTransactionManager;
import org.wildfly.transaction.client.LocalTransaction;
import org.wildfly.transaction.client.RemoteTransaction;
import org.wildfly.transaction.client.RemoteTransactionContext;
import org.wildfly.transaction.client.XAOutflowHandle;
import org.wildfly.transaction.client.provider.remoting.SimpleIdResolver;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
@SuppressWarnings("deprecation")
class EJBClientChannel {

    private final MarshallerFactory marshallerFactory;

    private final Channel channel;
    private final int version;
    private final DiscoveredNodeRegistry discoveredNodeRegistry;

    private final InvocationTracker invocationTracker;

    private final MarshallingConfiguration configuration;
    private final IntIndexMap<UserTransactionID> userTxnIds = new IntIndexHashMap<UserTransactionID>(UserTransactionID::getId);

    private final RemoteTransactionContext transactionContext;
    private final AtomicInteger finishedParts = new AtomicInteger(0);
    private final AtomicReference<FutureResult<EJBClientChannel>> futureResultRef;

    EJBClientChannel(final Channel channel, final int version, final DiscoveredNodeRegistry discoveredNodeRegistry, final FutureResult<EJBClientChannel> futureResult) {
        this.channel = channel;
        this.version = version;
        this.discoveredNodeRegistry = discoveredNodeRegistry;
        marshallerFactory = Marshalling.getProvidedMarshallerFactory("river");
        MarshallingConfiguration configuration = new MarshallingConfiguration();
        configuration.setClassResolver(ProtocolClassResolver.INSTANCE);
        final Connection connection = channel.getConnection();
        if (version < 3) {
            configuration.setClassTable(ProtocolV1ClassTable.INSTANCE);
            configuration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
            configuration.setObjectResolver(new ProtocolV1ObjectResolver(connection, true));
            configuration.setVersion(2);
            // Do not wait for cluster topology report.
            finishedParts.set(0b10);
        } else {
            configuration.setObjectTable(ProtocolV3ObjectTable.INSTANCE);
            configuration.setObjectResolver(new ProtocolV3ObjectResolver(connection, true));
            configuration.setVersion(4);
            // server does not present v3 unless the transaction service is also present
        }
        transactionContext = RemoteTransactionContext.getInstance();
        this.configuration = configuration;
        invocationTracker = new InvocationTracker(this.channel, channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue(), EJBClientChannel::mask);
        futureResultRef = new AtomicReference<>(futureResult);
        final String nodeName = connection.getRemoteEndpointName();
        final NodeInformation nodeInformation = discoveredNodeRegistry.getNodeInformation(nodeName);
        nodeInformation.addAddress(this);
        nodeInformation.setInvalid(false);
        channel.addCloseHandler((ignored1, ignored2) -> nodeInformation.removeConnection(this));
    }

    static int mask(int original) {
        return original & 0xffff;
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
                case Protocol.BAD_VIEW_TYPE:
                case Protocol.PROCEED_ASYNC_RESPONSE:{
                    final int invId = message.readUnsignedShort();
                    leaveOpen = invocationTracker.signalResponse(invId, msg, message, false);
                    break;
                }
                case Protocol.COMPRESSED_INVOCATION_MESSAGE: {
                    DataInputStream inputStream = new DataInputStream(new InflaterInputStream(message));
                    final int realMessageId = inputStream.readByte();
                    final int invId = inputStream.readUnsignedShort();
                    leaveOpen = invocationTracker.signalResponse(invId, realMessageId, new ResponseMessageInputStream(inputStream, invId), false);
                    break;
                }
                case Protocol.MODULE_AVAILABLE: {
                    int count = StreamUtils.readPackedSignedInt32(message);
                    final NodeInformation nodeInformation = discoveredNodeRegistry.getNodeInformation(getChannel().getConnection().getRemoteEndpointName());
                    final EJBModuleIdentifier[] moduleList = new EJBModuleIdentifier[count];
                    for (int i = 0; i < count; i ++) {
                        final String appName = message.readUTF();
                        final String moduleName = message.readUTF();
                        final String distinctName = message.readUTF();
                        final EJBModuleIdentifier moduleIdentifier = new EJBModuleIdentifier(appName, moduleName, distinctName);
                        moduleList[i] = moduleIdentifier;

                        Logs.INVOCATION.debugf("Received MODULE_AVAILABLE(%x) message for module %s", msg, moduleIdentifier);

                    }
                    nodeInformation.addModules(this, moduleList);
                    finishPart(0b01);
                    break;
                }
                case Protocol.MODULE_UNAVAILABLE: {
                    int count = StreamUtils.readPackedSignedInt32(message);
                    final NodeInformation nodeInformation = discoveredNodeRegistry.getNodeInformation(getChannel().getConnection().getRemoteEndpointName());
                    final HashSet<EJBModuleIdentifier> set = new HashSet<>(count);
                    for (int i = 0; i < count; i ++) {
                        final String appName = message.readUTF();
                        final String moduleName = message.readUTF();
                        final String distinctName = message.readUTF();
                        final EJBModuleIdentifier moduleIdentifier = new EJBModuleIdentifier(appName, moduleName, distinctName);
                        set.add(moduleIdentifier);
                        Logs.INVOCATION.debugf("Received MODULE_UNAVAILABLE(%x) message for module %s", msg, moduleIdentifier);
                    }
                    nodeInformation.removeModules(this, set);
                    break;
                }
                case Protocol.CLUSTER_TOPOLOGY_ADDITION:
                case Protocol.CLUSTER_TOPOLOGY_COMPLETE: {
                    int clusterCount = StreamUtils.readPackedSignedInt32(message);
                    for (int i = 0; i < clusterCount; i ++) {
                        final String clusterName = message.readUTF();
                        int memberCount = StreamUtils.readPackedSignedInt32(message);
                        for (int j = 0; j < memberCount; j ++) {
                            final String nodeName = message.readUTF();
                            discoveredNodeRegistry.addNode(clusterName, nodeName);
                            final NodeInformation nodeInformation = discoveredNodeRegistry.getNodeInformation(nodeName);
                            Logs.INVOCATION.debugf("Received CLUSTER_TOPOLOGY(%x) message, registering cluster %s to node %s", msg, clusterName, nodeName);

                            // create and register the concrete ServiceURLs for each client mapping
                            int mappingCount = StreamUtils.readPackedSignedInt32(message);
                            for (int k = 0; k < mappingCount; k ++) {
                                int b = message.readUnsignedByte();
                                final boolean ip6 = allAreClear(b, 1);
                                int netmaskBits = b >>> 1;
                                final byte[] sourceIpBytes = new byte[ip6 ? 16 : 4];
                                message.readFully(sourceIpBytes);
                                final CidrAddress block = CidrAddress.create(sourceIpBytes, netmaskBits);
                                final String destHost = message.readUTF();
                                final int destPort = message.readUnsignedShort();
                                final InetSocketAddress destination = new InetSocketAddress(destHost, destPort);
                                nodeInformation.addAddress(channel.getConnection().getProtocol(), clusterName, block, destination);
                                Logs.INVOCATION.debugf("Received CLUSTER_TOPOLOGY(%x) message block, registering block %s to address %s", msg, block, destination);
                            }
                        }
                    }
                    finishPart(0b10);
                    break;
                }
                case Protocol.CLUSTER_TOPOLOGY_REMOVAL: {
                    int clusterCount = StreamUtils.readPackedSignedInt32(message);
                    for (int i = 0; i < clusterCount; i ++) {
                        String clusterName = message.readUTF();
                        discoveredNodeRegistry.removeCluster(clusterName);

                        Logs.INVOCATION.debugf("Received CLUSTER_TOPOLOGY_REMOVAL(%x) message for cluster %s", msg, clusterName);

                        for (NodeInformation nodeInformation : discoveredNodeRegistry.getAllNodeInformation()) {
                            nodeInformation.removeCluster(clusterName);
                        }
                    }
                    break;
                }
                case Protocol.CLUSTER_TOPOLOGY_NODE_REMOVAL: {
                    int clusterCount = StreamUtils.readPackedSignedInt32(message);
                    for (int i = 0; i < clusterCount; i ++) {
                        String clusterName = message.readUTF();
                        int memberCount = StreamUtils.readPackedSignedInt32(message);
                        for (int j = 0; j < memberCount; j ++) {
                            String nodeName = message.readUTF();
                            discoveredNodeRegistry.removeNode(clusterName, nodeName);
                            final NodeInformation nodeInformation = discoveredNodeRegistry.getNodeInformation(nodeName);
                            nodeInformation.removeCluster(clusterName);

                            Logs.INVOCATION.debugf("Received CLUSTER_TOPOLOGY_NODE_REMOVAL(%x) message for (cluster, node) = (%s, %s)", msg, clusterName, nodeName);

                        }
                    }
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

    public void processInvocation(final EJBReceiverInvocationContext receiverContext, final ConnectionPeerIdentity peerIdentity) {
        MethodInvocation invocation = invocationTracker.addInvocation(id -> new MethodInvocation(id, receiverContext));
        final EJBClientInvocationContext invocationContext = receiverContext.getClientInvocationContext();
        invocationContext.putAttachment(INV_KEY, invocation);
        final EJBLocator<?> locator = invocationContext.getLocator();
        final int peerIdentityId;
        if (version >= 3) {
            peerIdentityId = peerIdentity.getId();
        } else {
            peerIdentityId = 0; // unused
        }
        try (MessageOutputStream underlying = invocationTracker.allocateMessage()) {
            MessageOutputStream out = handleCompression(invocationContext, underlying);
            try {
                out.write(Protocol.INVOCATION_REQUEST);
                out.writeShort(invocation.getIndex());

                Marshaller marshaller = getMarshaller();
                marshaller.start(new NoFlushByteOutput(Marshalling.createByteOutput(out)));

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

                    // write sec context
                    marshaller.writeInt(peerIdentityId);

                    // write weak affinity
                    marshaller.writeObject(invocationContext.getWeakAffinity());

                    // write response compression info
                    if (invocationContext.isCompressResponse()) {
                        int compressionLevel = invocationContext.getCompressionLevel() > 0 ? invocationContext.getCompressionLevel() : 15;
                        marshaller.writeByte(compressionLevel);
                    } else {
                        marshaller.writeByte(0);
                    }

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
                underlying.cancel();
                throw e;
            } finally {
                out.close();
            }
        } catch (IOException e) {
            receiverContext.requestFailed(new RequestSendFailedException(e.getMessage() + " @ " + peerIdentity.getConnection().getPeerURI(), e, true), getRetryExecutor());
        } catch (RollbackException | SystemException | RuntimeException e) {
            receiverContext.requestFailed(new EJBException(e.getMessage(), e), getRetryExecutor());
            return;
        }
    }

    /**
     * Wraps the {@link MessageOutputStream message output stream} into a relevant {@link DataOutputStream}, taking into account various factors like the necessity to
     * compress the data that gets passed along the stream
     *
     * @param invocationContext         The EJB client invocation context
     * @param messageOutputStream       The message output stream that needs to be wrapped
     * @return the compressed stream
     * @throws IOException if a problem occurs
     */
    private MessageOutputStream handleCompression(final EJBClientInvocationContext invocationContext, final MessageOutputStream messageOutputStream) throws IOException {
        // if the negotiated protocol version doesn't support compressed messages then just return
        if(version == 1) {
            return messageOutputStream;
        }

        // if "hints" are disabled, just return a DataOutputStream without the necessity of processing any "hints"
        final Boolean hintsDisabled = invocationContext.getProxyAttachment(AttachmentKeys.HINTS_DISABLED);
        if (hintsDisabled != null && hintsDisabled) {
            if (Logs.REMOTING.isTraceEnabled()) {
                Logs.REMOTING.trace("Hints are disabled. Ignoring any CompressionHint on methods being invoked on view " + invocationContext.getViewClass());
            }
            return messageOutputStream;
        }

        // process any CompressionHint
        final int compressionLevel = invocationContext.getCompressionLevel();
        // write out a attachment to indicate whether or not the response has to be compressed (v2 only)
        if (version == 2 && invocationContext.isCompressResponse()) {
            invocationContext.putAttachment(AttachmentKeys.COMPRESS_RESPONSE, true);
            invocationContext.putAttachment(AttachmentKeys.RESPONSE_COMPRESSION_LEVEL, compressionLevel);
            if (Logs.REMOTING.isTraceEnabled()) {
                Logs.REMOTING.trace("Letting the server know that the response of method " + invocationContext.getInvokedMethod() + " has to be compressed with compression level = " + compressionLevel);
            }
        }

        // create a compressed invocation data *only* if the request has to be compressed (note, it's perfectly valid for certain methods to just specify that only the response is compressed)
        if (invocationContext.isCompressRequest()) {
            // write out the header indicating that it's a compressed stream
            messageOutputStream.write(Protocol.COMPRESSED_INVOCATION_MESSAGE);
            // create the deflater using the specified level
            final Deflater deflater = new Deflater(compressionLevel);
            // wrap the message outputstream with the deflater stream so that *any subsequent* data writes to the stream are compressed
            final DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(messageOutputStream, deflater);
            if (Logs.REMOTING.isTraceEnabled()) {
                Logs.REMOTING.trace("Using a compressing stream with compression level = " + compressionLevel + " for request data for EJB invocation on method " + invocationContext.getInvokedMethod());
            }
            return new WrapperMessageOutputStream(messageOutputStream, deflaterOutputStream);
        } else {
            // just return a normal DataOutputStream without any compression
            return messageOutputStream;
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
            PackedInteger.writePackedInteger(dataOutput, ((RemoteTransaction) transaction).getEstimatedRemainingTime());
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
        if (invocation == null) {
            // lost it somehow
            return false;
        }
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

    public <T> StatefulEJBLocator<T> openSession(final StatelessEJBLocator<T> statelessLocator, final ConnectionPeerIdentity identity) throws Exception {
        SessionOpenInvocation<T> invocation = invocationTracker.addInvocation(id -> new SessionOpenInvocation<>(id, statelessLocator));
        try (MessageOutputStream out = invocationTracker.allocateMessage()) {
            out.write(Protocol.OPEN_SESSION_REQUEST);
            out.writeShort(invocation.getIndex());
            writeRawIdentifier(statelessLocator, out);
            if (version >= 3) {
                out.writeInt(identity.getId());
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

    static IoFuture<EJBClientChannel> construct(final Channel channel, final DiscoveredNodeRegistry discoveredNodeRegistry) {
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
                        out.writeUTF("river");
                    }
                    // almost done; wait for initial module available report
                    final EJBClientChannel ejbClientChannel = new EJBClientChannel(channel, version, discoveredNodeRegistry, futureResult);
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
        private IOException ex;

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

        public void handleException(IOException cause) {
            synchronized (this) {
                this.ex = cause;
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
                        if (version >= 3) {
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
                        throw new NoSuchEJBException(message + " @ " + getChannel().getConnection().getPeerURI());
                    }
                    case Protocol.BAD_VIEW_TYPE: {
                        final String message = response.readUTF();
                        throw Logs.REMOTING.invalidViewTypeForInvocation(message);
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
                        if (ex != null) {
                            throw new EJBException(ex);
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

    void finishPart(int bit) {
        int oldVal, newVal;
        do {
            oldVal = finishedParts.get();
            if (allAreSet(oldVal, bit)) {
                return;
            }
            newVal = oldVal | bit;
        } while (! finishedParts.compareAndSet(oldVal, newVal));
        if (newVal == 0b11) {
            final FutureResult<EJBClientChannel> futureResult = futureResultRef.get();
            if (futureResult != null && futureResultRef.compareAndSet(futureResult, null)) {
                // done!
                futureResult.setResult(this);
            }
        }
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

        @Override
        public void handleResponse(int parameter, MessageInputStream inputStream) {
            handleResponse(parameter, new DataInputStream(inputStream));
        }

        private void handleResponse(final int id, final DataInputStream inputStream) {
            switch (id) {
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
//                            context.setWeakAffinity();
                        }
                    } catch (RuntimeException | IOException | RollbackException | SystemException e) {
                        receiverInvocationContext.requestFailed(new EJBException(e), getRetryExecutor());
                        safeClose(inputStream);
                        break;
                    }
                    receiverInvocationContext.resultReady(new MethodCallResultProducer(inputStream, id));
                    break;
                }
                case Protocol.CANCEL_RESPONSE: {
                    free();
                    if (version >= 3) {
                        final XAOutflowHandle outflowHandle = getOutflowHandle();
                        if (outflowHandle != null) outflowHandle.forgetEnlistment();
                    }
                    receiverInvocationContext.requestCancelled();
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
                        if (version >= 3) {
                            final XAOutflowHandle outflowHandle = getOutflowHandle();
                            if (outflowHandle != null) outflowHandle.forgetEnlistment();
                        }
                        final String message = inputStream.readUTF();
                        final EJBModuleIdentifier moduleIdentifier = receiverInvocationContext.getClientInvocationContext().getLocator().getIdentifier().getModuleIdentifier();
                        final NodeInformation nodeInformation = discoveredNodeRegistry.getNodeInformation(getChannel().getConnection().getRemoteEndpointName());
                        nodeInformation.removeModule(EJBClientChannel.this, moduleIdentifier);
                        receiverInvocationContext.requestFailed(new NoSuchEJBException(message + " @ " + getChannel().getConnection().getPeerURI()), getRetryExecutor());
                    } catch (IOException e) {
                        receiverInvocationContext.requestFailed(new EJBException("Failed to read 'No such EJB' response", e), getRetryExecutor());
                    } finally {
                        safeClose(inputStream);
                    }
                    break;
                }
                case Protocol.BAD_VIEW_TYPE: {
                    free();
                    try {
                        if (version >= 3) {
                            final XAOutflowHandle outflowHandle = getOutflowHandle();
                            if (outflowHandle != null) outflowHandle.forgetEnlistment();
                        }
                        final String message = inputStream.readUTF();
                        receiverInvocationContext.requestFailed(Logs.REMOTING.invalidViewTypeForInvocation(message), getRetryExecutor());
                    } catch (IOException e) {
                        receiverInvocationContext.requestFailed(new EJBException("Failed to read 'Bad EJB view type' response", e), getRetryExecutor());
                    } finally {
                        safeClose(inputStream);
                    }
                    break;
                }
                case Protocol.NO_SUCH_METHOD: {
                    free();
                    try {
                        if (version >= 3) {
                            final XAOutflowHandle outflowHandle = getOutflowHandle();
                            if (outflowHandle != null) outflowHandle.forgetEnlistment();
                        }
                        final String message = inputStream.readUTF();
                        // todo: I don't think this is the best exception type for this case...
                        receiverInvocationContext.requestFailed(new IllegalArgumentException(message), getRetryExecutor());
                    } catch (IOException e) {
                        receiverInvocationContext.requestFailed(new EJBException("Failed to read 'No such EJB method' response", e), getRetryExecutor());
                    } finally {
                        safeClose(inputStream);
                    }
                    break;
                }
                case Protocol.SESSION_NOT_ACTIVE: {
                    free();
                    try {
                        if (version >= 3) {
                            final XAOutflowHandle outflowHandle = getOutflowHandle();
                            if (outflowHandle != null) outflowHandle.forgetEnlistment();
                        }
                        final String message = inputStream.readUTF();
                        receiverInvocationContext.requestFailed(new EJBException(message), getRetryExecutor());
                    } catch (IOException e) {
                        receiverInvocationContext.requestFailed(new EJBException("Failed to read 'Session not active' response", e), getRetryExecutor());
                    } finally {
                        safeClose(inputStream);
                    }
                    break;
                }
                case Protocol.EJB_NOT_STATEFUL: {
                    free();
                    try {
                        if (version >= 3) {
                            final XAOutflowHandle outflowHandle = getOutflowHandle();
                            if (outflowHandle != null) outflowHandle.forgetEnlistment();
                        }
                        final String message = inputStream.readUTF();
                        receiverInvocationContext.requestFailed(new EJBException(message), getRetryExecutor());
                    } catch (IOException e) {
                        receiverInvocationContext.requestFailed(new EJBException("Failed to read 'EJB not stateful' response"), getRetryExecutor());
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
                    receiverInvocationContext.requestFailed(new EJBException("Unknown protocol response"), getRetryExecutor());
                    break;
                }
            }
        }

        public void handleClosed() {
            receiverInvocationContext.requestFailed(new EJBException(new ClosedChannelException()), getRetryExecutor());
        }

        public void handleException(IOException cause) {
            receiverInvocationContext.requestFailed(new EJBException(cause), getRetryExecutor());
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
                final ResponseMessageInputStream response;
                if(inputStream instanceof ResponseMessageInputStream) {
                    response = (ResponseMessageInputStream) inputStream;
                } else {
                    response = new ResponseMessageInputStream(inputStream, id);
                }
                Object result;
                try (final Unmarshaller unmarshaller = createUnmarshaller()) {
                    unmarshaller.start(response);
                    result = unmarshaller.readObject();
                    int attachments = unmarshaller.readUnsignedByte();
                    final EJBClientInvocationContext clientInvocationContext = receiverInvocationContext.getClientInvocationContext();
                    for (int i = 0; i < attachments; i ++) {
                        String key = unmarshaller.readObject(String.class);
                        if (key.equals(Affinity.WEAK_AFFINITY_CONTEXT_KEY)) {
                            final Affinity affinity = unmarshaller.readObject(Affinity.class);
                            clientInvocationContext.putAttachment(AttachmentKeys.WEAK_AFFINITY, affinity);
                            clientInvocationContext.setWeakAffinity(affinity);
                        } else if (key.equals(EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY)) {
                            // skip
                            unmarshaller.readObject();
                        } else {
                            clientInvocationContext.getContextData().put(key, unmarshaller.readObject());
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
                Exception e;
                try (final ResponseMessageInputStream response = new ResponseMessageInputStream(inputStream, id)) {
                    if (version >= 3) {
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
                    }
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

    private XnioWorker getRetryExecutor() {
        return getChannel().getConnection().getEndpoint().getXnioWorker();
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
