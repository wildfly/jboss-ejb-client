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

import java.io.IOException;

import jakarta.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.TransactionID;
import org.jboss.ejb.client.XidTransactionID;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.util.BlockingInvocation;
import org.jboss.remoting3.util.InvocationTracker;
import org.jboss.remoting3.util.StreamUtils;
import org.wildfly.transaction.client.provider.remoting.RemotingOperations;
import org.wildfly.transaction.client.spi.SimpleTransactionControl;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class EJBTransactionOperations implements RemotingOperations {
    private final EJBClientChannel channel;

    EJBTransactionOperations(final Connection connection) throws IOException {
        final EJBClientContext ejbClientContext = EJBClientContext.getCurrent();
        final RemoteEJBReceiver receiver = ejbClientContext.getAttachment(RemoteTransportProvider.ATTACHMENT_KEY);
        if (receiver != null) {
            this.channel = receiver.getClientChannel(connection);
        } else {
            throw Logs.REMOTING.noRemoteTransportOnEJBContext();
        }
    }

    public void rollback(final Xid xid, final ConnectionPeerIdentity peerIdentity) throws XAException {
        assert peerIdentity.getId() == 0;
        executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_ROLLBACK_REQUEST, false, false, false);
    }

    public void setRollbackOnly(final Xid xid, final ConnectionPeerIdentity peerIdentity) throws XAException {
        assert peerIdentity.getId() == 0;
        // no operation
    }

    public void beforeCompletion(final Xid xid, final ConnectionPeerIdentity peerIdentity) throws XAException {
        assert peerIdentity.getId() == 0;
        executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_BEFORE_COMPLETION_REQUEST, false, false, false);
    }

    public int prepare(final Xid xid, final ConnectionPeerIdentity peerIdentity) throws XAException {
        assert peerIdentity.getId() == 0;
        return executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_PREPARE_REQUEST, true, false, false);
    }

    public void forget(final Xid xid, final ConnectionPeerIdentity peerIdentity) throws XAException {
        assert peerIdentity.getId() == 0;
        executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_FORGET_REQUEST, false, false, false);
    }

    public void commit(final Xid xid, final boolean onePhase, final ConnectionPeerIdentity peerIdentity) throws XAException {
        assert peerIdentity.getId() == 0;
        executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_COMMIT_REQUEST, false, true, onePhase);
    }

    private int executeSimpleInvocation(final TransactionID transactionID, int type, boolean withAnswer, boolean withParam, boolean param) throws XAException {
        final InvocationTracker invocationTracker = channel.getInvocationTracker();
        final PlainTransactionInvocation invocation = invocationTracker.addInvocation(PlainTransactionInvocation::new);
        try (MessageOutputStream os = invocationTracker.allocateMessage(invocation)) {
            os.writeByte(type);
            os.writeShort(invocation.getIndex());
            final byte[] encoded = transactionID.getEncodedForm();
            PackedInteger.writePackedInteger(os, encoded.length);
            os.write(encoded);
            if (withParam) {
                os.writeBoolean(param);
            }
        } catch (IOException e) {
            throw new XAException(XAException.XAER_RMERR);
        }
        try (BlockingInvocation.Response response = invocation.getResponse()) {
            switch (response.getParameter()) {
                case Protocol.TXN_RESPONSE: {
                    final MessageInputStream inputStream = response.getInputStream();
                    boolean flag = inputStream.readBoolean();
                    if (flag != withAnswer) {
                        // unrecognized parameter
                        throw new XAException(XAException.XAER_RMFAIL);
                    }
                    return flag ? PackedInteger.readPackedInteger(inputStream) : 0;
                }
                case Protocol.APPLICATION_EXCEPTION: {
                    throw readAppException(channel, response);
                }
                default: {
                    throw new XAException(XAException.XAER_RMFAIL);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            invocation.cancel();
            throw new XAException(XAException.XAER_RMERR);
        } catch (IOException e) {
            throw new XAException(XAException.XAER_RMERR);
        } finally {
            invocationTracker.remove(invocation);
        }
    }

    static XAException readAppException(final EJBClientChannel channel, final BlockingInvocation.Response response) throws XAException {
        Exception e;
        try (final Unmarshaller unmarshaller = channel.createUnmarshaller()) {
            try (MessageInputStream inputStream = response.getInputStream()) {
                unmarshaller.start(Marshalling.createByteInput(inputStream));
                e = unmarshaller.readObject(Exception.class);
                unmarshaller.finish();
                // The version is probably < 3 else we would not be here
                // drain off attachments so the server doesn't complain
                while (inputStream.read() != -1) {
                    inputStream.skip(Long.MAX_VALUE);
                }
            }
        } catch (IOException | ClassNotFoundException e1) {
            throw new XAException(XAException.XAER_RMERR);
        }
        if (e == null) {
            throw new XAException(XAException.XAER_RMFAIL);
        }
        try {
            throw e;
        } catch (RuntimeException | XAException e1) {
            throw e1;
        } catch (Exception e1) {
            final XAException xae = new XAException(XAException.XAER_RMERR);
            xae.initCause(e1);
            return xae;
        }
    }

    static class PlainTransactionInvocation extends BlockingInvocation {
        PlainTransactionInvocation(final int index) {
            super(index);
        }
    }

    public Xid[] recover(final int flag, final String parentName, final ConnectionPeerIdentity peerIdentity) throws XAException {
        assert peerIdentity.getId() == 0;
        final InvocationTracker invocationTracker = channel.getInvocationTracker();
        final PlainTransactionInvocation invocation = invocationTracker.addInvocation(PlainTransactionInvocation::new);
        try (MessageOutputStream os = invocationTracker.allocateMessage(invocation)) {
            os.writeByte(Protocol.TXN_RECOVERY_REQUEST);
            os.writeShort(invocation.getIndex());
            os.writeUTF(parentName);
            os.writeInt(flag);
        } catch (IOException e) {
            throw new XAException(XAException.XAER_RMERR);
        }
        try (BlockingInvocation.Response response = invocation.getResponse()) {
            switch (response.getParameter()) {
                case Protocol.TXN_RECOVERY_RESPONSE: {
                    final MessageInputStream inputStream = response.getInputStream();
                    int count = StreamUtils.readPackedUnsignedInt31(inputStream);
                    final Xid[] xids = new Xid[count];
                    // unmarshall it :(
                    final Unmarshaller unmarshaller = channel.createUnmarshaller();
                    unmarshaller.start(Marshalling.createByteInput(inputStream));
                    for (int i = 0; i < count; i ++) {
                        xids[i ++] = unmarshaller.readObject(XidTransactionID.class).getXid();
                    }
                    unmarshaller.finish();
                    return xids;
                }
                case Protocol.APPLICATION_EXCEPTION: {
                    throw readAppException(channel, response);
                }
                default: {
                    throw new XAException(XAException.XAER_RMFAIL);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            invocation.cancel();
            throw new XAException(XAException.XAER_RMERR);
        } catch (IOException | ClassNotFoundException e) {
            throw new XAException(XAException.XAER_RMERR);
        } finally {
            invocationTracker.remove(invocation);
        }
    }

    public SimpleTransactionControl begin(final ConnectionPeerIdentity peerIdentity) throws SystemException {
        assert peerIdentity.getId() == 0;
        return new EJBSimpleTransactionControl(channel);
    }
}
