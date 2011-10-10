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

package org.jboss.ejb.client.test.client;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.Locator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.test.common.AnonymousCallbackHandler;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.jboss.ejb.client.remoting.IoFutureHelper.get;

/**
 * User: jpai
 */
public class EJBClientAPIUsageTestCase {

    private static DummyServer server;

    private static Connection connection;
    
    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        server = new DummyServer("localhost", 6999);
        server.start();
        server.register("my-app", "my-module", null, EchoBean.class.getSimpleName(), new EchoBean());

        final Endpoint endpoint = Remoting.createEndpoint("endpoint", Executors.newSingleThreadExecutor(), OptionMap.EMPTY);
        final Xnio xnio = Xnio.getInstance();
        final Registration registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(xnio), OptionMap.create(Options.SSL_ENABLED, false));


        // open a connection
        final IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://localhost:6999"), OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE), new AnonymousCallbackHandler());
        connection = get(futureConnection, 5, TimeUnit.SECONDS);
    }

    @Test
    public void testProxyGeneration() throws Exception {
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, "my-app", "my-module", EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
    }

    @Test
    public void testProxyInvocation() throws Exception {
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, "my-app", "my-module", EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        final String message = "Yet another Hello World!!!";
        EJBClientContext ejbClientContext = EJBClientContext.create();
        try {
            ejbClientContext.registerConnection(connection);
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", message, echo);
        } finally {
            EJBClientContext.suspendCurrent();
        }
    }
}
