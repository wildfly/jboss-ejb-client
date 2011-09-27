/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client.test.stateless;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.naming.EJBObjectFactory;
import org.jboss.ejb.client.naming.ServerObjectFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.spi.NamingManager;
import java.net.URI;
import java.util.Hashtable;

import static org.junit.Assert.assertEquals;

/**
 * Call a 'SLSB' via direct EJB remote API.
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class StatelessTestCase {
    private static DummyServer server;

    @AfterClass
    public static void afterClass() {
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        final InitialContext ctx = new InitialContext();
        final URI uri = new URI("remote://localhost:6999");
        final Reference refInfo = EJBObjectFactory.createReference(uri, "my-app", "my-module", "GreeterBean", GreeterRemote.class);
        ctx.bind("test", refInfo);
        ctx.createSubcontext("x");
        ctx.bind("x/server1", ServerObjectFactory.createReference(uri));
        ctx.bind("ejb", ServerObjectFactory.createReference());

        server = new DummyServer();
        server.start();
        server.register("my-app/my-module/GreeterBean", new GreeterBean());
    }

    @Test
    public void testGreeting() throws Exception {
        GreeterRemote remote = EJBClient.getProxy("my-app", "my-module", "dn", GreeterRemote.class, "GreeterBean");
        String result = remote.greet("test");
        assertEquals("Hi test", result);
    }

    @Test
    public void testLookup() throws Exception {
        final Context ctx = new InitialContext();
        final GreeterRemote remote = (GreeterRemote) ctx.lookup("test");
        String result = remote.greet("lookup");
        assertEquals("Hi lookup", result);
    }

    @Test
    public void testLookupEJB() throws Exception {
        final Context ctx = new InitialContext();
        final GreeterRemote remote = (GreeterRemote) ctx.lookup("ejb/my-app/my-module/GreeterBean#" + GreeterRemote.class.getName());
        String result = remote.greet("lookupEJB");
        assertEquals("Hi lookupEJB", result);
    }

    @Test
    public void testLookupServer1() throws Exception {
        final Context ctx = new InitialContext();
        final GreeterRemote remote = (GreeterRemote) ctx.lookup("x/server1/my-app/my-module/GreeterBean#" + GreeterRemote.class.getName());
        String result = remote.greet("lookupServer1");
        assertEquals("Hi lookupServer1", result);
    }

    @Test
    public void testObjectFactory() throws Exception {
        final URI uri = new URI("remote://localhost:6999");
        final Reference refInfo = EJBObjectFactory.createReference(uri, "my-app", "my-module", "GreeterBean", GreeterRemote.class);
        final Name name = new CompositeName("test");
        final Context nameCtx = new InitialContext();
        final Hashtable<?, ?> environment = new Hashtable<Object, Object>();
        final GreeterRemote remote = (GreeterRemote) NamingManager.getObjectInstance(refInfo, name, nameCtx, environment);
        String result = remote.greet("objectfactory");
        assertEquals("Hi objectfactory", result);
    }
}
