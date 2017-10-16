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

import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import com.arjuna.ats.internal.jbossatx.jta.jca.XATerminator;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.DeploymentNodeSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
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
import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.transaction.client.ContextTransactionManager;
import org.wildfly.transaction.client.ContextTransactionSynchronizationRegistry;
import org.wildfly.transaction.client.LocalTransactionContext;
import org.wildfly.transaction.client.LocalUserTransaction;
import org.wildfly.transaction.client.RemoteTransactionContext;
import org.wildfly.transaction.client.provider.jboss.JBossLocalTransactionProvider;

/**
 * Tests DeploymentNodeSelector
 *
 * @author Jason T. Greene
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class DeploymentNodeSelectorTestCase {
    private static final Logger logger = Logger.getLogger(DeploymentNodeSelectorTestCase.class);
    private static ContextTransactionManager txManager;
    private static ContextTransactionSynchronizationRegistry txSyncRegistry;

    private DummyServer server1, server2, server3, server4;
    private boolean serverStarted = false;

    // module
    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private static final String SERVER1_NAME = "server1";
    private static final String SERVER2_NAME = "server2";

    /**
     * Do any general setup here
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
     * Do any test specific setup here
     */
    @Before
    public void beforeTest() throws Exception {
        // start a server
        server1 = new DummyServer("localhost", 6999, SERVER1_NAME, true);
        server2 = new DummyServer("localhost", 7999, SERVER2_NAME, true);
        server1.start();
        server2.start();
        serverStarted = true;
        logger.info("Started servers ...");

        server1.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(SERVER1_NAME));
        server2.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(SERVER2_NAME));
    }

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

    @Test
      public void testSLSBInvocation() {
          // create a proxy for invocation
          final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, EchoBean.class.getSimpleName(), DISTINCT_NAME);
          final Echo proxy = EJBClient.createProxy(statelessEJBLocator);

          Assert.assertNotNull("Received a null proxy", proxy);
          logger.info("Created proxy for Echo: " + proxy.toString());

          logger.info("Invoking on proxy...");

          TestSelector.PICK_NODE = SERVER1_NAME;
          for (int i = 0; i < 10; i++) {
              Assert.assertEquals(SERVER1_NAME, proxy.whoAreYou());
          }

          TestSelector.PICK_NODE = SERVER2_NAME;
          for (int i = 0; i < 10; i++) {
              Assert.assertEquals(SERVER2_NAME, proxy.whoAreYou());
          }
      }

      /**
       * Test a basic invocation on clustered SFSB
       */
      @Test
      public void testSFSBInvocation() throws Exception {
          TestSelector.PICK_NODE = SERVER2_NAME;
          // create a proxy for invocation
          final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, EchoBean.class.getSimpleName(), DISTINCT_NAME);
          StatefulEJBLocator<Echo> statefulEJBLocator = null;
          statefulEJBLocator = EJBClient.createSession(statelessEJBLocator);

          Echo proxy = EJBClient.createProxy(statefulEJBLocator);
          Assert.assertNotNull("Received a null proxy", proxy);
          for (int i = 0; i < 10; i++) {
              Assert.assertEquals(SERVER2_NAME, proxy.whoAreYou());
          }

          TestSelector.PICK_NODE = SERVER1_NAME;
          statefulEJBLocator = EJBClient.createSession(statelessEJBLocator);
          proxy = EJBClient.createProxy(statefulEJBLocator);
          for (int i = 0; i < 10; i++) {
              Assert.assertEquals(SERVER1_NAME, proxy.whoAreYou());
          }
      }
    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {
        server1.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        server2.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        logger.info("Unregistered module ...");

        if (serverStarted) {
            try {
                this.server1.stop();
                this.server2.stop();
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
