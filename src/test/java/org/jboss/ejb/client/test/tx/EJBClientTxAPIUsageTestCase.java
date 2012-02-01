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

package org.jboss.ejb.client.test.tx;

import javax.transaction.UserTransaction;

import org.jboss.ejb.client.ConstantContextSelector;
import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBClientTransactionContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.test.common.DummyEJBReceiver;
import org.junit.Test;

/**
 * @author Jaikiran Pai
 */
public class EJBClientTxAPIUsageTestCase {

    @Test
    public void testUserTransactionBegin() throws Exception {
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            final EJBClientTransactionContext ejbClientTransactionContext = EJBClientTransactionContext.createLocal();
            final EJBReceiver dummyReceiver = new DummyEJBReceiver("dummynodename");
            ejbClientContext.registerEJBReceiver(dummyReceiver);
            final UserTransaction userTransaction = EJBClient.getUserTransaction("dummynodename");
            userTransaction.begin();
        } finally {
            EJBClientTransactionContext.setSelector(new ConstantContextSelector<EJBClientTransactionContext>(null));
            EJBClientContext.setSelector(oldClientContextSelector);
        }
    }

}
