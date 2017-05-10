package org.jboss.ejb.client.test;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Tests basic invocation of a bean deployed on a single server node.
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class SimpleInvocationTestCase {

    private static final Logger logger = Logger.getLogger(SimpleInvocationTestCase.class);

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
     * Test a basic invocation
     */
//    @Ignore
    @Test
    public void testInvocationWithURIAffinity() {
        logger.info("Testing invocation on proxy with URIAffinity");

        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, Echo.class.getSimpleName(), DISTINCT_NAME);
        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        URI uri = null;
        try {
            uri = new URI("remote", null,"localhost", 6999, null, null,null);
        } catch(URISyntaxException use) {
            //
        }
        EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(uri));
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        System.out.println("Invoking on proxy...");
        // invoke on the proxy (use a URIAffinity for now)
        final String message = "hello!";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Got an unexpected echo", echo, message);
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
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
    }

}
