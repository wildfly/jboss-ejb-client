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

import java.io.IOException;

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import org.jboss.ejb.client.TransactionID;
import org.jboss.ejb.client.XidTransactionID;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.Connection;
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
        this.channel = EJBClientChannel.from(connection);
    }

    public void rollback(final Xid xid) throws XAException {
        executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_ROLLBACK_REQUEST, false, false, false);
    }

    public void setRollbackOnly(final Xid xid) throws XAException {
        // no operation
    }

    public void beforeCompletion(final Xid xid) throws XAException {
        executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_BEFORE_COMPLETION_REQUEST, false, false, false);
    }

    public int prepare(final Xid xid) throws XAException {
        return executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_PREPARE_REQUEST, true, false, false);
    }

    public void forget(final Xid xid) throws XAException {
        executeSimpleInvocation(new XidTransactionID(xid), Protocol.TXN_FORGET_REQUEST, false, false, false);
    }

    public void commit(final Xid xid, final boolean onePhase) throws XAException {
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
                    return flag ? StreamUtils.readPackedSignedInt32(inputStream) : 0;
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

    public Xid[] recover(final int flag, final String parentName) throws XAException {
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
        }
    }

    public SimpleTransactionControl begin(final int timeout) throws SystemException {
        return new EJBSimpleTransactionControl(channel, timeout);
    }
}
