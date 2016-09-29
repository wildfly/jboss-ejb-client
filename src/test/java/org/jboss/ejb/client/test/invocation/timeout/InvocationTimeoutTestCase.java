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

package org.jboss.ejb.client.test.invocation.timeout;

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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import javax.ejb.EJBException;

/**
 * Tests that if a invocation takes longer than the configured invocation timeout, then the
 * client receives a timeout exception
 *
 * @author Jaikiran Pai
 * @see https://issues.jboss.org/browse/EJBCLIENT-33
 */
public class InvocationTimeoutTestCase {

    private static final Logger logger = Logger.getLogger(InvocationTimeoutTestCase.class);

    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private DummyServer server;
    private ContextSelector<EJBClientContext> previousSelector;

    @Before
    public void beforeTest() throws IOException {
        // TODO: Come back to this once we enable a way to test this project against IPv6
        server = new DummyServer("localhost", 6999);
        server.start();

        final LazyMan slowBean = new SlowBean();

        // deploy on server
        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, SlowBean.class.getSimpleName(), slowBean);

        // setup EJB client context
        final String clientPropsName = "invocation-timeout-jboss-ejb-client.properties";
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
     * Invokes a method which takes longer than the configured invocation timeout. The client is expected
     * to a receive a timeout exception
     *
     * @throws Exception
     */
    @Test
    public void testInvocationTimeout() throws Exception {
        // create a proxy for invocation
        final StatelessEJBLocator<LazyMan> statelessEJBLocator = new StatelessEJBLocator<LazyMan>(LazyMan.class, APP_NAME, MODULE_NAME, SlowBean.class.getSimpleName(), "");
        final LazyMan slowBean = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", slowBean);

        // invoke
        try {
            slowBean.doThreeSecondWork();
            Assert.fail("Expected to receive a timeout for the invocation");
        } catch (Exception e) {
            if (e instanceof EJBException && e.getCause() instanceof TimeoutException) {
                logger.info("Got the expected timeout exception", e.getCause());
                return;
            }
            throw e;
        }
    }

}
