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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import jakarta.ejb.NoSuchEJBException;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.ejb.client.legacy.JBossEJBProperties;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.Result;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests basic features of proxies for invocation of a bean deployed on a single server node.
 *
 * The server environment consists of two singleton nodes:
 * on node1: beans StatefulEchoBean, StatelessEchoBean are deployed, non-clustered
 * on node2: beans StatefulEchoBean, StatelessEchoBean are deployed, non-clustered
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class SimpleInvocationTestCase extends AbstractEJBClientTestCase {

    private static final Logger logger = Logger.getLogger(SimpleInvocationTestCase.class);
    private static final String PROPERTIES_FILE = "jboss-ejb-client.properties";

    /**
     * Do any general setup here
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // trigger the static init of the correct proeprties file - this also depends on running in forkMode=always
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
        for (int i = 0; i < 2; i++) {
            startServer(i);
            // deploy a stateful bean
            deployStateful(i);
            // deploy a stateless bean
            deployStateless(i);
        }
     }

    /**
     * Test number of configured connections in EJBClientContext.
     */
    @Test
    public void testConfiguredConnections() {
        logger.info("=== Testing EJBClientContext for correct number of configured connections ===");

        EJBClientContext context = EJBClientContext.getCurrent();
        List<EJBClientConnection> connections = context.getConfiguredConnections();

        Assert.assertEquals("Number of configured connections for this context is incorrect", 2, connections.size());
        for (EJBClientConnection connection : connections) {
            logger.info("found connection: destination = " + connection.getDestination() + ", forDiscovery = " + connection.isForDiscovery());
        }
    }

    /**
     * Test invocation with URIAffinity
     *
     * scenario:
     *   invoked bean available on two targets
     * expected result:
     *   invocation always chooses target identified by URI
     */
    @Test
    public void testInvocationWithURIAffinity() {
        logger.info("=== Testing invocation on proxy with URIAffinity ===");

        // create a proxy for invocation
        URI uri = null;
        try {
            uri = new URI("remote", null,"localhost", 6999, null, null,null);
        } catch(URISyntaxException use) {
            //
        }
        final Affinity expectedStrongAffinity = URIAffinity.forUri(uri);

        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, expectedStrongAffinity);
        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        final Result<String> echoResult = proxy.echo(message);

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertEquals("Got an unexpected node for invocation target", echoResult.getNode(), SERVER1_NAME);
    }

    /**
     * Test invocation with URIAffinity
     *
     * scenario:
     *   invoked bean available on one target, not pointed to by URI
     * expected result:
     *   invocation always chooses target identified by URI, NoSuchEJBException
     */
    @Test
    public void testInvocationWithURIAffinityNoFailover() {
        logger.info("=== Testing invocation on proxy with URIAffinity does not failover ===");

        // create a proxy for invocation
        URI uri = null;
        try {
            uri = new URI("remote", null,"localhost", 6999, null, null,null);
        } catch(URISyntaxException use) {
            //
        }
        final Affinity expectedStrongAffinity = URIAffinity.forUri(uri);

        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, expectedStrongAffinity);
        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // undeploy the bean from host pointed by URI
        undeployStateless(0);

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        boolean gotNoSuchEJBException;
        Result<String> echoResult ;
        try {
            echoResult = proxy.echo(message);
            gotNoSuchEJBException = false;
        } catch(NoSuchEJBException e) {
            gotNoSuchEJBException = true;
            echoResult = null;
        }
        Assert.assertEquals("NoSuchEJBException was expected", true, gotNoSuchEJBException);

        // redeploy the bean on the server
        deployStateless(0);
    }

    /**
     * Test SFSB default proxy initialization (legacy behaviour)
     *
     * scenario:
     *   invoked bean available on one target, node2
     * expected result:
     *   SFSB session is created on that target, strong affinity = Node(node2), weakAffinity = NONE
     */
    @Test
    public void testSFSBDefaultProxyInitialization() {
        logger.info("=== Testing SFSB default proxy initialization ===");

        // undeploy the bean from host node1
        undeployStateful(0);

        Affinity expectedStrongAffinity = new NodeAffinity("node2");
        // this is what should give, but what follows is equivalent
        // Affinity expectedWeakAffinity = Affinity.NONE;
        Affinity expectedWeakAffinity = new NodeAffinity("node2");

        // create a proxy for SFSB, with a default value for strong affinity
        final StatelessEJBLocator<Echo> statefulEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, Affinity.NONE);
        Echo proxy ;
        try {
            proxy = EJBClient.createSessionProxy(statefulEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);

        // check affinity assignments
        Affinity strongAffinity = EJBClient.getStrongAffinity(proxy);
        Affinity weakAffinity = EJBClient.getWeakAffinity(proxy);
        logger.info("strong affinity = " + strongAffinity + ", weak affinity = " + weakAffinity);
        Assert.assertEquals("Expected strong affinity != " + expectedStrongAffinity, expectedStrongAffinity, strongAffinity);
        Assert.assertEquals("Expected weak affinity != " + expectedWeakAffinity, expectedWeakAffinity, weakAffinity);

        // redeploy the bean on the server
        deployStateful(0);
    }

    /**
     * Test SFSB programmatic proxy initialization
     *
     * scenario:
     *   invoked bean available on two targets, node1, node2
     *   strong affinity is set to point to one of the two targets, node2
     * expected result:
     *   SFSB session is created on that target, strong affinity = Node(node2), weakAffinity = NONE
     */
    @Test
    public void testSFSBProgrammaticProxyInitialization() {
        logger.info("=== Testing SFSB programmatic proxy initialization ===");

        Affinity expectedStrongAffinity = new NodeAffinity("node2");
        // Affinity expectedWeakAffinity = Affinity.NONE;
        // TODO: fix this affinity assignment
        Affinity expectedWeakAffinity = new NodeAffinity("node2");

        // create a proxy for SFSB, with a NodeAffinity which points to node2
        final StatelessEJBLocator<Echo> statefulEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, expectedStrongAffinity);
        Echo proxy ;
        try {
            proxy = EJBClient.createSessionProxy(statefulEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);

        // check affinity assignments
        Affinity strongAffinity = EJBClient.getStrongAffinity(proxy);
        Affinity weakAffinity = EJBClient.getWeakAffinity(proxy);
        logger.info("strong affinity = " + strongAffinity + ", weak affinity = " + weakAffinity);
        Assert.assertEquals("Expected strong affinity != " + expectedStrongAffinity, expectedStrongAffinity, strongAffinity);
        Assert.assertEquals("Expected weak affinity != " + expectedWeakAffinity, expectedWeakAffinity, weakAffinity);
    }

    /**
     * Test SFSB invocation
     *
     * scenario:
     *   invoked bean available on two targets, node1, node2
     *   strong affinity is set to point to one of the two targets, node2
     * expected result:
     *   the invocation occurs on node2
     */
    @Test
    public void testSFSBInvocationation() {
        logger.info("=== Testing SFSB invocation ===");

        Affinity expectedStrongAffinity = new NodeAffinity("node2");
        // Affinity expectedWeakAffinity = Affinity.NONE;

        // create a proxy for SFSB, with a NodeAffinity which points to node2
        final StatelessEJBLocator<Echo> statefulEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, expectedStrongAffinity);
        Echo proxy ;
        try {
            proxy = EJBClient.createSessionProxy(statefulEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        final Result<String> echoResult = proxy.echo(message);
        logger.info("SFSB invocation had target " + echoResult.getNode());

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertEquals("Got an unexpected node for invocation target", echoResult.getNode(), SERVER2_NAME);
    }

    /**
     * Test SFSB invocation with failover
     *
     * NOTE: the failover feature only applies to clustered deployments
     *
     * scenario:
     *   invoked bean available on one target, node2
     *   strong affinity is set to point to one of the two targets, node1
     *   invoke on the proxy
     *   undeploy the bean from node1
     *   invoke on the proxy
     * expected result:
     *   the first invocation occurs on node1
     *   the second invocation returns NoSuchEJBException
     */
    @Test
    public void testSFSBInvocationationNoFailover() {
        logger.info("=== Testing SFSB invocation with failover ===");

        Affinity expectedStrongAffinity = new NodeAffinity("node1");
        // Affinity expectedWeakAffinity = Affinity.NONE;

        // create a proxy for SFSB, with a NodeAffinity which points to node2
        final StatelessEJBLocator<Echo> statefulEJBLocator = StatelessEJBLocator.create(Echo.class, STATEFUL_IDENTIFIER, expectedStrongAffinity);
        Echo proxy ;
        try {
            proxy = EJBClient.createSessionProxy(statefulEJBLocator);
        } catch (Exception e) {
            Assert.fail("Unexpected exception when creating session proxy: e = " + e.getMessage());
            proxy = null;
        }
        Assert.assertNotNull("Received a null proxy", proxy);

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        Result<String> echoResult = proxy.echo(message);

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertEquals("Got an unexpected node for invocation target", echoResult.getNode(), SERVER1_NAME);

        // undeploy the bean on the server - this will trigger failover
        undeployStateful(0);

        // invoke on the proxy
        logger.info("Invoking on proxy...again");
        boolean gotNoSuchEJBException;
        try {
            echoResult = proxy.echo(message);
            gotNoSuchEJBException = false;
        } catch(NoSuchEJBException e) {
            gotNoSuchEJBException = true;
            echoResult = null;
        }
        Assert.assertEquals("NoSuchEJBException was expected", true, gotNoSuchEJBException);

        // redeploy the bean on the server
        deployStateful(0);
    }

    /**
     * Test SLSB invocation
     *
     * scenario:
     *   invoked bean available on two targets, node1, node2
     *   strong affinity is set to Affinity.NONE
     * expected result:
     *   the invocation occurs on either node1 or node2
     */
    @Test
    public void testSLSBInvocation() {
        logger.info("=== Testing SLSB invocation ===");

        Affinity expectedStrongAffinity = Affinity.NONE;

        // create a proxy for SLSB
        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, expectedStrongAffinity);
        Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        final Result<String> echoResult = proxy.echo(message);
        logger.info("SLSB invocation had target " + echoResult.getNode());

        // check the message contents and the target
        Assert.assertEquals("Got an unexpected echo", echoResult.getValue(), message);
        Assert.assertTrue("Got an unexpected node for invocation target", echoResult.getNode().equals(SERVER1_NAME) || echoResult.getNode().equals(SERVER2_NAME));
    }

    /**
     * Tests that when a EJB implementation throws an exception, the exception stacktrace
     * received by the client contains the necessary stacktrace elements of the caller/client
     * invocation.
     */
    @Test
    public void testSLSBInvocationForExceptionStackTrace() {
        Affinity expectedStrongAffinity = Affinity.NONE;
        // create a proxy for SLSB
        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, expectedStrongAffinity);
        Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);
        // invoke on the proxy
        final String message = "request to throw IllegalArgumentException";
        try {
            final Result<String> echoResult = proxy.echo(message);
            Assert.fail("Invocation was expected to throw an exception, but didn't");
        } catch (Exception e) {
            final StackTraceElement callerStackTrace = Thread.currentThread().getStackTrace()[1];
            // make sure the stacktrace "contains" the caller/client stacktrace
            for (final StackTraceElement stackTraceElement : e.getStackTrace()) {
                if (callerStackTrace.getClassName().equals(stackTraceElement.getClassName())
                        && callerStackTrace.getFileName().equals(stackTraceElement.getFileName())
                        && callerStackTrace.getMethodName().equals(stackTraceElement.getMethodName())) {
                    // the stacktrace has the necessary and expected caller reference
                    return;
                }
            }
            Assert.fail("Exception stacktrace is missing caller side details in the stacktrace");
        }
    }

    /**
     * Test SLSB invocation with failed node
     *
     * scenario:
     *   invoked bean available on two nodes, node1, node2
     *   strong affinity is set to NONE
     *   invoke on the proxy
     *   undeploy the bean from node1
     *   invoke on the proxy
     * expected result:
     *   the first invocation occurs on either node1 or node2
     *   the second invocation occurs on the remaining node2
     */
    @Test
    public void testSLSBInvocationWithFailedNode() {
        logger.info("=== Testing SLSB invocation with failed node ===");

        Affinity expectedStrongAffinity = Affinity.NONE;

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
        // undeploy server
        for (int i = 0; i < 2; i++) {
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
