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

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAException;

/**
 * The transaction context for manual control of transactions on a remote node.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientUserTransactionContext extends EJBClientTransactionContext {

    static final EJBClientUserTransactionContext INSTANCE = new EJBClientUserTransactionContext();

    /**
     * User transaction objects are bound to a single source thread; we do not support suspending or resuming
     * transactions in this simple mode.  Asynchronous invocations use the value from the source thread.
     */
    private static final ThreadLocal<State> CURRENT_TRANSACTION_STATE = new ThreadLocal<State>();

    /**
     * {@inheritDoc}
     */
    protected UserTransactionID getAssociatedTransactionID(final EJBClientInvocationContext invocationContext) {
        final State state = CURRENT_TRANSACTION_STATE.get();
        return state == null ? null : state.currentId;
    }

    protected String getTransactionNode() {
        final State state = CURRENT_TRANSACTION_STATE.get();
        final UserTransactionID id = state == null ? null : state.currentId;
        return id == null ? null : id.getNodeName();
    }

    /**
     * {@inheritDoc}
     */
    protected UserTransaction getUserTransaction(String nodeName) {
        return new UserTransactionImpl(nodeName);
    }

    private static final AtomicInteger idCounter = new AtomicInteger(new Random().nextInt());

    static class State {
        UserTransactionID currentId;
        int status = Status.STATUS_NO_TRANSACTION;
        int timeout = 0;

        State() {
        }
    }

    class UserTransactionImpl implements UserTransaction {
        private final String nodeName;

        UserTransactionImpl(final String nodeName) {
            this.nodeName = nodeName;
        }

        public void begin() throws NotSupportedException, SystemException {
            State state = CURRENT_TRANSACTION_STATE.get();
            if (state == null) {
                CURRENT_TRANSACTION_STATE.set(state = new State());
            }
            if (state.currentId != null) {
                throw Logs.MAIN.txAlreadyAssociatedWithThread();
            }
            final UserTransactionID transactionID = new UserTransactionID(nodeName, idCounter.getAndAdd(127));
            state.currentId = transactionID;
            state.status = Status.STATUS_ACTIVE;
        }

        public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
            final State state = CURRENT_TRANSACTION_STATE.get();
            if (state == null || state.currentId == null) {
                throw Logs.MAIN.noTxAssociatedWithThread();
            }
            if (state.status != Status.STATUS_ACTIVE && state.status != Status.STATUS_MARKED_ROLLBACK) {
                throw Logs.MAIN.txNotActiveForThread();
            }
            final UserTransactionID transactionID = state.currentId;
            try {
                final EJBClientContext clientContext = EJBClientContext.requireCurrent();
                final EJBReceiverContext receiverContext = clientContext.requireNodeEJBReceiverContext(nodeName);
                final EJBReceiver receiver = receiverContext.getReceiver();
                if (state.status == Status.STATUS_MARKED_ROLLBACK) {
                    state.status = Status.STATUS_ROLLING_BACK;
                    try {
                        receiver.sendRollback(receiverContext, transactionID);
                    } catch (Throwable ignored) {
                        // log it maybe?
                    }
                    throw new RollbackException("Transaction marked for rollback only");
                } else {
                    state.status = Status.STATUS_COMMITTING;
                    try {
                        receiver.sendCommit(receiverContext, transactionID, true);
                    } catch (XAException e) {
                        if (e.errorCode >= XAException.XA_RBBASE && e.errorCode <= XAException.XA_RBEND) {
                            throw new RollbackException(e.getMessage());
                        }
                        if (e.errorCode == XAException.XA_HEURMIX) {
                            throw new HeuristicMixedException(e.getMessage());
                        }
                        if (e.errorCode == XAException.XA_HEURRB) {
                            throw new HeuristicRollbackException(e.getMessage());
                        }
                        throw new SystemException(e.getMessage());
                    }
                }
            } finally {
                state.currentId = null;
                state.status = Status.STATUS_NO_TRANSACTION;
            }
        }

        public void rollback() throws IllegalStateException, SecurityException, SystemException {
            final State state = CURRENT_TRANSACTION_STATE.get();
            if (state == null || state.currentId == null) {
                throw Logs.MAIN.noTxAssociatedWithThread();
            }
            if (state.status != Status.STATUS_ACTIVE && state.status != Status.STATUS_MARKED_ROLLBACK) {
                throw Logs.MAIN.txNotActiveForThread();
            }
            final UserTransactionID transactionID = state.currentId;
            try {
                final EJBClientContext clientContext = EJBClientContext.requireCurrent();
                final EJBReceiverContext receiverContext = clientContext.requireNodeEJBReceiverContext(nodeName);
                final EJBReceiver receiver = receiverContext.getReceiver();
                state.status = Status.STATUS_ROLLING_BACK;
                try {
                    receiver.sendRollback(receiverContext, transactionID);
                } catch (XAException e) {
                    throw new SystemException(e.getMessage());
                }
            } finally {
                state.currentId = null;
                state.status = Status.STATUS_NO_TRANSACTION;
            }
        }

        public void setRollbackOnly() throws IllegalStateException, SystemException {
            final State state = CURRENT_TRANSACTION_STATE.get();
            if (state != null) switch (state.status) {
                case Status.STATUS_ROLLING_BACK:
                case Status.STATUS_ROLLEDBACK:
                case Status.STATUS_MARKED_ROLLBACK: {
                    // nothing to do
                    return;
                }
                case Status.STATUS_ACTIVE: {
                    // mark rollback
                    state.status = Status.STATUS_MARKED_ROLLBACK;
                    return;
                }
            }
            throw new IllegalStateException("Transaction not active");
        }

        public int getStatus() throws SystemException {
            final State state = CURRENT_TRANSACTION_STATE.get();
            return state == null ? Status.STATUS_NO_TRANSACTION : state.status;
        }

        public void setTransactionTimeout(int seconds) throws SystemException {
            if (seconds < 0) {
                seconds = 0;
            }
            State state = CURRENT_TRANSACTION_STATE.get();
            if (state == null) {
                CURRENT_TRANSACTION_STATE.set(state = new State());
            }
            state.timeout = seconds;
        }
    }
}
