/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.ejb.client.test.invocation.retry;

import org.jboss.ejb.client.ClusterContext;
import org.jboss.ejb.client.ClusterNodeManager;
import org.jboss.ejb.client.ConstantContextSelector;
import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver;
import org.jboss.ejb.client.test.common.AnonymousCallbackHandler;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.jboss.ejb.client.remoting.IoFutureHelper.*;

/**
 * Tests that invocation on stateful beans which are deployed in a cluster context, fails over without throwing any
 * client side exceptions, when deployment on one of the nodes is undeployed but the client hasn't yet received the
 * undeployment notification
 *
 * @author Jaikiran Pai
 * @see https://issues.jboss.org/browse/EJBCLIENT-32
 */
public class StatefulClusteredApplicationUndeploymentFailoverTestCase {
    private static final Logger logger = Logger.getLogger(StatefulClusteredApplicationUndeploymentFailoverTestCase.class);

    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private static final String SERVER_ONE_NAME = "server-one";
    private static final String SERVER_TWO_NAME = "server-two";

    private DummyServer serverOne;
    private DummyServer serverTwo;

    private boolean serverOneStarted;
    private boolean serverTwoStarted;

    private ContextSelector<EJBClientContext> previousSelector;

    @Before
    public void beforeTest() throws Exception {
        // start a couple of servers and deploy the beans into them
        // TODO: Come back to this once we enable a way to test this project against IPv6
        serverOne = new DummyServer("localhost", 6999, SERVER_ONE_NAME);
        serverTwo = new DummyServer("localhost", 7999, SERVER_TWO_NAME);

        serverOne.start();
        serverOneStarted = true;

        serverTwo.start();
        serverTwoStarted = true;

        final EchoBean echoBeanOnServerOne = new EchoBean(SERVER_ONE_NAME);
        final EchoBean echoBeanOnServerTwo = new EchoBean(SERVER_TWO_NAME);
        // deploy on both servers
        serverOne.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), echoBeanOnServerOne);
        serverTwo.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), echoBeanOnServerTwo);

        // setup EJB client context with server one EJB receiver
        final EJBReceiver serverOneReceiver = this.getServerOneReceiver();
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        ejbClientContext.registerEJBReceiver(serverOneReceiver);

        // add a cluster context to the EJB client context and add both server one and server two to the
        // cluster context
        final EJBReceiver serverTwoReceiver = this.getServerTwoReceiver();
        // create the cluster context
        final ClusterContext clusterContext = ejbClientContext.getOrCreateClusterContext(serverTwo.getClusterName());
        // add server two to the cluster context
        clusterContext.addClusterNode(SERVER_TWO_NAME, new ClusterNodeManager() {
            @Override
            public String getNodeName() {
                return SERVER_TWO_NAME;
            }

            @Override
            public EJBReceiver getEJBReceiver() {
                return serverTwoReceiver;
            }
        });
        // add server one to the cluster context
        clusterContext.addClusterNode(SERVER_ONE_NAME, new ClusterNodeManager() {
            @Override
            public String getNodeName() {
                return SERVER_ONE_NAME;
            }

            @Override
            public EJBReceiver getEJBReceiver() {
                return serverOneReceiver;
            }
        });
        // setup the context selector
        this.previousSelector = EJBClientContext.setSelector(new ConstantContextSelector<EJBClientContext>(ejbClientContext));

    }

    @After
    public void afterTest() throws IOException {
        if (serverOneStarted) {
            try {
                this.serverOne.stop();
            } catch (Exception e) {
                logger.info("Could not stop server one", e);
            }
        }
        if (serverTwoStarted) {
            try {
                this.serverTwo.stop();
            } catch (Exception e) {
                logger.info("Could not stop server two", e);
            }
        }
        if (this.previousSelector != null) {
            EJBClientContext.setSelector(previousSelector);
        }
    }

    /**
     * Starts off with 2 servers having different implementations of the same bean and invokes on the bean interface.
     * Then un-deploys the deployment from a server which previously handled a invocation request. The un-deployment
     * process on the server does *not* notify the client of the un-deployment and let's the client think that the
     * deployment is still available on the server. The test continues to invoke on the proxy and the EJB client API
     * implementation is expected to be smart enough to retry the invocation(s) on a different node after it receives
     * a {@link javax.ejb.NoSuchEJBException} from the server which no longer has the deployment
     *
     * @throws Exception
     */
    @Test
    public void testStatefulInvocationFailoverDueToUndeployment() throws Exception {
        // first try some invocations which should succeed
        // create a proxy for invocation
        final StatefulEJBLocator<Echo> statefulEJBLocator = EJBClient.createSession(Echo.class, APP_NAME, MODULE_NAME, EchoBean.class.getSimpleName(), DISTINCT_NAME);
        final Echo proxy = EJBClient.createProxy(statefulEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);

        // invoke
        final String msg = "foo!";
        for (int i = 0; i < 5; i++) {
            final String echo = proxy.echo(msg);
            Assert.assertEquals("Unexpected echo", msg, echo);
        }

        final String serverNameBeforeUndeployment = proxy.getServerName();
        Assert.assertNotNull("Server name returned was null", serverNameBeforeUndeployment);
        Assert.assertTrue("Unexpected server name returned", SERVER_ONE_NAME.equals(serverNameBeforeUndeployment) || SERVER_TWO_NAME.equals(serverNameBeforeUndeployment));

        // now undeploy the deployment from the server on which this previous invocation happened.
        // Note that we don't intentionally send a undeploy notification to the client and let it think that
        // the server still has the deployment
        final String expectedServerNameAfterUndeployment;
        if (SERVER_ONE_NAME.equals(serverNameBeforeUndeployment)) {
            this.serverOne.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), false);
            expectedServerNameAfterUndeployment = SERVER_TWO_NAME;
        } else {
            this.serverTwo.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), false);
            expectedServerNameAfterUndeployment = SERVER_ONE_NAME;
        }

        // now invoke
        final String serverNameAfterUndeployment = proxy.getServerName();
        Assert.assertNotNull("Server name was null after undeployment", serverNameAfterUndeployment);
        Assert.assertFalse("Server on which deployment was undeployed was chosen", serverNameAfterUndeployment.equals(serverNameBeforeUndeployment));
        Assert.assertEquals("Unexpected server name after undeployment", expectedServerNameAfterUndeployment, serverNameAfterUndeployment);

        // do a few more invocations
        for (int i = 0; i < 5; i++) {
            final String echo = proxy.echo(msg);
            Assert.assertEquals("Unexpected echo after undeployment", msg, echo);
            final String serverName = proxy.getServerName();
            Assert.assertEquals("Unexpected server name after undeployment", expectedServerNameAfterUndeployment, serverName);
        }

        // now let the server send a undeploy notification to the client so that it actually knows that the deployment
        // is no longer present on that server
        if (SERVER_ONE_NAME.equals(serverNameBeforeUndeployment)) {
            this.serverOne.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), true);
        } else {
            this.serverTwo.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), true);
        }

        // do a few more invocations
        for (int i = 0; i < 5; i++) {
            final String echo = proxy.echo(msg);
            Assert.assertEquals("Unexpected echo after undeployment", msg, echo);
            final String serverName = proxy.getServerName();
            Assert.assertEquals("Unexpected server name after undeployment", expectedServerNameAfterUndeployment, serverName);
        }

    }

    private EJBReceiver getServerOneReceiver() throws IOException, URISyntaxException {
        final Endpoint endpoint = Remoting.createEndpoint("endpoint", OptionMap.EMPTY);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        // open a connection
        final IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://localhost:6999"), OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE), new AnonymousCallbackHandler());
        final Connection connection = get(futureConnection, 5, TimeUnit.SECONDS);
        return new RemotingConnectionEJBReceiver(connection, null, OptionMap.EMPTY, "remote");
    }

    private EJBReceiver getServerTwoReceiver() throws IOException, URISyntaxException {
        final Endpoint endpoint = Remoting.createEndpoint("endpoint", OptionMap.EMPTY);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        // open a connection
        final IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://localhost:7999"), OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE), new AnonymousCallbackHandler());
        final Connection connection = get(futureConnection, 5, TimeUnit.SECONDS);
        return new RemotingConnectionEJBReceiver(connection, null, OptionMap.EMPTY, "remote");
    }

}
