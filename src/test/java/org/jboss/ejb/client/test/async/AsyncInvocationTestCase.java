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

package org.jboss.ejb.client.test.async;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Future;

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.PropertiesBasedEJBClientConfiguration;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Jaikiran Pai
 */
public class AsyncInvocationTestCase {

    private static final Logger logger = Logger.getLogger(AsyncInvocationTestCase.class);

    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private DummyServer server;
    private ContextSelector<EJBClientContext> previousSelector;

    @Before
    public void beforeTest() throws IOException {
        server = new DummyServer("localhost", 7999);
        server.start();

        final SlowEcho asyncBean = new AsyncBean();
        final ExceptionThrowingBean exceptionThrowingBean = new ExceptionThrowingBean();

        // deploy on server
        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, AsyncBean.class.getSimpleName(), asyncBean);
        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, ExceptionThrowingBean.class.getSimpleName(), exceptionThrowingBean);

        // setup EJB client context
        final String clientPropsName = "async-invocation-jboss-ejb-client.properties";
        final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(clientPropsName);
        if (inputStream == null) {
            throw new RuntimeException("Could not find EJB client configuration properties at " + clientPropsName + " using classloader " + this.getClass().getClassLoader());
        }
        final Properties clientProps = new Properties();
        clientProps.load(inputStream);
        final EJBClientConfiguration clientConfiguration = new PropertiesBasedEJBClientConfiguration(clientProps);
        final ConfigBasedEJBClientContextSelector selector = new ConfigBasedEJBClientContextSelector(clientConfiguration);
        this.previousSelector = EJBClientContext.setSelector(selector);
    }

    @After
    public void afterTest() throws IOException {
        try {
            this.server.stop();
        } catch (Exception e) {
            logger.info("Could not stop server", e);
        }
        if (this.previousSelector != null) {
            EJBClientContext.setSelector(previousSelector);
        }
    }

    /**
     * Tests that the invocation on async methods of a bean return the correct states when the
     * returned {@link Future} is used on the client side
     *
     * @throws Exception
     */
    @Test
    public void testAsyncInvocation() throws Exception {
        // create a proxy for invocation
        final StatelessEJBLocator<SlowEcho> statelessEJBLocator = new StatelessEJBLocator<SlowEcho>(SlowEcho.class, APP_NAME, MODULE_NAME, AsyncBean.class.getSimpleName(), "");
        final SlowEcho asyncBean = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", asyncBean);

        // invoke
        final String message = "Hola!";
        final Future<String> futureResult = asyncBean.twoSecondEcho(message);
        Assert.assertFalse("Unexpected return for isDone()", futureResult.isDone());
        Assert.assertFalse("Unexpected return for isCancelled()", futureResult.isCancelled());
        // cancel the invocation
        final boolean wasCancelled = futureResult.cancel(true);
        // subsequent invocations to isDone() will *always* return true after a call to cancel()
        Assert.assertTrue("Unexpected return for isDone() after a call to cancel", futureResult.isDone());
        // subsequent invocations to isCancelled() will return true *if* cancel() returned true
        if (wasCancelled) {
            Assert.assertTrue("Unexpected return for isCancelled() after a call to cancel() returned true", futureResult.isCancelled());
        }

    }

    /**
     * Tests that the {@link EJBClient#asynchronous(Object)} method returns a proxy which treats invocations on the proxy to be asynchronous.
     */
    @Test
    public void testAsyncProxy() throws Exception {
        // create a simple proxy for invocation
        final StatelessEJBLocator<ExceptionThrower> statelessEJBLocator = new StatelessEJBLocator<ExceptionThrower>(ExceptionThrower.class, APP_NAME, MODULE_NAME, ExceptionThrowingBean.class.getSimpleName(), "");
        final ExceptionThrower normalProxy = EJBClient.createProxy(statelessEJBLocator);
        // now create an async proxy out of it
        final ExceptionThrower asyncProxy = EJBClient.asynchronous(normalProxy);
        logger.info("Invoking on async proxy");
        // fire. this invocation should return immediately without any exception since it's a async proxy and the client as a result will not wait for the (exception) response from the server
        asyncProxy.justThrowBackSomeException();
    }
}
