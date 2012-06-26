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

/**
 * The client interceptor which associates the current transaction ID with the invocation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class TransactionInterceptor implements EJBClientInterceptor {

    public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
        final EJBClientTransactionContext current = EJBClientTransactionContext.getCurrent();
        // A EJB client tx context allows to set selectors and there's no guarantee that the
        // selector returns a non-null context. So be safe!
        if (current != null) {
            final TransactionID transactionID = current.getAssociatedTransactionID(context);
            if (transactionID != null) {
                context.putAttachment(AttachmentKeys.TRANSACTION_ID_KEY, transactionID);
            }
        }
        context.sendRequest();
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        return context.getResult();
    }
}
