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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A serialized version of EJB XAResource which can only be used during transaction recovery
 *
 * @author Jaikiran Pai
 */
class RecoveryOnlySerializedEJBXAResource implements XAResource, Serializable {

    private static final long serialVersionUID = -8675861321388933320L;

    private final String ejbReceiverNodeName;

    RecoveryOnlySerializedEJBXAResource(final String ejbReceiverNodeName) {
        this.ejbReceiverNodeName = ejbReceiverNodeName;
    }

    @Override
    public void commit(Xid xid, boolean onePhase) throws XAException {
        final List<EJBReceiverContext> receiverContexts = ReceiverRegistrationListener.INSTANCE.getRelevantReceiverContexts(this.ejbReceiverNodeName);
        if (receiverContexts.isEmpty()) {
            final EJBClientContext context = EJBClientContext.getCurrent();
            // getting the node EJB receiver should cause the receiver contexts list to be populated
            if (context == null || context.getNodeEJBReceiver(ejbReceiverNodeName) == null || receiverContexts.isEmpty()) {
                Logs.TXN.debugf("No EJB receiver contexts available for committing EJB XA resource for Xid %s during transaction recovery. Returning XAException.XA_RETRY", xid);
                throw new XAException(XAException.XA_RETRY);
            }
        }
        final XidTransactionID transactionID = new XidTransactionID(xid);
        for (final EJBReceiverContext receiverContext : receiverContexts) {
            final EJBReceiver receiver = receiverContext.getReceiver();
            Logs.TXN.debugf("%s sending commit request for Xid %s to EJB receiver with node name %s during recovery. One phase? %s", this, xid, receiver.getNodeName(), onePhase);
            receiver.sendCommit(receiverContext, transactionID, onePhase);
        }
    }

    @Override
    public void rollback(Xid xid) throws XAException {
        final List<EJBReceiverContext> receiverContexts = ReceiverRegistrationListener.INSTANCE.getRelevantReceiverContexts(this.ejbReceiverNodeName);
        if (receiverContexts.isEmpty()) {
            final EJBClientContext context = EJBClientContext.getCurrent();
            // getting the node EJB receiver should cause the receiver contexts list to be populated
            if (context == null || context.getNodeEJBReceiver(ejbReceiverNodeName) == null || receiverContexts.isEmpty()) {
                Logs.TXN.debugf("No EJB receiver contexts available for rolling back EJB XA resource for Xid %s during transaction recovery. Returning XAException.XA_RETRY", xid);
                throw new XAException(XAException.XA_RETRY);
            }
        }

        final XidTransactionID transactionID = new XidTransactionID(xid);
        for (final EJBReceiverContext receiverContext : receiverContexts) {
            final EJBReceiver receiver = receiverContext.getReceiver();
            Logs.TXN.debugf("%s sending rollback request for Xid %s to EJB receiver with node name %s during recovery", this, xid, receiver.getNodeName());
            receiver.sendRollback(receiverContext, transactionID);
        }
    }

    @Override
    public void forget(Xid xid) throws XAException {
        final List<EJBReceiverContext> receiverContexts = ReceiverRegistrationListener.INSTANCE.getRelevantReceiverContexts(this.ejbReceiverNodeName);
        if (receiverContexts.isEmpty()) {
            final EJBClientContext context = EJBClientContext.getCurrent();
            // getting the node EJB receiver should cause the receiver contexts list to be populated
            if (context == null || context.getNodeEJBReceiver(ejbReceiverNodeName) == null || receiverContexts.isEmpty()) {
                Logs.TXN.debugf("No EJB receiver contexts available for forgetting EJB XA resource for Xid %s during transaction recovery. Returning XAException.XA_RETRY", xid);
                throw new XAException(XAException.XA_RETRY);
            }
        }
        final XidTransactionID transactionID = new XidTransactionID(xid);
        for (final EJBReceiverContext receiverContext : receiverContexts) {
            final EJBReceiver receiver = receiverContext.getReceiver();
            Logs.TXN.debugf("%s sending forget request for Xid %s to EJB receiver with node name %s during recovery", this, xid, receiver.getNodeName());
            receiver.sendForget(receiverContext, transactionID);
        }
    }

    @Override
    public void end(Xid xid, int i) throws XAException {
        Logs.TXN.debugf("Ignoring end request on XAResource %s since this XAResource is only meant for transaction recovery", this);
    }

    @Override
    public int getTransactionTimeout() throws XAException {
        return 0;
    }

    @Override
    public boolean isSameRM(XAResource xaResource) throws XAException {
        return false;
    }

    @Override
    public int prepare(Xid xid) throws XAException {
        if (Logs.TXN.isDebugEnabled()) {
            Logs.TXN.debug("Prepare wasn't supposed to be called on " + this + " since this XAResource is only meant for transaction recovery. "
                    + "Ignoring the prepare request for xid " + xid);
        }
        return XA_OK;
    }

    @Override
    public Xid[] recover(int i) throws XAException {
        return new Xid[0];
    }


    @Override
    public boolean setTransactionTimeout(int i) throws XAException {
        return false;
    }

    @Override
    public void start(Xid xid, int i) throws XAException {
        Logs.TXN.debugf("Ignoring start request on XAResource %s since this XAResource is only meant for transaction recovery", this);
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RecoveryOnlySerializedEJBXAResource");
        sb.append("{ejbReceiverNodeName='").append(ejbReceiverNodeName).append('\'');
        sb.append('}');
        return sb.toString();
    }

    /**
     * Listener which keeps track of the relevant EJB receivers which have been registered in various EJB client contexts.
     * These receivers, if relevant, will then be used during transaction recovery.
     */
    static class ReceiverRegistrationListener implements EJBClientContextListener {

        static final ReceiverRegistrationListener INSTANCE = new ReceiverRegistrationListener();

        private final List<EJBReceiverContext> relevantReceiverContexts = Collections.synchronizedList(new ArrayList<EJBReceiverContext>());

        private ReceiverRegistrationListener() {
        }

        @Override
        public void contextClosed(EJBClientContext ejbClientContext) {
        }

        @Override
        public void receiverRegistered(final EJBReceiverContext receiverContext) {
            this.relevantReceiverContexts.add(receiverContext);
        }

        @Override
        public void receiverUnRegistered(final EJBReceiverContext receiverContext) {
            this.relevantReceiverContexts.remove(receiverContext);
        }

        private List<EJBReceiverContext> getRelevantReceiverContexts(final String nodeName) {
            final List<EJBReceiverContext> eligibleReceiverContext = new ArrayList<EJBReceiverContext>();
            synchronized (relevantReceiverContexts) {
                for (final EJBReceiverContext receiverContext : relevantReceiverContexts) {
                    if (receiverContext == null) {
                        continue;
                    }
                    if (nodeName == null) {
                        eligibleReceiverContext.add(receiverContext);
                    } else if (nodeName.equals(receiverContext.getReceiver().getNodeName())) {
                        eligibleReceiverContext.add(receiverContext);
                    }
                }
            }
            return eligibleReceiverContext;
        }

    }
}
