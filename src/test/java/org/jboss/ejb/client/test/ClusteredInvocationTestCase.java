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
package org.jboss.ejb.client.test;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientCluster;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.EJBModuleIdentifier;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.Result;
import org.jboss.ejb.client.test.common.StatefulEchoBean;
import org.jboss.ejb.client.test.common.StatelessEchoBean;
import org.jboss.ejb.server.ClusterTopologyListener.ClusterInfo;
import org.jboss.ejb.server.ClusterTopologyListener.NodeInfo;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

/**
 * Tests basic features of proxies for invocation of a bean deployed on clustered server nodes.
 *
 * The server environment consists of two clustered nodes and one singleton node:
 * on node1: bean Echo is deployed, clustered
 * on node2: bean Echo is deployed, clustered
 * on node3: bean Echo is deployed, non-clustered
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class ClusteredInvocationTestCase extends AbstractEJBClientTestCase{

    private static final Logger logger = Logger.getLogger(ClusteredInvocationTestCase.class);
    private static final String PROPERTIES_FILE = "clustered-jboss-ejb-client.properties";

    /**
     * Do any general setup here
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // trigger the static init of the correct properties file - this also depends on running in forkMode=always
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(SimpleInvocationTestCase.class.getClassLoader(), PROPERTIES_FILE);
        JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);

        // Launch callback if needed
        ClassCallback.beforeClassCallback();
    }

    /**
     * Do any test specific setup here
     */
    @Before
    public void beforeTest() throws Exception {

        // start servers
        for (int i = 0; i < 3; i++) {
            startServer(i, 6999 + (i * 100));
            deployStateful(i);
            deployStateless(i);
        }

        // define clusters
        defineCluster(0, CLUSTER);
        defineCluster(1, CLUSTER);
        // don't add node3 to the cluster; it's a singleton node
    }

    /**
     * Test number of configured connections in EJBClientContext.
     */
    @Test
    public void testConfiguredConnections() {
        logger.info("=== Testing configured connections ===");

        EJBClientContext context = EJBClientContext.getCurrent();
        List<EJBClientConnection> connections = context.getConfiguredConnections();

        Assert.assertEquals("Number of configured connections for this context is incorrect", 3, connections.size());
        for (EJBClientConnection connection : connections) {
            logger.info("found connection: destination = " + connection.getDestination() + ", forDiscovery = " + connection.isForDiscovery());
        }

        Collection<EJBClientCluster> clusters = context.getInitialConfiguredClusters();
        for (EJBClientCluster cluster: clusters) {
            logger.info("found cluster: name = " + cluster.getName());
        }
    }

    /**
     * Test SFSB default proxy initialization (legacy behaviour)
     *
     * scenario:
     *   invoked bean available on both nodes of the cluster "ejb" = {node1, node2}, not available on non-clustered node3
     *   accept default affinities (simulated here by setting strong affinity = NONE)
     * expected result:
     *   SFSB session is created on node 'node' of the cluster, strong affinity = ClusterAffinity("ejb"), weakAffinity = NodeAffinity(node)
     */
    @Test
    public void testSFSBDefaultProxyInitialization() {
        logger.info("=== Testing default proxy initialization for SFSB proxy with ClusterAffinity ===");

        Affinity expectedStrongAffinity = new ClusterAffinity("ejb");
        Affinity expectedWeakAffinity1 = new NodeAffinity(serverNames[0]);
        Affinity expectedWeakAffinity2 = new NodeAffinity(serverNames[1]);

        // remove the singleton from the environment
        undeployStateful(2);

        // create a SFSB proxy with default affinity
        final StatelessEJBLocator<Echo> statefulEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, Affinity.NONE);
        Echo proxy ;
        try {
            proxy = EJBClient.createSessionProxy(statefulEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // check affinity assignment
        Affinity strongAffinity = EJBClient.getStrongAffinity(proxy);
        Affinity weakAffinity = EJBClient.getWeakAffinity(proxy);
        Assert.assertEquals("Got an unexpected strong affinity", expectedStrongAffinity , strongAffinity);
        Assert.assertTrue("Got an unexpected weak affinity", weakAffinity.equals(expectedWeakAffinity1) || weakAffinity.equals(expectedWeakAffinity2));

        // add back the singleton from the environment
        deployStateful(2);
    }

    /**
     * Test SFSB programmatic proxy initialization
     *
     * scenario:
     *   invoked bean available on both nodes of the cluster "ejb" = {node1, node2}, also available on non-clustered node3
     * expected result:
     *   SFSB session is created on node 'node' of the cluster, strong affinity = ClusterAffinity("ejb"), weakAffinity = NodeAffinity(node)
     */
   @Test
    public void testSFSBProgrammaticProxyInitialization() {
        logger.info("=== Testing programmatic proxy initialization for SFSB proxy with ClusterAffinity ===");

        Affinity expectedStrongAffinity = new ClusterAffinity("ejb");
        Affinity expectedWeakAffinity1 = new NodeAffinity(serverNames[0]);
        Affinity expectedWeakAffinity2 = new NodeAffinity(serverNames[1]);

        // create a SFSB proxy with default affinity
        final StatelessEJBLocator<Echo> statefulEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, expectedStrongAffinity);
        Echo proxy ;
        try {
            proxy = EJBClient.createSessionProxy(statefulEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // check affinity assignment
        Affinity strongAffinity = EJBClient.getStrongAffinity(proxy);
        Affinity weakAffinity = EJBClient.getWeakAffinity(proxy);
        Assert.assertEquals("Got an unexpected strong affinity", expectedStrongAffinity , strongAffinity);
        Assert.assertTrue("Got an unexpected weak affinity", weakAffinity.equals(expectedWeakAffinity1) || weakAffinity.equals(expectedWeakAffinity2));
    }


    /**
     * Test a basic invocation on clustered SFSB
     *
     * scenario:
     *   invoked bean available on three nodes, non-clustered node3, and cluster "ejb" = {node1, node2}
     *   proxy has cluster affinity
     * expected result:
     *   SFSB session is created on a node 'node' in the cluster, strong affinity = ClusterAffinity("ejb"), weakAffinity = NodeAffinity("node")
     *   the first invocation arrives at the node the session was created on
     *   the second invocation arrives at the same node
     */
    @Test
    public void testClusteredSFSBInvocation() throws Exception {
        logger.info("=== Testing invocation on SFSB proxy with ClusterAffinity ===");

        Affinity expectedStrongAffinity = new ClusterAffinity("ejb");
        Affinity expectedWeakAffinity1 = new NodeAffinity(serverNames[0]);
        Affinity expectedWeakAffinity2 = new NodeAffinity(serverNames[1]);

        // create a proxy for invocation
       final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, expectedStrongAffinity);
       Echo proxy;
        try {
            proxy = EJBClient.createSessionProxy(statelessEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // remember the node the session was created on
        Affinity weakAffinity = EJBClient.getWeakAffinity(proxy);
        Assert.assertTrue("Expected weak affinity to have NodeAffinity!", weakAffinity instanceof NodeAffinity);
        String target = ((NodeAffinity)weakAffinity).getNodeName();
        Assert.assertTrue("Session not created on cluster node!", target.equals(serverNames[0]) || target.equals(serverNames[1]));

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        Result<String> echoResult = proxy.echo(message);
        logger.info("Clustered SFSB invocation had target " + echoResult.getNode());

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(target));

        // invoke on the proxy
        logger.info("Invoking on proxy...again");
        echoResult = proxy.echo(message);
        logger.info("Clustered SFSB invocation had target " + echoResult.getNode());

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(target));

    }

    /**
     * Test a basic invocation on non-clustered SFSB
     */
    @Test
    public void testNonClusteredSFSBInvocation() throws Exception {
        logger.info("=== Testing invocation on SFSB proxy with NodeAffinity ===");

        Affinity expectedStrongAffinity = new NodeAffinity("node3");

        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, expectedStrongAffinity);
        Echo proxy;
        try {
            proxy = EJBClient.createSessionProxy(statelessEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        final Result<String> echoResult = proxy.echo(message);

        // check message contents and target
        logger.info("Non-clustered SFSB invocation had target " + echoResult.getNode());
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(serverNames[2]));
    }

    /**
     * Test invocation on clustered SFSB with failover
     *
     * scenario:
     *   invoked bean available on three nodes, non-clustered node3, and cluster "ejb" = {node1, node2}
     *   proxy has cluster affinity
     * expected result:
     *   SFSB session is created on a node 'node' in the cluster, strong affinity = ClusterAffinity("ejb"), weakAffinity = NodeAffinity("node")
     *   the first invocation arrives at the node the session was created on
     *   undeploy the bean from that node
     *   the second invocation arrives at the other node, having successfully failed over
     */
    @Test
    public void testClusteredSFSBInvocationWithFailover() throws Exception {
        logger.info("=== Testing invocation on SFSB proxy with ClusterAffinity ===");

        Affinity expectedStrongAffinity = new ClusterAffinity("ejb");

        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, expectedStrongAffinity);
        Echo proxy;
        try {
            proxy = EJBClient.createSessionProxy(statelessEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // remember the node the session was created on
        Affinity weakAffinity = EJBClient.getWeakAffinity(proxy);
        Assert.assertTrue("Expected weak affinity to have NodeAffinity!", weakAffinity instanceof NodeAffinity);
        String target = ((NodeAffinity)weakAffinity).getNodeName();
        Assert.assertTrue("Session not created on cluster node!", target.equals(serverNames[0]) || target.equals(serverNames[1]));

        // calculate the failover node and the index of the node the session resides on
        String failoverNode = target == "node1" ? "node2" : "node1";
        int sessionNodeIndex = target == "node1" ? 0 : 1;

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        Result<String> echoResult = proxy.echo(message);
        logger.info("Clustered SFSB invocation had target " + echoResult.getNode());

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(target));

        undeployStateful(sessionNodeIndex);

        // invoke on the proxy
        logger.info("Invoking on proxy...again");
        echoResult = proxy.echo(message);
        logger.info("Clustered SFSB invocation had target " + echoResult.getNode());

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(failoverNode));

        deployStateful(sessionNodeIndex);
    }


    /**
     * Test a basic invocation on a clustered SLSB
     *
     * scenario:
     *   invoked bean available on three nodes, cluster "ejb" = {node1, node2}, non-clustered node node3
     *   proxy has cluster affinity to cluster "ejb"
     * expected result:
     *   SLSB invocation occurs on one of the two cluster nodes
     */
    @Test
    public void testClusteredSLSBInvocation() {
        logger.info("=== Testing invocation on SLSB proxy with ClusterAffinity ===");

        Affinity expectedStrongAffinity = new ClusterAffinity("ejb");

        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, expectedStrongAffinity);
        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        final Result<String> echoResult = proxy.echo(message);

        // check message contents and target
        logger.info("Clustered SLSB invocation had target " + echoResult.getNode());
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(serverNames[0]) || echoResult.getNode().equals(serverNames[1]));
    }

    /**
     * Test SLSB invocation with failed node
     *
     * scenario:
     *   invoked bean available on clustered nodes, node1 and node2, as well as non-clustered node, node3
     *   strong affinity is set to ClusterAffinity("ejb")
     *   invoke on the proxy
     *   undeploy the bean from node1
     *   invoke on the proxy
     * expected result:
     *   the first invocation occurs on a cluster nodes, either node1 or node2
     *   the second invocation occurs on the remaining cluster node, node2
     */
    @Test
    public void testClusteredSLSBInvocationWithFailedNode() {
        logger.info("=== Testing SLSB invocation with failed node ===");

        Affinity expectedStrongAffinity = new ClusterAffinity("ejb");

        // create a proxy for SLSB
        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, expectedStrongAffinity);
        Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        Result<String> echoResult = proxy.echo(message);
        logger.info("SLSB invocation had target " + echoResult.getNode());

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(SERVER1_NAME) || echoResult.getNode().equals(SERVER2_NAME));

        undeployStateless(0);

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        echoResult = proxy.echo(message);
        logger.info("SLSB invocation had target " + echoResult.getNode());

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(SERVER2_NAME));

        deployStateless(0);
    }

    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {

        // wipe cluster
        removeCluster(0, CLUSTER_NAME);
        removeCluster(1, CLUSTER_NAME);

        // undeploy servers
        for (int i = 0; i < 3; i++) {
            undeployStateful(i);
            undeployStateless(i);
            stopServer(i);
        }
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
    }
}
