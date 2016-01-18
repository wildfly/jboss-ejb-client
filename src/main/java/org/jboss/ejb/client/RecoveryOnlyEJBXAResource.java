/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * An EJB {@link XAResource} which is supposed to be used only during transaction recovery.
 *
 * @author Jaikiran Pai
 */
class RecoveryOnlyEJBXAResource implements XAResource {

    private final EJBReceiverContext receiverContext;
    private final String transactionOriginNodeIdentifier;

    /**
     * @param transactionOriginNodeIdentifier
     *                        The node identifier of the node from which the transaction originated
     * @param receiverContext The EJB receiver context of the target EJB receiver/server which will be used to fetch the Xid(s)
     *                        which need to be recovered
     */
    RecoveryOnlyEJBXAResource(final String transactionOriginNodeIdentifier, final EJBReceiverContext receiverContext) {
        this.receiverContext = receiverContext;
        this.transactionOriginNodeIdentifier = transactionOriginNodeIdentifier;
    }

    @Override
    public void commit(Xid xid, boolean onePhase) throws XAException {
        final XidTransactionID transactionID = new XidTransactionID(xid);
        final EJBReceiver receiver = receiverContext.getReceiver();
        Logs.TXN.debug("Sending commit request for Xid " + xid + " to EJB receiver with node name " + receiver.getNodeName() + " during recovery. One phase? " + onePhase);
        receiver.sendCommit(receiverContext, transactionID, onePhase);
    }

    @Override
    public void end(Xid xid, int i) throws XAException {
        Logs.TXN.debug("Ignoring end request on XAResource " + this + " since this XAResource is only meant for transaction recovery");
    }

    @Override
    public void forget(Xid xid) throws XAException {
        final XidTransactionID transactionID = new XidTransactionID(xid);
        final EJBReceiver receiver = receiverContext.getReceiver();
        Logs.TXN.debug("Sending forget request for Xid " + xid + " to EJB receiver with node name " + receiver.getNodeName() + " during recovery");
        receiver.sendForget(receiverContext, transactionID);
    }

    @Override
    public int getTransactionTimeout() throws XAException {
        return 0;
    }

    @Override
    public boolean isSameRM(XAResource xaResource) throws XAException {
        if (xaResource == null) {
            return false;
        }
        boolean toReturn = EJBClientManagedTransactionContext.isEJBXAResourceClass(xaResource.getClass().getName());

        if (toReturn) {
            toReturn = xaResource instanceof RecoveryOnlyEJBXAResource;
            if (toReturn) {
                final EJBReceiver receiver = receiverContext.getReceiver();
                RecoveryOnlyEJBXAResource other = (RecoveryOnlyEJBXAResource)xaResource;
                final EJBReceiver otherReceiver = other.receiverContext.getReceiver();
                toReturn = receiver.getNodeName().equals(otherReceiver.getNodeName());
            }
        }
        return toReturn;
    }

    @Override
    public int prepare(Xid xid) throws XAException {
        Logs.TXN.debugf("Called prepare on recovery-only resource for xid %s", this, xid);
        throw new XAException(XAException.XAER_RMERR);
    }

    @Override
    public Xid[] recover(final int flags) throws XAException {
        final EJBReceiver receiver = receiverContext.getReceiver();
        Logs.TXN.debug("Send recover request for transaction origin node identifier " + transactionOriginNodeIdentifier + " to EJB receiver with node name " + receiver.getNodeName());
        return receiver.sendRecover(receiverContext, transactionOriginNodeIdentifier, flags);
    }

    @Override
    public void rollback(Xid xid) throws XAException {
        final XidTransactionID transactionID = new XidTransactionID(xid);
        final EJBReceiver receiver = receiverContext.getReceiver();
        Logs.TXN.debug("Sending rollback request for Xid " + xid + " to EJB receiver with node name " + receiver.getNodeName() + " during recovery");
        receiver.sendRollback(receiverContext, transactionID);
    }

    @Override
    public boolean setTransactionTimeout(int i) throws XAException {
        return false;
    }

    @Override
    public void start(Xid xid, int i) throws XAException {
        Logs.TXN.debug("Ignoring start request on XAResource " + this + " since this XAResource is only meant for transaction recovery");
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RecoveryOnlyEJBXAResource");
        sb.append("{receiverContext=").append(receiverContext);
        sb.append(", transactionOriginNodeIdentifier='").append(transactionOriginNodeIdentifier).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
