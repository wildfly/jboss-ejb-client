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

import java.util.Collection;
import java.util.List;

/**
 * Tests basic invocation of a bean deployed on a single server node.
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class ClusteredInvocationTestCase {

    private static final Logger logger = Logger.getLogger(ClusteredInvocationTestCase.class);
    private static final String PROPERTIES_FILE = "clustered-jboss-ejb-client.properties";

    // servers
    private static final String SERVER1_NAME = "node1";
    private static final String SERVER2_NAME = "node2";

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

    /**
     * Do any general setup here
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // trigger the static init of the correct properties file - this also depends on running in forkMode=always
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(SimpleInvocationTestCase.class.getClassLoader(), PROPERTIES_FILE);
        JBossEJBProperties.getContextManager().setGlobalDefault(ejbProperties);
    }

    /**
     * Do any test specific setup here
     */
    @Before
    public void beforeTest() throws Exception {

        // start a server
        servers[0] = new DummyServer("localhost", 6999, serverNames[0]);
        servers[0].start();
        serversStarted[0] = true;
        logger.info("Started server " + serverNames[0]);

        // start a server
        servers[1] = new DummyServer("localhost", 7099, serverNames[1]);
        servers[1].start();
        serversStarted[1] = true;
        logger.info("Started server " + serverNames[1]);

        // deploy modules
        servers[0].register(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getSimpleName(), new EchoBean());
        logger.info("Registered module on server " + servers[0]);

        servers[1].register(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getSimpleName(), new EchoBean());
        logger.info("Registered module on server " + servers[1]);

        // define clusters
        servers[0].addCluster(CLUSTER);
        logger.info("Added node to cluster " + CLUSTER_NAME + ": server " + servers[1]);
        servers[1].addCluster(CLUSTER);
        logger.info("Added node to cluster " +  CLUSTER_NAME +":  server " + servers[1]);
    }

    @Test
    public void testConfiguredConnections() {
        EJBClientContext context = EJBClientContext.getCurrent();
        List<EJBClientConnection> connections = context.getConfiguredConnections();

        Assert.assertEquals("Number of configured connections for this context is incorrect", 2, connections.size());
        for (EJBClientConnection connection : connections) {
            logger.info("found connection: destination = " + connection.getDestination() + ", forDiscovery = " + connection.isForDiscovery());
        }

        Collection<EJBClientCluster> clusters = context.getInitialConfiguredClusters();
        for (EJBClientCluster cluster: clusters) {
            logger.info("found cluster: name = " + cluster.getName());
        }
    }

    /**
     * Test a basic invocation on clustered SLSB
     */
    @Test
    public void testClusteredSLSBInvocation() {
        logger.info("Testing invocation on SLSB proxy with ClusterAffinity");

        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, Echo.class.getSimpleName(), DISTINCT_NAME);
        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);

        EJBClient.setStrongAffinity(proxy, new ClusterAffinity("ejb"));
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        logger.info("Invoking on proxy...");
        // invoke on the proxy (use a ClusterAffinity for now)
        final String message = "hello!";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Got an unexpected echo", echo, message);
    }

    /**
     * Test a basic invocation on clustered SFSB
     */
    @Test
    public void testClusteredSFSBInvocation() throws Exception {
        logger.info("Testing invocation on SFSB proxy with ClusterAffinity");

        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, Echo.class.getSimpleName(), DISTINCT_NAME);
        StatefulEJBLocator<Echo> statefulEJBLocator = null;
        try {
            statefulEJBLocator = EJBClient.createSession(statelessEJBLocator);
        } catch(Exception e) {
            logger.warn("Got exception", e);
            throw e;
        }

        final Echo proxy = EJBClient.createProxy(statefulEJBLocator);

        EJBClient.setStrongAffinity(proxy, new ClusterAffinity("ejb"));
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        logger.info("Invoking on proxy...");
        // invoke on the proxy (use a ClusterAffinity for now)
        final String message = "hello!";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Got an unexpected echo", echo, message);
    }

    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {
        servers[0].unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        logger.info("Unregistered module from " + serverNames[0]);

        servers[1].unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        logger.info("Unregistered module from " + serverNames[1]);

        servers[0].removeCluster(CLUSTER_NAME);
        servers[1].removeCluster(CLUSTER_NAME);

        if (serversStarted[0]) {
            try {
                this.servers[0].stop();
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            }
        }
        logger.info("Stopped server " + serverNames[0]);

        if (serversStarted[1]) {
            try {
                this.servers[1].stop();
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            }
        }
        logger.info("Stopped server " + serverNames[1]);
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
    }

}
