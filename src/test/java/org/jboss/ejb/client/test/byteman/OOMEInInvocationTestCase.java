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
package org.jboss.ejb.client.test.byteman;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.AbstractEJBClientTestCase;
import org.jboss.ejb.client.test.ClassCallback;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test throws OutOfMemoryException on receiver through byteman, we check the log returned by XNIO contains
 * all the information
 * @author tmiyar
 *
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="target/test-classes")
public class OOMEInInvocationTestCase extends AbstractEJBClientTestCase {

    private static final Logger logger = Logger.getLogger(OOMEInInvocationTestCase.class);
    private static final String PROPERTIES_FILE = "jboss-ejb-client.properties";

    /**
     * Do any general setup here
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // trigger the static init of the correct proeprties file - this also depends on running in forkMode=always
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(OOMEInInvocationTestCase.class.getClassLoader(), PROPERTIES_FILE);
        JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);

        // Launch callback if needed
        ClassCallback.beforeClassCallback();
    }

    /**
     * Do any test specific setup here
     */
    @Before
    public void beforeTest() throws Exception {
        // start server
            startServer(0, 6999);
            // deploy a stateful bean
            deployStateless(0);
            System.clearProperty("echo");
     }

    /**
     * Test SLSB invocation
     *
     * scenario:
     *   invoked bean available on node1
     *   strong affinity is set to Affinity.NONE
     * expected result:
     *   invocation will fail and a message containing the invoked method will be displayed
     */
    @Test
    public void testSLSBInvocation() {
        
        Assert.assertEquals("echo system property exists", null, System.getProperty("echo"));
        
        Affinity expectedStrongAffinity = Affinity.NONE;
        
        // create a proxy for SLSB
        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, expectedStrongAffinity);
        Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        try {
            proxy.echo(message);
        } catch (RuntimeException e) {
            //don't do anything, it is expected
        }
        // check the property contents
        Assert.assertEquals("method echo not in error message", "true", System.getProperty("echo"));
    }

    /**
      * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {
        // undeploy server
            undeployStateless(0);
            stopServer(0);
            System.clearProperty("echo");
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
    }
    
 }
