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

package org.jboss.ejb.client.remoting;

import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.TransactionID;

/**
 * The receiver-specific interceptor for transaction propagation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemotingTransactionInterceptor implements RemotingEJBClientInterceptor {

    public void handleInvocation(final EJBClientInvocationContext<? extends RemotingAttachments> context) throws Exception {
        final TransactionID transactionID = context.getAttachment(TransactionID.TRANSACTION_ID_KEY);
        if (transactionID != null) {
            context.getReceiverSpecific().putPayloadAttachment(0x0001, transactionID.getEncodedForm());
        }
        context.sendRequest();
    }

    public Object handleInvocationResult(final EJBClientInvocationContext<? extends RemotingAttachments> context) throws Exception {
        // nothing to do with result
        return context.getResult();
    }
}
