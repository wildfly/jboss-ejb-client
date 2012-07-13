/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A transaction context for environments with a {@link TransactionManager}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientManagedTransactionContext extends EJBClientTransactionContext {
    private final TransactionManager transactionManager;
    private final TransactionSynchronizationRegistry synchronizationRegistry;
    private static final AtomicReferenceFieldUpdater<ResourceImpl, State> stateUpdater = AtomicReferenceFieldUpdater.newUpdater(ResourceImpl.class, State.class, "state");

    EJBClientManagedTransactionContext(final TransactionManager transactionManager, final TransactionSynchronizationRegistry synchronizationRegistry) {
        this.transactionManager = transactionManager;
        this.synchronizationRegistry = synchronizationRegistry;
    }

    static class NodeKey {
        private final String nodeName;

        NodeKey(final String nodeName) {
            this.nodeName = nodeName;
        }

        public boolean equals(final Object obj) {
            return obj instanceof NodeKey && equals((NodeKey) obj);
        }

        boolean equals(final NodeKey obj) {
            return obj != null && nodeName.equals(obj.nodeName);
        }

        public int hashCode() {
            return nodeName.hashCode();
        }
    }

    protected TransactionID getAssociatedTransactionID(final EJBClientInvocationContext invocationContext) throws Exception {
        final Transaction transaction = transactionManager.getTransaction();
        if (transaction == null) {
            // no txn
            return null;
        }
        final int txStatus = transaction.getStatus();
        switch (txStatus) {
            case Status.STATUS_ACTIVE:
                // this is the only state we are interested in, for enlisting our XAResource
                break;
            default:
                // we won't be enlisting our XAResource for tx state other than ACTIVE
                return null;
        }
        final Object transactionKey = synchronizationRegistry.getTransactionKey();

        final EJBReceiver receiver = invocationContext.getReceiver();
        final String nodeName = receiver.getNodeName();

        final ResourceImpl resource = new ResourceImpl(invocationContext, transactionKey);

        XidTransactionID transactionID;
        if (transaction.enlistResource(resource)) {
            transactionID = resource.getTransactionID();
            if (transactionID != null) {
                return transactionID;
            }
            throw Logs.MAIN.txEnlistmentDidNotYieldTxId();
        }
        // another resource exists for this transaction ID
        transactionID = (XidTransactionID) synchronizationRegistry.getResource(new NodeKey(nodeName));
        if (transactionID == null) {
            throw Logs.MAIN.cannotEnlistTx();
        }
        synchronizationRegistry.registerInterposedSynchronization(new SynchronizationImpl(invocationContext, transactionID));
        return transactionID;
    }

    protected String getTransactionNode() {
        return null;
    }

    final class SynchronizationImpl implements Synchronization {
        private final EJBClientContext ejbClientContext;
        private final String nodeName;
        private final XidTransactionID transactionID;

        SynchronizationImpl(final EJBClientInvocationContext ejbClientInvocationContext, final XidTransactionID transactionID) {
            this.ejbClientContext = ejbClientInvocationContext.getClientContext();
            this.nodeName = ejbClientInvocationContext.getReceiver().getNodeName();
            this.transactionID = transactionID;
        }

        public void beforeCompletion() {
            final EJBReceiverContext receiverContext = ejbClientContext.requireNodeEJBReceiverContext(this.nodeName);
            final EJBReceiver receiver = receiverContext.getReceiver();
            receiver.beforeCompletion(receiverContext, transactionID); // block for result
        }

        public void afterCompletion(final int status) {
        }
    }


    static final class State {
        private final XidTransactionID transactionID;
        private final boolean suspended;
        private final AtomicInteger participantCnt;

        State(final XidTransactionID transactionID, final boolean suspended, final AtomicInteger cnt) {
            this.transactionID = transactionID;
            this.suspended = suspended;
            participantCnt = cnt;
        }

        State(final State old, final boolean suspended) {
            transactionID = old.transactionID;
            participantCnt = old.participantCnt;
            this.suspended = suspended;
        }
    }

    final class ResourceImpl implements XAResource {
        private final Object transactionKey;
        private final EJBClientContext ejbClientContext;
        private final String nodeName;
        @SuppressWarnings("unused")
        volatile State state;

        ResourceImpl(final EJBClientInvocationContext ejbClientInvocationContext, final Object transactionKey) {
            this.ejbClientContext = ejbClientInvocationContext.getClientContext();
            this.transactionKey = transactionKey;
            this.nodeName = ejbClientInvocationContext.getReceiver().getNodeName();
        }

        XidTransactionID getTransactionID() {
            final State state = this.state;
            return state == null ? null : state.transactionID;
        }

        public void start(final Xid xid, final int flags) throws XAException {
            if (flags == TMNOFLAGS || flags == TMJOIN) {
                // Not associated (T₀) -> Associated (T₁)
                final XidTransactionID transactionID = new XidTransactionID(xid);
                if (!stateUpdater.compareAndSet(this, null, new State(transactionID, false, new AtomicInteger()))) {
                    // XAResource is already busy on a transaction
                    // this should be impossible though since XAResource is bound to a transaction
                    throw new XAException(XAException.XAER_INVAL);
                }
            } else if (flags == TMRESUME) {
                // Suspended (T₂) -> Associated (T₁)
                State state;
                do {
                    state = this.state;
                    if (state == null || !state.suspended) {
                        throw new XAException(XAException.XAER_INVAL);
                    }
                } while (!stateUpdater.compareAndSet(this, state, state = new State(state, false)));
            } else {
                throw new XAException(XAException.XAER_INVAL);
            }
        }

        public void end(final Xid xid, final int flags) throws XAException {
            if (flags == TMSUSPEND) {
                // Associated (T₁) -> Suspended (T₂)
                State state;
                do {
                    state = this.state;
                    if (state == null || state.suspended) {
                        throw new XAException(XAException.XAER_INVAL);
                    }
                } while (!stateUpdater.compareAndSet(this, state, state = new State(state, true)));
            } else if (flags == TMFAIL || flags == TMSUCCESS) {
                // Associated (T₁) | Suspended (T₂) -> Not associated (T₀)
                if (stateUpdater.getAndSet(this, null) == null) {
                    throw new XAException(XAException.XAER_INVAL);
                }
            } else {
                throw new XAException(XAException.XAER_INVAL);
            }
        }

        public int prepare(final Xid xid) throws XAException {
            final XidTransactionID transactionID = new XidTransactionID(xid);
            final EJBReceiverContext receiverContext = ejbClientContext.requireNodeEJBReceiverContext(this.nodeName);
            final EJBReceiver receiver = receiverContext.getReceiver();
            return receiver.sendPrepare(receiverContext, transactionID);
        }

        public void commit(final Xid xid, final boolean onePhase) throws XAException {
            final XidTransactionID transactionID = new XidTransactionID(xid);
            final EJBReceiverContext receiverContext = ejbClientContext.requireNodeEJBReceiverContext(this.nodeName);
            final EJBReceiver receiver = receiverContext.getReceiver();
            receiver.sendCommit(receiverContext, transactionID, onePhase);
        }

        public void forget(final Xid xid) throws XAException {
            final XidTransactionID transactionID = new XidTransactionID(xid);
            final EJBReceiverContext receiverContext = ejbClientContext.requireNodeEJBReceiverContext(this.nodeName);
            final EJBReceiver receiver = receiverContext.getReceiver();
            receiver.sendForget(receiverContext, transactionID);
        }

        public void rollback(final Xid xid) throws XAException {
            final XidTransactionID transactionID = new XidTransactionID(xid);
            final EJBReceiverContext receiverContext = ejbClientContext.requireNodeEJBReceiverContext(this.nodeName);
            final EJBReceiver receiver = receiverContext.getReceiver();
            receiver.sendRollback(receiverContext, transactionID);
        }

        public boolean isSameRM(final XAResource resource) throws XAException {
            return resource instanceof ResourceImpl && isSameRM((ResourceImpl) resource);
        }

        boolean isSameRM(final ResourceImpl resource) throws XAException {
            return resource != null && transactionKey == resource.transactionKey && nodeName.equals(resource.nodeName);
        }

        public boolean setTransactionTimeout(final int seconds) throws XAException {
            return false;
        }

        public int getTransactionTimeout() throws XAException {
            return 0;
        }

        public Xid[] recover(final int flags) throws XAException {
            return new Xid[0];
        }
    }
}
