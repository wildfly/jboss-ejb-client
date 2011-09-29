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
     * Get the transaction ID to associate with the invocation.
     *
     * @param invocationContext the invocation context
     * @return the transaction ID to associate, or {@code null} for none
     */
    protected abstract TransactionID associate(EJBClientInvocationContext<?> invocationContext);

    /**
     * Create a local client transaction context which is controlled directly via {@link UserTransaction} methods.
     *
     * @return the local transaction context
     */
    public static EJBClientTransactionContext createLocal() {
        return null;
    }

    /**
     * Create a transaction context which is controlled by an actual transaction manager.
     *
     * @param transactionManager the transaction manager
     * @param synchronizationRegistry the transaction synchronization registry
     * @return the transaction context
     */
    public static EJBClientTransactionContext create(TransactionManager transactionManager, TransactionSynchronizationRegistry synchronizationRegistry) {
        return null;
    }
}
