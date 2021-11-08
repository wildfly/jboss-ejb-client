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

import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.Result;
import org.jboss.ejb.client.test.common.StatelessEchoBean;
import org.jboss.ejb.server.ClusterTopologyListener;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ejb.EJBException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.fail;

/**
 * Tests fail-over of the EJB client invocation mechanism.
 *
 * NOTE: When shutting down a server, if this happens during discovery, we can have trouble:
 * - we shut down the server A
 * - discovery tries to contact all known nodes {A,B}; gets a channel closed exception
 * - discovery cannot reach node A
 * - topology update arrives which excludes A from available nodes
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class ClusteredInvocationFailOverTestCase extends AbstractEJBClientTestCase {

    public static AtomicInteger SENT = new AtomicInteger();

    private static final Logger logger = Logger.getLogger(ClusteredInvocationFailOverTestCase.class);
    private static final String PROPERTIES_FILE = "jboss-ejb-client.properties";

    private static final int THREADS = 1;

    public static final ClusterTopologyListener.ClusterRemovalInfo removal = DummyServer.getClusterRemovalInfo(CLUSTER_NAME, NODE1);
    public static final ClusterTopologyListener.ClusterInfo addition = DummyServer.getClusterInfo(CLUSTER_NAME, NODE1);

    private static ExecutorService executorService;
    private volatile boolean runInvocations = true;

    Map<String, AtomicInteger> twoNodesUp = new HashMap<String, AtomicInteger>();
    Map<String, AtomicInteger> oneNodeUp = new HashMap<String, AtomicInteger>();

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
        // start a cluster of two nodes
        for (int i = 0; i < 2; i++) {
            startServer(i);
            deployStateless(i);
            defineCluster(i, CLUSTER);
        }
        twoNodesUp.clear();
        oneNodeUp.clear();
    }


    /**
     * Test a basic invocation on clustered SLSB
     */
    @Test
    public void testClusteredSLSBInvocation() throws Exception {
        List<Future<?>> retList = new ArrayList<>();

        for(int i = 0; i < THREADS; ++i) {
            // set up THREADs number of invocation loops
            retList.add(executorService.submit((Callable<Object>) () -> {
                while (runInvocations) {
                    try {
                        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, StatelessEchoBean.class.getSimpleName(), DISTINCT_NAME);
                        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);

                        EJBClient.setStrongAffinity(proxy, new ClusterAffinity("ejb"));
                        Assert.assertNotNull("Received a null proxy", proxy);
                        logger.info("Created proxy for Echo: " + proxy.toString());

                        logger.info("Invoking on proxy...");
                        // invoke on the proxy (use a ClusterAffinity for now)
                        final String message = "hello!";
                        SENT.incrementAndGet();
                        final Result<String> echoResult = proxy.echo(message);
                        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);

                        // increment the invocation count
                        if (isServerStarted(0) && isServerStarted(1)) {
                            synchronized (twoNodesUp) {
                                String node = echoResult.getNode();
                                AtomicInteger hits = twoNodesUp.get(node);
                                if (hits == null) {
                                    twoNodesUp.put(node, new AtomicInteger(0));
                                    hits = twoNodesUp.get(node);
                                }
                                hits.getAndIncrement();
                                logger.info("invocation on two nodes hit node: " + node);
                            }
                        } else if (isServerStarted(1)) {
                            synchronized (oneNodeUp) {
                                String node = echoResult.getNode();
                                AtomicInteger hits = oneNodeUp.get(node);
                                if (hits == null) {
                                    oneNodeUp.put(node, new AtomicInteger(0));
                                    hits = oneNodeUp.get(node);
                                }
                                hits.getAndIncrement();
                                logger.info("invocation on one nodes hit node: " + node);
                            }
                        } else {
                            fail("Invocation hit unreachable target");
                        }

                    } catch(Exception e) {
                        if (e instanceof EJBException && e.getCause() instanceof ClosedChannelException) {
                            // this is expected when we shut the server down asynchronously during an invocation
                        } else {
                            Thread.dumpStack();
                            fail("Invocation failed with exception " + e.toString());
                        }
                    }
                }
                return "ok";
            }));
        }

        // invoke
        Thread.sleep(500);

        // stop a server and update the topology of the remaining node
        logger.info("Stopping server: " + serverNames[0]);
        undeployStateless(0);
        stopServer(0);
        removeClusterNodes(1, removal);
        logger.info("Stopped server: " + serverNames[0]);


        // invoke
        Thread.sleep(500);

        // start a server and update the topologuy of the new node and the remaining node
        logger.info("Starting server: " + serverNames[0]);
        startServer(0);
        deployStateless(0);
        defineCluster(0, CLUSTER);
        addClusterNodes(1, addition);
        logger.info("Started server: " + serverNames[0]);

        // invoke
        Thread.sleep(500);

        runInvocations = false;
        for(Future<?> i : retList) {
            i.get();
        }

        // check results
        System.out.println("map twoNodesUp = " + twoNodesUp.toString());
        System.out.println("map oneNodeUp = " + oneNodeUp.toString());

    }

    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() throws Exception {
        // shutdown the cluster of two nodes
        for (int i = 0; i < 2; i++) {
            stopServer(i);
            undeployStateless(i);
            removeCluster(i, CLUSTER.getClusterName());
        }
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
        executorService.shutdownNow();
    }

}
