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

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.PropertiesBasedEJBClientConfiguration;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Tests that invocation on stateless beans fails over without throwing any client side errors
 * when the deployment on one of the servers, within the EJB client context, is undeployed but the undeployment
 * notifications hasn't yet reached the client
 *
 * @author Jaikiran Pai
 * @see https://issues.jboss.org/browse/EJBCLIENT-32
 */
public class StatelessApplicationUndeploymentFailoverTestCase {

    private static final Logger logger = Logger.getLogger(StatelessApplicationUndeploymentFailoverTestCase.class);

    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private static final String SERVER_ONE_NAME = "test-endpoint-one";
    private static final String SERVER_TWO_NAME = "test-endpoint-two";

    private DummyServer serverOne;
    private DummyServer serverTwo;

    private boolean serverOneStarted;
    private boolean serverTwoStarted;

    private ContextSelector<EJBClientContext> previousSelector;

    @Before
    public void beforeTest() throws IOException {
        // start a couple of servers and deploy the beans into them
        // TODO: Come back to this once we enable a way to test this project against IPv6
        serverOne = new DummyServer("localhost", 6999);
        serverTwo = new DummyServer("localhost", 7999);

        serverOne.start();
        serverOneStarted = true;

        serverTwo.start();
        serverTwoStarted = true;

        final EchoBean echoBeanOnServerOne = new EchoBean(SERVER_ONE_NAME);
        final EchoBean echoBeanOnServerTwo = new EchoBean(SERVER_TWO_NAME);
        // deploy on both servers
        serverOne.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), echoBeanOnServerOne);
        serverTwo.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), echoBeanOnServerTwo);

        // setup EJB client context with both the servers
        final String clientPropsName = "stateless-invocation-retry-jboss-ejb-client.properties";
        final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(clientPropsName);
        if (inputStream == null) {
            throw new RuntimeException("Could not find EJB client configuration properties at " + clientPropsName + " using classloader " + this.getClass().getClassLoader());
        }
        final Properties clientProps = new Properties();
        clientProps.load(inputStream);
        final EJBClientConfiguration clientConfiguration = new PropertiesBasedEJBClientConfiguration(clientProps);
        final ConfigBasedEJBClientContextSelector selector = new ConfigBasedEJBClientContextSelector(clientConfiguration);
        this.previousSelector = EJBClientContext.setSelector(selector);
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
     * Then undeploys the deployment from a server which previously handled a invocation request. The undeployment
     * process on the server does *not* notify the client of the undeployment and let's the client think that the
     * deployment is still available on the server. The test continues to invoke on the proxy and the EJB client API
     * implementation is expected to be smart enough to retry the invocation(s) on a different node after it receives
     * a {@link javax.ejb.NoSuchEJBException} from the server which no longer has the deployment
     *
     * @throws Exception
     */
    @Test
    public void testStatelessInvocationFailoverDueToUndeployment() throws Exception {
        // first try some invocations which should succeed
        // create a proxy for invocation
        final StatelessEJBLocator<Echo> statelessEJBLocator = new StatelessEJBLocator<Echo>(Echo.class, APP_NAME, MODULE_NAME, EchoBean.class.getSimpleName(), "");
        final Echo proxy = EJBClient.createProxy(statelessEJBLocator);
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
}
