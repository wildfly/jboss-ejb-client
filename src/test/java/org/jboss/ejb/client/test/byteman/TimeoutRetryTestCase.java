/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.ejb.client.test.byteman;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ejb.NoSuchEJBException;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.ClassCallback;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that a hang does not happen if timeout happens concurrently with retry
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir = "target/test-classes")
public class TimeoutRetryTestCase {
    private static final Logger logger = Logger.getLogger(TimeoutRetryTestCase.class);
    private static final String PROPERTIES_FILE = "jboss-ejb-client.properties";

    private DummyServer server;
    private boolean serverStarted = false;

    // module
    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";
    private static final String BEAN_NAME = EchoBean.class.getName();
    private static final String SERVER_NAME = "test-server";

    /**
     * Do any general setup here
     *
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // trigger the static init of the correct properties file - this also depends on running in forkMode=always
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(TimeoutRetryTestCase.class.getClassLoader(), PROPERTIES_FILE);
        JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);

        // Launch callback if needed
        ClassCallback.beforeClassCallback();
    }

    /**
     * Do any test specific setup here
     */
    @Before
    public void beforeTest() throws Exception {
        // start a server
        server = new DummyServer("localhost", 6999, SERVER_NAME);
        server.start();
        serverStarted = true;
        logger.info("Started server ...");

        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, BEAN_NAME, new EchoBean());
        logger.infof("Registered module: %s %s %s %s", APP_NAME, MODULE_NAME, DISTINCT_NAME, BEAN_NAME);
    }

    /**
     * Test a basic invocation
     */
    @Test
    public void testInvocationWithURIAffinity() {
        final String message = "hello!";
        final URI uri;
        try {
            uri = new URI("remote", null, "localhost", 6999, null, null, null);
        } catch (URISyntaxException use) {
            throw new RuntimeException((use));
        }

        // first calling a correctly configured EchoBean proxy
        StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<>(Echo.class, APP_NAME, MODULE_NAME, BEAN_NAME, DISTINCT_NAME);
        Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(uri));
        Assert.assertEquals(message, proxy.echo(message).getValue());

        // create a proxy with wrong bean name for invocation
        statelessEJBLocator = new StatelessEJBLocator<>(Echo.class, APP_NAME, MODULE_NAME, "wrong-name", DISTINCT_NAME);
        proxy = EJBClient.createProxy(statelessEJBLocator);
        EJBClient.setInvocationTimeout(proxy, 1, TimeUnit.SECONDS);
        EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(uri));
        Assert.assertNotNull("Received a null proxy", proxy);

        logger.info("Invoking on proxy with wrong bean name " + proxy);
        NoSuchEJBException expectedException = null;
        long start = System.currentTimeMillis();
        try {
            proxy.echo(message);
            Assert.fail("Invocation expected to fail");
        } catch (NoSuchEJBException expected) {
            expectedException = expected;
        }

        //we have a 3s sleep in the retry code
        //and a 1s timeout
        //so we verify it was less than 1s
        final long invocationDuration = System.currentTimeMillis() - start;
        Assert.assertTrue("Invocation should have timed out after 1000 ms, but actual duration is " + invocationDuration, invocationDuration < 2000);
        logger.infof("Invocation correctly timed out in %s ms", invocationDuration);

        // check the expected exception type
        boolean found = false;
        final Throwable[] suppressed = expectedException.getSuppressed();
        logger.infof("Suppressed exceptions: %s", Stream.of(suppressed).map(Throwable::toString).collect(Collectors.toList()));
        for (Throwable i : suppressed) {
            // the suppressed exception may be a java.util.concurrent.TimeoutException, or
            // NoSuchEJBException that embeds a TimeoutException
            if (i instanceof TimeoutException || i instanceof NoSuchEJBException) {
                found = true;
                break;
            }
        }
        if (!found) {
            expectedException.printStackTrace();
            Assert.fail("Expected a suppressed timeout exception or NoSuchEJBException");
        }
    }

    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {
        server.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, BEAN_NAME);
        logger.infof("Unregistered module: %s %s %s %s", APP_NAME, MODULE_NAME, DISTINCT_NAME, BEAN_NAME);

        if (serverStarted) {
            try {
                this.server.stop();
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            }
        }
        logger.info("Stopped server ...");
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
    }

}
