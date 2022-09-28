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

import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.ClusterNodeSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.Result;
import org.jboss.ejb.server.ClusterTopologyListener;
import org.jboss.ejb.server.ClusterTopologyListener.ClusterInfo;
import org.jboss.ejb.server.ClusterTopologyListener.NodeInfo;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jakarta.ejb.NoSuchEJBException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Tests the ability of the the RemoteEJBDiscoveryProvider to detect the condition when a last node left in a cluster has crashed
 * and to remove that cluster from the discovered node registry (DNR).
 * The condition is as follows: if we get a ConnectException when trying to connect to a node X in cluster Y, and the DNR shows
 * X as being the only member of Y, then remove cluster Y from the DNR.
 *
 * This test implements a validation criterion which ensures that the following illegal scenario does not occur:
 * - start two cluster nodes A, B:              // membership = {A,B}
 * - shutdown A                                 // membership = {B}
 * - crash B                                    // membership = {B}
 * - start A                                    // membership = {A,B}
 * In this case, B is e member of the cluster (according to the DNR) but it has crashed.
 *
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class LastNodeToLeaveTestCase {

    private static final Logger logger = Logger.getLogger(LastNodeToLeaveTestCase.class);
    private static final String PROPERTIES_FILE = "last-node-to-leave-jboss-ejb-client.properties";

    // servers
    private static final String SERVER1_NAME = "node1";
    private static final String SERVER2_NAME = "node2";
    private static final int THREADS = 4;
    private static final int INTERVAL_TIME_SECS = 3;
    private static final int INVOCATION_DELAY_SECS = 1;

    private DummyServer[] servers = new DummyServer[2];
    private static String[] serverNames = {SERVER1_NAME, SERVER2_NAME};
    private static boolean[] serversStarted = new boolean[2] ;

    // module
    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    // cluster
    // note: node names and server names should match!
    private static final String CLUSTER_NAME = "ejb";
    private static final String NODE1_NAME = "node1";
    private static final String NODE2_NAME = "node2";

    private static final NodeInfo NODE1 = DummyServer.getNodeInfo(NODE1_NAME, "localhost",6999,"0.0.0.0",0);
    private static final NodeInfo NODE2 = DummyServer.getNodeInfo(NODE2_NAME, "localhost",7099,"0.0.0.0",0);
    private static final ClusterInfo CLUSTER = DummyServer.getClusterInfo(CLUSTER_NAME, NODE1, NODE2);
    private static final ClusterInfo CLUSTER_NODE1 = DummyServer.getClusterInfo(CLUSTER_NAME, NODE1);
    private static final ClusterInfo CLUSTER_NODE2 = DummyServer.getClusterInfo(CLUSTER_NAME, NODE2);

    private static final ClusterTopologyListener.ClusterRemovalInfo CLUSTER_REMOVE_NODE1 = DummyServer.getClusterRemovalInfo(CLUSTER_NAME, NODE1);
    private static final ClusterTopologyListener.ClusterRemovalInfo CLUSTER_REMOVE_NODE2 = DummyServer.getClusterRemovalInfo(CLUSTER_NAME, NODE2);

    private static ExecutorService executorService;
    private volatile boolean runInvocations = true;

    /**
     * Do any general setup here
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // trigger the static init of the correct properties file - this also depends on running in forkMode=always
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(SimpleInvocationTestCase.class.getClassLoader(), PROPERTIES_FILE);
        JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);

        executorService = Executors.newFixedThreadPool(THREADS);
    }

    /**
     * Do any test specific setup here
     */
    @Before
    public void beforeTest() throws Exception {

        startServer(0, CLUSTER);
        startServer(1, CLUSTER);
    }

    /*
     * Returns a list of true/fase values describing current server availability.
     */
    public static List<Boolean> getServersStarted() {
        List<Boolean> booleanList = new ArrayList<Boolean>();
        for (int i = 0; i < serversStarted.length; i++) {
            booleanList.add(new Boolean(serversStarted[i]));
        }
        return booleanList;
    }

    public static class SelectorResult {
        String cluster;
        List<String> connectedNodes;
        List<String> availableNodes;
        List<Boolean> serversAvailable;

        public SelectorResult(String cluster, List<String> connectedNodes, List<String> availableNodes, List<Boolean> serversAvailable) {
            this.cluster = cluster;
            this.connectedNodes = connectedNodes;
            this.availableNodes = availableNodes;
            this.serversAvailable = serversAvailable;
        }

        /**
         * This should assert that no non-available server nodes should be present in either connected nodes nor available nodes
         *
         * @return true of the assertion holds; false otherwise
         */
        public boolean checkResult() {
            for (int i = 0; i < serverNames.length; i++) {
                // check that no non-available server is in the available nodes list for this cluster
                if (!serversAvailable.get(i) && availableNodes.contains(serverNames[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "SelectorResult{" + "cluster='" + cluster + '\'' + ", connectedNodes=" + connectedNodes + ", availableNodes=" + availableNodes + ", serversAvailable=" + serversAvailable + '}';
        }
    }

    // results of the selector visits
    public static ConcurrentHashMap<String, List<SelectorResult>> selectorResults = new ConcurrentHashMap<String, List<SelectorResult>>();

    public static class TestSelector implements ClusterNodeSelector {

        @Override
        public String selectNode(String clusterName, String[] connectedNodes, String[] availableNodes) {
            SelectorResult selectorResult = new SelectorResult(clusterName, Arrays.asList(connectedNodes), Arrays.asList(availableNodes), getServersStarted());
            selectorResults.computeIfAbsent(Thread.currentThread().getName(), ignored -> new ArrayList<SelectorResult>()).add(selectorResult);

            logger.infof("logging selector result: %s", selectorResult);
            // now pick a random node
            return RANDOM_CONNECTED.selectNode(clusterName, connectedNodes, availableNodes);
        }
    }


    /**
     * Test a basic invocation on clustered SLSB
     */
    @Test
    public void testClusteredSLSBInvocation() throws Exception {
        List<Future<?>> retList = new ArrayList<>();

        for(int i = 0; i < THREADS; ++i) {
            retList.add(executorService.submit((Callable<Object>) () -> {
                // create a proxy
                final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, Echo.class.getSimpleName(), DISTINCT_NAME);
                final Echo proxy = EJBClient.createProxy(statelessEJBLocator);

                EJBClient.setStrongAffinity(proxy, new ClusterAffinity("ejb"));
                Assert.assertNotNull("Received a null proxy", proxy);
                logger.info("Created proxy for Echo: " + proxy.toString());

                while (runInvocations) {
                    logger.info("Invoking on proxy...");
                    // invoke on the proxy (use a ClusterAffinity for now)
                    final String message = "hello!";
                    // one second delay between invocations
                    Thread.sleep(INVOCATION_DELAY_SECS * 1000);

                    Result<String> echo = null;
                    try {
                        echo = proxy.echo(message);
                    } catch(NoSuchEJBException e) {
                        logger.info("Got NoSuchEJBException from node, skipping...");
                        echo = new Result<String>(message, "unknown");
                    }
                    Assert.assertEquals("Got an unexpected echo", echo, message);
                }
                return "ok";
            }));
        }

        // stop one of the two servers ( {node1, node2} -> {node2})
        Thread.sleep(INTERVAL_TIME_SECS * 1000);
        stopServer(0, CLUSTER_REMOVE_NODE1);
        servers[1].removeClusterNodes(CLUSTER_REMOVE_NODE1);
        Thread.sleep(INTERVAL_TIME_SECS * 1000);

        // now crash the last server in the cluster  ( {node2} -> {})
        crashServer(1);
        Thread.sleep(INTERVAL_TIME_SECS * 1000);

        // restart one of the two servers - we should not see server1 as being available  ( {} -> {node1})
        startServer(0, CLUSTER_NODE1);

        Thread.sleep(INTERVAL_TIME_SECS * 1000);

        // stop the client
        runInvocations = false;

        // check the list of connected and available servers
        for(Future<?> i : retList) {
            try {
                i.get();
            } catch(Exception e) {
                logger.infof("Got exception processing client thread future: exception = %s", e.toString());
            }
        }

        // print and validate the results
        for(Map.Entry<String, List<SelectorResult>> entry : selectorResults.entrySet()) {
            String thread = entry.getKey();
            List<SelectorResult> selectorResults = entry.getValue();
            logger.infof("Test results for thread = %s:\n", thread);
            for (SelectorResult selectorResult : selectorResults) {
                logger.infof("Selector result: %s", selectorResult);
                Assert.assertTrue(selectorResult.checkResult());
            }
        }
    }

    /**
     * Starts a cluster node with the default cluster membership (i.e. {node1, node2})
     * @param server the server number
     * @throws Exception
     */
    private void startServer(int server) throws Exception {
        servers[server] = new DummyServer("localhost", 6999 + (server * 100), serverNames[server]);
        servers[server].start();
        serversStarted[server] = true;
        logger.info("Started server " + serverNames[server]);

        servers[server].register(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getSimpleName(), new EchoBean());
        logger.info("Registered module on server " + servers[server]);

        servers[server].addCluster(CLUSTER);
        logger.info("Added node to cluster " + CLUSTER_NAME + ": server " + servers[server]);

    }

    /**
     * Stops a server with the default cluster being removed (i.e. {node1, node2})
     *
     * @param server the server number
     */
    private void stopServer(int server) {
        if (serversStarted[server]) {
            try {
                servers[server].unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
                servers[server].removeCluster(CLUSTER_NAME);
                logger.info("Unregistered module from " + serverNames[server]);
                this.servers[server].stop();
                logger.info("Stopped server " + serverNames[server]);
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            } finally {
                serversStarted[server] = false;
            }
        }
    }

    /**
     * Stops a server with crash failure semantics (i.e. don't send out any module or topology updates)
     *
     * @param server the server number
     */
    private void crashServer(int server) {
        if (serversStarted[server]) {
            try {
                this.servers[server].stop();
                logger.info("Crashed server " + serverNames[server]);
            } catch (Throwable t) {
                logger.info("Could not crash server", t);
            } finally {
                serversStarted[server] = false;
            }
        }
    }

    /**
     * Starts a server with a particular cluster topology
     * This is required to model changes in cluster membership.
     *
     * @param server the server number
     * @param startingClusterTopology the cluster topology the server should advertise when first contacted
     * @throws Exception
     */
    private void startServer(int server, ClusterInfo startingClusterTopology) throws Exception {
        servers[server] = new DummyServer("localhost", 6999 + (server * 100), serverNames[server]);
        servers[server].start();
        serversStarted[server] = true;
        logger.info("Started server " + serverNames[server]);

        servers[server].register(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getSimpleName(), new EchoBean());
        logger.info("Registered module on server " + servers[server]);

        servers[server].addClusterNodes(startingClusterTopology);
        logger.info("Added node to cluster " + CLUSTER_NAME + ": server " + servers[server]);
    }

    /**
     * Stops a server with a particular cluster removal topology
     * This is required to model changes in cluster membership.
     *
     * @param server the server number
     * @param clusterRemovalTopology the cluster removal topology to convey to the client when it shuts down
     */
    private void stopServer(int server, ClusterTopologyListener.ClusterRemovalInfo clusterRemovalTopology) {
        if (serversStarted[server]) {
            try {
                servers[server].unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
                logger.info("Unregistered module from " + serverNames[server]);
                servers[server].removeClusterNodes(clusterRemovalTopology);
                logger.info("Removed node from cluster " + CLUSTER_NAME + ": server " + servers[server]);
                this.servers[server].stop();
                logger.info("Stopped server " + serverNames[server]);
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            } finally {
                serversStarted[server] = false;
            }
        }
    }

    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {
        stopServer(0, CLUSTER_REMOVE_NODE1);
        stopServer(1, CLUSTER_REMOVE_NODE2);
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
        executorService.shutdownNow();
    }

}
