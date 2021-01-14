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

import static org.jboss.ejb._private.SystemProperties.DISCOVERY_TIMEOUT;

import javax.ejb.NoSuchEJBException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
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
 * @author <a href="ingo@redhat.com">Ingo Weiss</a>
 */
public class RemoteDiscoveryTimeoutTestCase {
   private static final Logger logger = Logger.getLogger(RemoteDiscoveryTimeoutTestCase.class);
   private static final String PROPERTIES_FILE = "no-protocol-jboss-ejb-client.properties";

   private DummyServer server;
   private boolean serverStarted = false;

   // module
   private static final String APP_NAME = "my-foo-app";
   private static final String MODULE_NAME = "my-bar-module";
   private static final String DISTINCT_NAME = "";

   private static final String SERVER_NAME = "test-server";

   /**
    * Do any general setup here
    * @throws Exception
    */
   @BeforeClass
   public static void beforeClass() throws Exception {
      // trigger the static init of the correct properties file - this also depends on running in forkMode=always
      JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(RemoteDiscoveryTimeoutTestCase.class.getClassLoader(), PROPERTIES_FILE);
      JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);
   }

   /**
    * Do any test specific setup here
    */
   @Before
   public void beforeTest() throws Exception {
      // start a server
      server = new DummyServer("localhost", 6999, SERVER_NAME);
      server.start();
      serverStarted = true;
      logger.info("Started server ...");

      server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getSimpleName(), new EchoBean());
      logger.info("Registered module ...");
   }

   /**
    * Do any test-specific tear down here.
    */
   @After
   public void afterTest() {
      server.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
      logger.info("Unregistered module ...");

      if (serverStarted) {
         try {
            this.server.stop();
         } catch (Throwable t) {
            logger.info("Could not stop server", t);
         }
      }
      logger.info("Stopped server ...");
   }

   /**
    * Test a failed client discovery
    */
   @Test
   public void testClientDiscoveryTimeout() throws InterruptedException {
      logger.info("Testing client discovery timeout of 10 seconds");
      System.setProperty(DISCOVERY_TIMEOUT, "10");
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
