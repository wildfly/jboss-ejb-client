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

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

/**
 * The transaction context for an EJB client.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class EJBClientTransactionContext extends Attachable {

    /**
     * Get the transaction ID to associate with the invocation.  The transaction ID typically comes from the current
     * thread's transaction context.
     *
     * @param invocationContext the invocation context
     * @return the transaction ID to associate, or {@code null} for none
     * @throws Exception if an exception occurs
     */
    protected abstract TransactionID getAssociatedTransactionID(EJBClientInvocationContext invocationContext) throws Exception;

    /**
     * Get the node to which this transaction is pinned, if any.
     *
     * @return the node name or {@code null} if the transaction is not pinned
     */
    protected abstract String getTransactionNode();

    private static volatile ContextSelector<EJBClientTransactionContext> SELECTOR = new ConstantContextSelector<EJBClientTransactionContext>(createLocal());

    private static final RuntimePermission SET_SELECTOR_PERMISSION = new RuntimePermission("setClientTransactionContextSelector");

    /**
     * Set the client transaction context selector.
     *
     * @param selector the selector to set
     * @throws SecurityException if a security manager is installed and you do not have the {@code setClientTransactionContextSelector} {@link RuntimePermission}
     */
    public static void setSelector(final ContextSelector<EJBClientTransactionContext> selector) throws SecurityException {
        if (selector == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB client transaction context selector");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_SELECTOR_PERMISSION);
        }
        EJBClientTransactionContext.SELECTOR = selector;
    }

    /**
     * Set the singleton, global transaction context.  Replaces any selector which was set via {@link #setSelector(ContextSelector)}.
     *
     * @param context the context to set
     * @throws SecurityException if a security manager is installed and you do not have the {@code setClientTransactionContextSelector} {@link RuntimePermission}
     */
    public static void setGlobalContext(final EJBClientTransactionContext context) throws SecurityException {
        if (context == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB client transaction context");
        }
        setSelector(new ConstantContextSelector<EJBClientTransactionContext>(context));
    }

    /**
     * Get the current client transaction context, throwing an exception if one is not available.
     *
     * @return the transaction context
     * @throws IllegalStateException if no context is available
     */
    public static EJBClientTransactionContext requireCurrent() throws IllegalStateException {
        final EJBClientTransactionContext current = getCurrent();
        if (current == null) {
            throw Logs.MAIN.noTxContextAvailable();
        }
        return current;
    }

    /**
     * Get the current client transaction context.
     *
     * @return the current client transaction context
     */
    public static EJBClientTransactionContext getCurrent() {
        return SELECTOR.getCurrent();
    }

    /**
     * Create a local client transaction context which is controlled directly via {@link UserTransaction} methods.
     *
     * @return the local transaction context
     */
    public static EJBClientTransactionContext createLocal() {
        return EJBClientUserTransactionContext.INSTANCE;
    }

    /**
     * Create a transaction context which is controlled by an actual transaction manager.
     *
     * @param transactionManager      the transaction manager
     * @param synchronizationRegistry the transaction synchronization registry
     * @return the transaction context
     */
    public static EJBClientTransactionContext create(TransactionManager transactionManager, TransactionSynchronizationRegistry synchronizationRegistry) {
        return new EJBClientManagedTransactionContext(transactionManager, synchronizationRegistry);
    }

    /**
     * Get a {@link javax.transaction.UserTransaction} instance affiliated with a specific remote node to control the transaction
     * state.  The instance is only usable while there is an active connection with the given peer.
     *
     * @param nodeName the remote node name
     * @return the user transaction instance
     */
    protected UserTransaction getUserTransaction(String nodeName) {
        throw Logs.MAIN.userTxNotSupportedByTxContext();
    }
}
