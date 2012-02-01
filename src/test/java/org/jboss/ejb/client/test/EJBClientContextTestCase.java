/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client.test;

import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.test.common.DummyEJBReceiver;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the basic APIs exposed by the {@link EJBClientContext}
 *
 * @author Jaikiran Pai
 */
public class EJBClientContextTestCase {

    /**
     * Tests that registering the same {@link org.jboss.ejb.client.EJBReceiver} in a {@link EJBClientContext}
     * results in a no-op, the second time
     */
    @Test
    public void testSameReceiverRegistrationMultipleTimes() {
        final EJBClientContext clientContext = EJBClientContext.create();
        final DummyEJBReceiver dummyEJBReceiver = new DummyEJBReceiver("foo");

        // register the receiver
        final boolean firstRegistration = clientContext.registerEJBReceiver(dummyEJBReceiver);
        // should be registered
        Assert.assertTrue("EJB receiver wasn't registered in client context", firstRegistration);

        // try registering the same receiver again (should return false)
        final boolean secondRegistration = clientContext.registerEJBReceiver(dummyEJBReceiver);
        Assert.assertFalse("Same EJB receiver shouldn't have been registered in the client context", secondRegistration);

    }

    /**
     * Tests that if multiple {@link org.jboss.ejb.client.EJBReceiver}s with the same node name are added to a {@link EJBClientContext}
     * then only the first one is registered and the subsequent registrations are no-op
     */
    @Test
    public void testDuplicateNodeNameReceiverRegistration() {
        final EJBClientContext clientContext = EJBClientContext.create();
        final String nodeName = "same-node-name";
        final DummyEJBReceiver firstReceiver = new DummyEJBReceiver(nodeName);

        // register
        final boolean firstRegistered = clientContext.registerEJBReceiver(firstReceiver);
        Assert.assertTrue("EJB receiver wasn't registered in the client context", firstRegistered);

        final DummyEJBReceiver secondReceiver = new DummyEJBReceiver(nodeName);
        // try registering
        final boolean secondRegistered = clientContext.registerEJBReceiver(secondReceiver);
        Assert.assertFalse("An EJB receiver with a duplicated node name shouldn't have been registered in the client context", secondRegistered);

    }
}
