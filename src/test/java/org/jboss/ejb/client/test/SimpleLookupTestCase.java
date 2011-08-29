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

package org.jboss.ejb.client.test;

import org.jboss.ejb.client.EJBClient;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;

/**
 * User: jpai
 */
public class SimpleLookupTestCase {

    @Ignore("The test should not rely on the remote endpoint being there.")
    @Test
    public void testJndiLookup() throws Exception {
        final Properties props = new Properties();
        props.put(EJBClient.EJB_REMOTE_PROVIDER_URI, "remote://localhost:9999");
        props.put(Context.INITIAL_CONTEXT_FACTORY, org.jboss.ejb.client.naming.InitialContextFactory.class.getName());

        final Context ctx = new InitialContext(props);
        final SimpleRemoteBusinessInterface proxy = (SimpleRemoteBusinessInterface) ctx.lookup("java:global/app/module/bean!" + SimpleRemoteBusinessInterface.class.getName());
        try {
            proxy.doNothing();
            // temporary till we implement the remoting
            Assert.fail("Remoting was expected to be non-functional");
        } catch (UnsupportedOperationException uoe) {
            // expected for now!
        }

    }

    private interface SimpleRemoteBusinessInterface {

        void doNothing();
    }
}
