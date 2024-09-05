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

import org.jboss.ejb.client.DeploymentNodeSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.StatefulEchoBean;
import org.jboss.ejb.client.test.common.StatelessEchoBean;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.transaction.client.ContextTransactionManager;
import org.wildfly.transaction.client.ContextTransactionSynchronizationRegistry;

/**
 * Tests the use of DeploymentNodeSelector to control the choice of target node for an invocation, when
 * the target deployment is available on multiple nodes.
 *
 * @author Jason T. Greene
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class DeploymentNodeSelectorTestCase extends AbstractEJBClientTestCase {
    private static final Logger logger = Logger.getLogger(DeploymentNodeSelectorTestCase.class);
    private static ContextTransactionManager txManager;
    private static ContextTransactionSynchronizationRegistry txSyncRegistry;

    /**
     * Configure the EJBClientContext to be aware of servers localhost:6999 and localhost:7099 as well as
     * which DeploymentNodeSelector to use.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        String PROPERTIES_FILE = "deployment-node-selector-jboss-ejb-client.properties";

        // trigger the static init of the correct properties file - this also depends on running in forkMode=always
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(SimpleInvocationTestCase.class.getClassLoader(), PROPERTIES_FILE);
        JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);

        // Launch callback if needed
        ClassCallback.beforeClassCallback();
    }

    /**
     * Before each test, start the two servers at localhost:6999 and localhost:7099  and deploy deploy stateless
     * and stateful applicatuons on each.
     */
    @Before
    public void beforeTest() throws Exception {

        for (int i = 0; i < 2; i++) {
            // start a server
            startServer(i, 6999 + (i * 100), true);
            deployStateful(i);
            deployStateless(i);
        }
    }

    /*
     * A deployment node selector which returns the pick node (if set) or the first connected node otherwise.
     */
    public static class TestSelector implements DeploymentNodeSelector {
        private static volatile String PICK_NODE = null;

        @Override
        public String selectNode(String[] eligibleNodes, String appName, String moduleName, String distinctName) {
            if (PICK_NODE != null) {
                return PICK_NODE;
            }
            return eligibleNodes[0];
        }
    }

    /**
     * Test the operation of the DeploymentNodeSelector with a SLSB deployment.
     *
     * This test uses the pick node to select the target node for the invocation and verifies that the invocation
     * arrived at the correct node.
     *
     * In this case, as the SLSB is deployed on both nodes, and not stateful, we can use a different pick node
     * with the same proxy instance, and invocations will be correctly directed.
     */
    @Test
    public void testSLSBInvocation() {
        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, StatelessEchoBean.class.getSimpleName(), DISTINCT_NAME);
        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);

        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        logger.info("Invoking on proxy...");

        TestSelector.PICK_NODE = SERVER1_NAME;
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(SERVER1_NAME, proxy.echo("someMsg").getNode());
        }

        TestSelector.PICK_NODE = SERVER2_NAME;
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(SERVER2_NAME, proxy.echo("someMsg").getNode());
        }
    }

    /**
     * Test the operation of the DeploymentNodeSelector with a SFSB deployment.
     *
     * This test uses the pick node to select the target node for the invocation and verifies that the invocation
     * arrived at the correct node.
     *
     * In this case, as the SFSB is deployed on both nodes and stateful, each proxy will have affinity to the node
     * where its session was created, and cannot be redirected to another node without invocation failure (no more
     * destiations available).
     */
    @Test
    public void testSFSBInvocation() throws Exception {
        TestSelector.PICK_NODE = SERVER2_NAME;
        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, StatefulEchoBean.class.getSimpleName(), DISTINCT_NAME);
        StatefulEJBLocator<Echo> statefulEJBLocator = null;
        statefulEJBLocator = EJBClient.createSession(statelessEJBLocator);

        Echo proxy = EJBClient.createProxy(statefulEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(SERVER2_NAME, proxy.echo("someMsg").getNode());
        }

        TestSelector.PICK_NODE = SERVER1_NAME;
        statefulEJBLocator = EJBClient.createSession(statelessEJBLocator);
        proxy = EJBClient.createProxy(statefulEJBLocator);
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(SERVER1_NAME, proxy.echo("someMsg").getNode());
        }
    }

    /**
     * After each test, undeploy the applications and stop the servers.
     */
    @After
    public void afterTest() {

        for (int i = 0; i < 2; i++) {
            // stop a server
            undeployStateful(i);
            undeployStateless(i);
            stopServer(i);
        }
    }
}
