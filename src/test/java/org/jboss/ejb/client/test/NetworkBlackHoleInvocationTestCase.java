/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client.test;

import static org.jboss.ejb._private.SystemProperties.DISCOVERY_ADDITIONAL_NODE_TIMEOUT;
import static org.jboss.ejb._private.SystemProperties.DISCOVERY_TIMEOUT;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.Result;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * Tests discovery timeout configuration.
 *
 * @author <a href="ingo@redhat.com">Ingo Weiss</a>
 * @author <a href="thofman@redhat.com">Tomas Hofman</a>
 */
public class NetworkBlackHoleInvocationTestCase extends AbstractEJBClientTestCase {

    private static final Logger logger = Logger.getLogger(NetworkBlackHoleInvocationTestCase.class);
    private static final String PROPERTIES_FILE = "broken-server-jboss-ejb-client.properties";

    @BeforeClass
    public static void beforeClass() throws Exception {
        // trigger the static init of the correct properties file - this also depends on running in forkMode=always
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(SimpleInvocationTestCase.class.getClassLoader(), PROPERTIES_FILE);
        JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);

        // set discovery timeouts

        // broken-server-jboss-ejb-client.properties will have the ejb-client with 2 nodes on ports 6999 and 7099
        // it will succesfully invoke the ejb and then it will kill the 7099 port and try to invoke again
        // the expected behavior is that it will not wait more than org.jboss.ejb.client.discovery.additional-node-timeout once it has a connection to 6999 before invoking the ejb
        System.setProperty(DISCOVERY_TIMEOUT, "10");

        // This test will fail if org.jboss.ejb.client.discovery.additional-node-timeout is not set
        // assertInvocationTimeLessThan checks that the org.jboss.ejb.client.discovery.additional-node-timeout is effective
        // if org.jboss.ejb.client.discovery.additional-node-timeout is not effective it will timeout once it reaches the value of org.jboss.ejb.client.discovery.timeout
        System.setProperty(DISCOVERY_ADDITIONAL_NODE_TIMEOUT, "200");
    }

    @AfterClass
    public static void afterClass() {
        System.clearProperty(DISCOVERY_TIMEOUT);
        System.clearProperty(DISCOVERY_ADDITIONAL_NODE_TIMEOUT);
    }

    @Before
    public void beforeTest() throws Exception {
        startServer(0, 6999);
        startServer(1, 7099);

        deployStateless(0);
        deployStateless(1);

        // define clusters
        defineCluster(0, CLUSTER);
        defineCluster(1, CLUSTER);
    }

    @After
    public void afterTest() {
        // wipe cluster
        removeCluster(0, CLUSTER_NAME);
        removeCluster(1, CLUSTER_NAME);

        undeployStateless(0);
        undeployStateless(1);

        stopServer(0);
        stopServer(1);
    }

    /**
     * Tests that node discovery times out after additional-node-timeout, after the first node has been discovered.
     *
     * This method tests the use case when affinity is None.
     *
     * If the test method timed out, then the additional-node-timeout system property didn't take effect.
     */
    @Test(timeout = 4000)
    public void testDiscoveryTimeoutWithoutAffinity() throws Exception {
        final StatelessEJBLocator<Echo> locator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER,
                Affinity.NONE);

        // verify that client invocation works when both nodes are responsive
        verifyClient(locator);

        // stop second node and open a socket at the same port to simulate unresponsive node
        stopServer(1);
        try (ServerSocket s = new ServerSocket(7099, 100, InetAddress.getByName("localhost"))) {
            verifyClient(locator);
        }
    }

    /**
     * Tests that node discovery times out after additional-node-timeout, after the first node has been discovered.
     *
     * This method tests the use case when affinity is ClusterAffinity.
     *
     * If the test method timed out, then additional-node-timeout system property didn't take effect.
     */
    @Test(timeout = 4000)
    public void testDiscoveryTimeoutWithClusterAffinity() throws Exception {
        final StatelessEJBLocator<Echo> locator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER,
                new ClusterAffinity(CLUSTER_NAME));

        // verify that client invocation works when both nodes are responsive
        verifyClient(locator);

        // stop second node and open a socket at the same port to simulate an unresponsive node
        stopServer(1);
        try (ServerSocket s = new ServerSocket(7099, 100, InetAddress.getByName("localhost"))) {
            verifyClient(locator);
        }
    }

    private static void verifyClient(EJBLocator<Echo> locator) {
        final Echo proxy = EJBClient.createProxy(locator);
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        logger.info("Invoking on proxy...");
        // Invoke on the proxy. This should fail in 10 seconds or else it'll hang.
        final String message = "hello!";

        long invocationStart = System.currentTimeMillis();
        Result<String> echo = proxy.echo(message);
        assertInvocationTimeLessThan("org.jboss.ejb.client.discovery.additional-node-timeout ineffective", 3000, invocationStart);
        Assert.assertEquals(message, echo.getValue());
    }

    private static void assertInvocationTimeLessThan(String message, long maximumInvocationTimeMs, long invocationStart) {
        long invocationTime = System.currentTimeMillis() - invocationStart;
        if (invocationTime > maximumInvocationTimeMs)
            Assert.fail(String.format("%s: invocation time: %d > maximum expected invocation time: %d", message, invocationTime, maximumInvocationTimeMs));
    }
}
