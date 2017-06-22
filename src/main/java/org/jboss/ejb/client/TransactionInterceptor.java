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
@ClientInterceptorPriority(TransactionInterceptor.PRIORITY)
public final class TransactionInterceptor implements EJBClientInterceptor {
    private static final ContextTransactionManager transactionManager = ContextTransactionManager.getInstance();

    /**
     * This interceptor's priority.
     */
    public static final int PRIORITY = ClientInterceptorPriority.JBOSS_BEFORE;

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
