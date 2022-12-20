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

import java.net.InetAddress;
import java.net.ServerSocket;

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
        System.setProperty("org.jboss.ejb.client.discovery.timeout", "10");

        // This test will fail if org.jboss.ejb.client.discovery.additional-node-timeout is not set
        // assertInvocationTimeLessThan checks that the org.jboss.ejb.client.discovery.additional-node-timeout is effective
        // if org.jboss.ejb.client.discovery.additional-node-timeout is not effective it will timeout once it reaches the value of org.jboss.ejb.client.discovery.timeout
        System.setProperty("org.jboss.ejb.client.discovery.additional-node-timeout", "200");
    }

    @AfterClass
    public static void afterClass() {
        System.clearProperty("org.jboss.ejb.client.discovery.timeout");
        System.clearProperty("org.jboss.ejb.client.discovery.additional-node-timeout");
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
     * This method tests the use case when affinity is None.
     * If the test method timed out, then the additional-node-timeout system property didn't take effect.
     */
    @Test(timeout = 4000)
    public void testDiscoveryTimeoutWithoutAffinity() throws Exception {
        logger.info("Starting testDiscoveryTimeoutWithoutAffinity");
        testDiscoveryTimeout(Affinity.NONE);
    }

    /**
     * Tests that node discovery times out after additional-node-timeout, after the first node has been discovered.
     * This method tests the use case when affinity is ClusterAffinity.
     * If the test method timed out, then additional-node-timeout system property didn't take effect.
     */
    @Test(timeout = 4000)
    public void testDiscoveryTimeoutWithClusterAffinity() throws Exception {
        logger.info("Starting testDiscoveryTimeoutWithClusterAffinity");
        testDiscoveryTimeout(new ClusterAffinity(CLUSTER_NAME));
    }

    private void testDiscoveryTimeout(final Affinity affinity) throws Exception {
        final StatelessEJBLocator<Echo> locator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, affinity);

        // verify that client invocation works when both nodes are responsive
        verifyClient(locator);

        // stop second node and open a socket at the same port to simulate an unresponsive node
        stopServer(1);
        try (ServerSocket s = new ServerSocket(7099, 100, InetAddress.getByName("localhost"))) {
            verifyClient(locator);
        }
    }

    /**
     * Creates the proxy to invoke the target ejb, and verifies that the
     * invocation returns promptly (less than the maximum time).
     * @param locator ejb locator for creating proxy
     */
    private static void verifyClient(EJBLocator<Echo> locator) {
        final Echo proxy = EJBClient.createProxy(locator);
        Assert.assertNotNull("Received a null proxy", proxy);

        logger.info("Invoking on proxy " + proxy);
        // Invoke on the proxy. This should fail in 10 seconds or else it'll hang.
        final String message = "hello!";

        long invocationStart = System.currentTimeMillis();
        Result<String> result = proxy.echo(message);
        final long invocationTime = System.currentTimeMillis() - invocationStart;

        assertInvocationTimeLessThan(invocationTime, result);
        Assert.assertEquals(message, result.getValue());
    }

    private static void assertInvocationTimeLessThan(long invocationTime, Result<String> result) {
        final long maximumInvocationTimeMs = 3000;
        logger.infof("Invocation returned from %s in %d ms", result.getNode(), invocationTime);
        if (invocationTime > maximumInvocationTimeMs) {
            Assert.fail(String.format("org.jboss.ejb.client.discovery.additional-node-timeout ineffective: invocation time: %d > maximum expected invocation time: %d",
                    invocationTime, maximumInvocationTimeMs));
        }
    }
}
