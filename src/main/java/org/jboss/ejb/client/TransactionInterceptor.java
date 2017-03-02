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

import javax.transaction.Transaction;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientInterceptorPriority;
import org.jboss.ejb.client.annotation.ClientTransactionPolicy;
import org.wildfly.transaction.client.ContextTransactionManager;

/**
 * The client interceptor which associates the current transaction with the invocation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@ClientInterceptorPriority(ClientInterceptorPriority.JBOSS_BEFORE + 1)
public final class TransactionInterceptor implements EJBClientInterceptor {
    private static final ContextTransactionManager transactionManager = ContextTransactionManager.getInstance();

    /**
     * Construct a new instance.
     */
    public TransactionInterceptor() {
    }

    public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
        final ClientTransactionPolicy transactionPolicy = context.getTransactionPolicy();
        final Transaction transaction = transactionManager.getTransaction();

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
        if (transaction != null) {
            transactionManager.suspend();
            try {
                context.sendRequest();
            } finally {
                transactionManager.resume(transaction);
            }
        } else {
            context.sendRequest();
        }
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        return context.getResult();
    }
}
