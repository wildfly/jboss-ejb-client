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

import jakarta.ejb.NoSuchEJBException;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.Result;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests which validate the timeout mechanism(s) used in discovery.
 *
 * A call to discovery is made up of two independent activities: an activity which generates discovery results,
 * amd an activity which consumes discovery results. Under normal circumstances, discovery result generation
 * terminates, but this is not always the case.
 *
 * The system property org.jboss.ejb.client.discovery.timeout is a property which can set a timeout value for
 * discovery result consumption: if we have not consumed all results before the expiration of the timeout,
 * discovery result consumption is terminated, and returns the (possibly incomplete) set of already consumed
 * results.
 *
 * @author <a href="ingo@redhat.com">Ingo Weiss</a>
 */
public class RemoteDiscoveryTimeoutTestCase extends AbstractEJBClientTestCase {
   private static final Logger logger = Logger.getLogger(RemoteDiscoveryTimeoutTestCase.class);
   private static final String PROPERTIES_FILE = "no-protocol-jboss-ejb-client.properties";

   /**
    * Initialize the legacy JBossEJBProperties contextual with a jboss-ejb-client.properties file which has one
    * remote destination with a missing protocol; this will result in the client not being able to connect to
    * the server during discovery.
    *
    * @throws Exception
    */
   @BeforeClass
   public static void beforeClass() throws Exception {
      // trigger the static init of the correct properties file - this also depends on running in forkMode=always
      JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(RemoteDiscoveryTimeoutTestCase.class.getClassLoader(), PROPERTIES_FILE);
      JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);
   }

   /**
    * Before each test, start a server at localhost:6999 and deploy a stateless bean.
    */
   @Before
   public void beforeTest() throws Exception {
      // start a server
      startServer(0);
      // deploy a stateless echo bean
      deployStateless(0);
   }

   /**
    * After each test, undeploy the staless bean and stop the server.
    */
   @After
   public void afterTest() {
      // undeploy a stateless echo bean
      undeployStateless(0);
      // stop a server
      stopServer(0);
   }

   /**
    * Test a failed client discovery attempt, using the discovery timeout to avoid the test hanging.
    *
    * @todo need to consider exceptions and connect timeouts and avoid sitiations where processes can hang
    */
   @Test
   public void testClientDiscoveryTimeout() throws InterruptedException {
      logger.info("Testing client discovery timeout of 10 seconds");
      System.setProperty("org.jboss.ejb.client.discovery.timeout", "10");
      String errorMessage = "";

      try {
         // create a proxy for invocation
         final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<>
            (Echo.class, APP_NAME, MODULE_NAME, Echo.class.getSimpleName(), DISTINCT_NAME);
         final Echo proxy = EJBClient.createProxy(statelessEJBLocator);
         Assert.assertNotNull("Received a null proxy", proxy);
         logger.info("Created proxy for Echo: " + proxy.toString());

         logger.info("Invoking on proxy...");
         // Invoke on the proxy. This should fail in 10 seconds or else it'll hang.
         final String message = "hello!";
         final Result<String> echo = proxy.echo(message);
         Assert.assertNull(echo);
      } catch (NoSuchEJBException nsee) {
         errorMessage = nsee.getMessage();
      }

      Assert.assertTrue("Wrong error message. Expected EJBCLIENT000079.", errorMessage.contains("EJBCLIENT000079"));
   }
}
