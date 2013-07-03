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

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBClientInterceptor;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.test.common.AnonymousCallbackHandler;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.logging.Logger;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.EchoRemote;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.jboss.ejb.client.remoting.IoFutureHelper.get;

/**
 * Tests basic EJB client API usages
 *
 * @author Jaikiran Pai
 */
public class EJBClientAPIUsageTestCase {

    private static final Logger logger = Logger.getLogger(EJBClientAPIUsageTestCase.class);

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
        server.register("my-app", "my-module", "", EchoBean.class.getSimpleName(), new EchoBean());

        final Endpoint endpoint = Remoting.createEndpoint("endpoint", OptionMap.EMPTY);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));


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
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            ejbClientContext.registerConnection(connection, "remote");
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", message, echo);
        } finally {
            EJBClientContext.setSelector(oldClientContextSelector);
        }
    }

    /**
     * Tests that when a EJB client context is {@link org.jboss.ejb.client.EJBClientContext#close() closed}
     * any EJB invocations involving that context will fail
     *
     * @throws Exception
     */
    @Test
    public void testContextClose() throws Exception {
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, "my-app", "my-module", EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        final String message = "Testing EJB client context close";
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            ejbClientContext.registerConnection(connection, "remote");
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", message, echo);
            // now close the context
            ejbClientContext.close();
            // now try invoking again - should fail
            try {
                final String echoAfterClose = proxy.echo(message);
                Assert.fail("Invocation on a EJB was expected to fail after the EJB client context was closed, but it didn't");
            } catch (IllegalStateException ise) {
                // expected
                logger.debug("Received the expected exception on invoking an EJB after the EJB client context was closed", ise);
            }
        } finally {
            EJBClientContext.setSelector(oldClientContextSelector);
        }
    }

    /**
     * Test that registering and removing the EJB client interceptor(s) to a EJB client context works as expected
     *
     * @throws Exception
     */
    @Test
    public void testClientInterceptor() throws Exception {
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, "my-app", "my-module", EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        final String message = "foo-bar";
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        final EJBClientInterceptor.Registration interceptorRegistration = ejbClientContext.registerInterceptor(99999, new SimpleInterceptor());
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            ejbClientContext.registerConnection(connection, "remote");
            // we expect the interceptor to be invoked
            final String expectedEcho = SimpleInterceptor.class.getName() + " " + message;
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", expectedEcho, echo);

            // now unregister the interceptor and invoke again
            interceptorRegistration.remove();
            final String echoAfterRemovingInterceptor = proxy.echo(message);
            // this time the interceptor shouldn't have been invoked
            Assert.assertEquals("Unexpected echo message after removing EJB client intercpetor", message, echoAfterRemovingInterceptor);
        } finally {
            EJBClientContext.setSelector(oldClientContextSelector);
        }

    }

    private static final class SimpleInterceptor implements EJBClientInterceptor {

        @Override
        public void handleInvocation(EJBClientInvocationContext context) throws Exception {
            context.sendRequest();
        }

        @Override
        public Object handleInvocationResult(EJBClientInvocationContext context) throws Exception {
            final Object originalResult = context.getResult();
            if (originalResult instanceof String) {
                return this.getClass().getName() + " " + originalResult;
            }
            return originalResult;
        }
    }
}
