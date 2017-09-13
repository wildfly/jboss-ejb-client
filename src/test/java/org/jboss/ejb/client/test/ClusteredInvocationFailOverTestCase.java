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
import org.jboss.ejb.client.EJBClientCluster;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.server.ClusterTopologyListener.ClusterInfo;
import org.jboss.ejb.server.ClusterTopologyListener.NodeInfo;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests basic invocation of a bean deployed on a single server node.
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class ClusteredInvocationFailOverTestCase {

    public static AtomicInteger SENT = new AtomicInteger();

    private static final Logger logger = Logger.getLogger(ClusteredInvocationFailOverTestCase.class);
    private static final String PROPERTIES_FILE = "clustered-jboss-ejb-client.properties";

    // servers
    private static final String SERVER1_NAME = "node1";
    private static final String SERVER2_NAME = "node2";
    private static final int THREADS = 40;

    private DummyServer[] servers = new DummyServer[2];
    private static String[] serverNames = {SERVER1_NAME, SERVER2_NAME};
    private boolean[] serversStarted = new boolean[2] ;

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

        //startServer(0);
        startServer(1);
    }


    /**
     * Test a basic invocation on clustered SLSB
     */
    @Test
    public void testClusteredSLSBInvocation() throws Exception {
        List<Future<?>> retList = new ArrayList<>();

        for(int i = 0; i < THREADS; ++i) {
            retList.add(executorService.submit((Callable<Object>) () -> {
                while (runInvocations) {
                    final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, Echo.class.getSimpleName(), DISTINCT_NAME);
                    final Echo proxy = EJBClient.createProxy(statelessEJBLocator);

                    EJBClient.setStrongAffinity(proxy, new ClusterAffinity("ejb"));
                    Assert.assertNotNull("Received a null proxy", proxy);
                    logger.info("Created proxy for Echo: " + proxy.toString());

                    logger.info("Invoking on proxy...");
                    // invoke on the proxy (use a ClusterAffinity for now)
                    final String message = "hello!";
                    SENT.incrementAndGet();
                    final String echo = proxy.echo(message);
                    Assert.assertEquals("Got an unexpected echo", echo, message);
                }
                return "ok";
            }));
        }

        Thread.sleep(500);
        stopServer(0);
        //startServer(0);
        //Thread.sleep(500);
        //stopServer(1);

        Thread.sleep(500);
        runInvocations = false;
        for(Future<?> i : retList) {
            i.get();
        }

    }

    private void stopServer(int server) {
        if (serversStarted[server]) {
            try {
                servers[server].unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
                servers[server].removeCluster(CLUSTER_NAME);
                logger.info("Unregistered module from " + serverNames[0]);
                this.servers[server].stop();
                logger.info("Stopped server " + serverNames[server]);
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            } finally {
                serversStarted[server] = false;
            }
        }
    }

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
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {
        stopServer(0);
        stopServer(1);
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
        executorService.shutdownNow();
    }

}
