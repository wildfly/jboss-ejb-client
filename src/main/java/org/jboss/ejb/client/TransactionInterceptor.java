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

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.Xid;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientTransactionPolicy;
import org.wildfly.common.Assert;
import org.wildfly.transaction.client.RemoteTransactionContext;

/**
 * The client interceptor which associates the current transaction with the invocation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class TransactionInterceptor implements EJBClientInterceptor {
    private static final RemoteTransactionContext TRANSACTION_SYSTEM = doPrivileged((PrivilegedAction<RemoteTransactionContext>) RemoteTransactionContext::getInstance);

    private final Supplier<TransactionManager> transactionManagerSupplier;
    private final Function<Transaction, Xid> xidMapper;

    public TransactionInterceptor(final Supplier<TransactionManager> transactionManagerSupplier, final Function<Transaction, Xid> xidMapper) {
        Assert.checkNotNullParam("transactionManagerSupplier", transactionManagerSupplier);
        Assert.checkNotNullParam("xidMapper", xidMapper);
        this.transactionManagerSupplier = transactionManagerSupplier;
        this.xidMapper = xidMapper;
    }

    public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
        ClientTransactionPolicy transactionPolicy = context.getTransactionPolicy();
        if (transactionPolicy == null) {
            // TODO: per-thread and/or global default settings
            transactionPolicy = ClientTransactionPolicy.SUPPORTS;
        }
        final TransactionManager transactionManager = transactionManagerSupplier.get();
        final Transaction transaction = safeGetTransaction(transactionManager);

        if (transactionPolicy.failIfTransactionAbsent()) {
            if (transaction == null) {
                throw Logs.TXN.txNotActiveForThread();
            }
        }
        if (transactionPolicy.failIfTransactionPresent()) {
            if (transaction != null) {
                throw Logs.TXN.txAlreadyAssociatedWithThread();
            }
        }
        if (transactionPolicy.propagate()) {
            context.setTransaction(transaction);
        }
        context.sendRequest();
    }

    private static Transaction safeGetTransaction(final TransactionManager transactionManager) {
        try {
            return transactionManager == null ? null : transactionManager.getTransaction();
        } catch (SystemException e) {
            return null;
        }
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        return context.getResult();
    }
}
