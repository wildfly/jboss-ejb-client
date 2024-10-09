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

import java.util.Collection;
import java.util.List;

import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.ClusterNodeSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientCluster;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
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
import org.junit.Test;

/**
 * Tests use of the ClusterNodeSelector to control the choice of target node for an invocation, when all invocations
 * have a strong affinity to a cluster.
 *
 * @author Jason T. Greene
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class ClusterNodeSelectorTestCase extends AbstractEJBClientTestCase {

    private static final Logger logger = Logger.getLogger(ClusterNodeSelectorTestCase.class);
    private static final String PROPERTIES_FILE = "cluster-node-selector-jboss-ejb-client.properties";

    /*
     * A cluster node selector which returns the pick node (if set) or the first connected node otherwise.
     */
    public static class TestSelector implements ClusterNodeSelector  {
        private static volatile String PICK_NODE = null;

        @Override
        public String selectNode(String clusterName, String[] connectedNodes, String[] totalAvailableNodes) {
            if (PICK_NODE != null) {
                return PICK_NODE;
            }
            return connectedNodes[0];
        }
    }

    /**
     * Configure the EJBClientContext to be aware of servers localhost:6999 and localhost:7099 as a cluster "ejb",
     * as well as which ClusterNodeSelector to use.
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
     * Before each test, start the two servers as a cluster and deploy deploy stateless and stateful applicatuons on each.
     */
    @Before
    public void beforeTest() throws Exception {

        // start a server
        startServer(0, 6999);
        startServer(1, 7099);

        // deploy modules
        deployStateful(0);
        deployStateless(0);
        deployStateful(1);
        deployStateless(1);

        // define clusters
        defineCluster(0, CLUSTER);
        defineCluster(1, CLUSTER);
    }

    /**
     * A convenince test to ensure that the EJBClientContext is setup correctly for this test.
     * It expects two servers in a cluster called "ejb".
     */
    @Test
    public void testConfiguredConnections() {
        EJBClientContext context = EJBClientContext.getCurrent();
        List<EJBClientConnection> connections = context.getConfiguredConnections();

        Assert.assertEquals("Number of configured connections for this context is incorrect", 2, connections.size());
        for (EJBClientConnection connection : connections) {
            logger.info("found connection: destination = " + connection.getDestination() + ", forDiscovery = " + connection.isForDiscovery());
        }

        Collection<EJBClientCluster> clusters = context.getInitialConfiguredClusters();
        //Assert.assertEquals("Number of configured clusters is incorrect", 1, clusters.size());
        for (EJBClientCluster cluster: clusters) {
            String name = cluster.getName();
            logger.info("found cluster: name = " + name);
            //Assert.assertEquals("Cluster name should be ejb", "ejb", name);
        }
    }

    /**
     * Test the operation of the ClusterNodeSelector with a SLSB deployment.
     *
     * This test uses the pick node to select the target node for the invocation and verifies that the invocation
     * arrived at the correct node.
     */
    @Test
    public void testClusteredSLSBInvocation() {
        logger.info("Testing invocation on SLSB proxy with ClusterAffinity");

        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, StatelessEchoBean.class.getSimpleName(), DISTINCT_NAME);
        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);

        EJBClient.setStrongAffinity(proxy, new ClusterAffinity("ejb"));
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        logger.info("Invoking on proxy...");

        // use the cluster node selector to always use node1 as the target node for the invocation
        TestSelector.PICK_NODE = NODE1_NAME;
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(NODE1_NAME, proxy.echo("someMsg").getNode());
        }

        // use the cluster node selector to always use node2 as the target node for the invocation
        TestSelector.PICK_NODE = NODE2_NAME;
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(NODE2_NAME, proxy.echo("someMsg").getNode());
        }
    }

    /**
     * Test the operation of the ClusterNodeSelector with a SLSB deployment.
     *
     * Creating a SFSB proxy for a clustered deployment has the side-effect of setting the weak affinity to the node
     * the session was created on. So in the absence of failures, all invocations using that proxy should be directed
     * to that node. This test validates that the ClusterNodeSelector can be used to control the target for
     * session creation.
     */
    @Test
    public void testClusteredSFSBInvocation() throws Exception {
        logger.info("Testing invocation on SFSB proxy with ClusterAffinity");

        TestSelector.PICK_NODE = NODE2_NAME;
        // create a proxy for invocation and, using the ClusterNodeSelector, force session creation onto node2
        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, StatefulEchoBean.class.getSimpleName(), DISTINCT_NAME);
        StatefulEJBLocator<Echo> statefulEJBLocator = null;
        statefulEJBLocator = EJBClient.createSession(statelessEJBLocator.withNewAffinity(new ClusterAffinity("ejb")));

        // verify that all invocations are sticky to node2
        Echo proxy = EJBClient.createProxy(statefulEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(NODE2_NAME, proxy.echo("someMsg").getNode());
        }

        TestSelector.PICK_NODE = NODE1_NAME;
        // create a new proxy for invocation and using the ClusterNodeSelector, force session creation onto node1
        statefulEJBLocator = EJBClient.createSession(statelessEJBLocator.withNewAffinity(new ClusterAffinity("ejb")));
        proxy = EJBClient.createProxy(statefulEJBLocator);

        // verify that all invocations are now sticky to node2
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(NODE1_NAME, proxy.echo("someMsg").getNode());
        }
    }

    /**
     * After each test, undeploy the applications and stop the servers in the cluster.
     */
    @After
    public void afterTest() {
        undeployStateless(0);
        undeployStateful(0);

        undeployStateless(1);
        undeployStateful(1);

        removeCluster(0, CLUSTER_NAME);
        removeCluster(1, CLUSTER_NAME);

        stopServer(0);
        stopServer(1);
    }
}
