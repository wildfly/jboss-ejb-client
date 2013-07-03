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

package org.jboss.ejb.client.test.interceptor;

import org.jboss.ejb.client.ClasspathConfigBasedSelectorTestCase;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBClientInterceptor;
import org.jboss.ejb.client.PropertiesBasedEJBClientConfiguration;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.EchoRemote;
import org.jboss.ejb.client.test.common.ResourceSwitchingClassLoader;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;

/**
 * @author Jaikiran Pai
 */
public class ClasspathEJBClientInterceptorsTestCase {
    private static final Logger logger = Logger.getLogger(ClasspathConfigBasedSelectorTestCase.class);

    private static DummyServer server;
    private static final String SERVER_ENDPOINT_NAME = "classpath-ejb-client-interceptors-test-case-endpoint";

    // setup a resource switching classloader
    final ClassLoader resourceSwitchingCL = new ResourceSwitchingClassLoader("META-INF/services/" + EJBClientInterceptor.class.getName(), "ejb-client-interceptors." + EJBClientInterceptor.class.getName());

    @BeforeClass
    public static void beforeClass() throws Exception {
        server = new DummyServer("localhost", 7999, SERVER_ENDPOINT_NAME);
        server.start();
        server.register("dummy-app", "dummy-module", "", EchoBean.class.getSimpleName(), new EchoBean());
    }


    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Tests that client interceptors added explicitly to the EJBClientContext and also found by the service loader
     * mechanism in the client classpath are invoked in the right order during an invocation on the EJB
     *
     * @throws Exception
     */
    @Test
    public void testEJBClientInterceptorsOnClasspath() throws Exception {
        final Properties properties = this.getEJBClientConfigurationProperties();
        final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(properties);
        final ConfigBasedEJBClientContextSelector configBasedEJBClientContextSelector = new ConfigBasedEJBClientContextSelector(ejbClientConfiguration, this.resourceSwitchingCL);
        final EJBClientContext previousClientContext = EJBClientContext.getCurrent();
        try {
            // switch to our test EJB client context
            final EJBClientContext ejbClientContext = configBasedEJBClientContextSelector.getCurrent();
            // add an explicit interceptor
            ejbClientContext.registerInterceptor(99999, new InterceptorThree());
            EJBClientContext.setConstantContext(ejbClientContext);
            final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, "dummy-app", "dummy-module", EchoBean.class.getSimpleName(), "");
            final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
            final String message = "foo";
            // The explicitly added client interceptor should be invoked first and then all the interceptors in the client
            // classpath are supposed to be invoked in the order they are found in classpath
            final String expectedEcho = InterceptorThree.class.getName() + " " + InterceptorTwo.class.getName() + " " + InterceptorOne.class.getName() + " " + message;
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message which was expected to be intercepted", expectedEcho, echo);
        } finally {
            if (previousClientContext != null) {
                EJBClientContext.setConstantContext(previousClientContext);
            }
        }
        final EJBClientContext ejbClientContext = configBasedEJBClientContextSelector.getCurrent();
        Assert.assertNotNull("No client context found", ejbClientContext);

    }

    /**
     * Returns the properties that can be passed on to the constructor of the {@link javax.naming.InitialContext}
     * to create scoped EJB client contexts
     *
     * @return
     * @throws Exception
     */
    private Properties getEJBClientConfigurationProperties() throws Exception {
        final Properties ejbClientContextProps = new Properties();
        final String connectionName = "foo-bar-connection";
        ejbClientContextProps.put("remote.connectionprovider.create.options.org.xnio.Options.SSL_ENABLED", "false");
        // add a property which lists the connections that we are configuring. In
        // this example, we are just configuring a single connection named "foo-bar-connection"
        ejbClientContextProps.put("remote.connections", connectionName);
        // add a property which points to the host server of the "foo-bar-connection"
        ejbClientContextProps.put("remote.connection." + connectionName + ".host", "localhost");
        // add a property which points to the port on which the server is listening for EJB invocations
        ejbClientContextProps.put("remote.connection." + connectionName + ".port", "7999");
        ejbClientContextProps.put("remote.connection." + connectionName + ".protocol", "remote");
        // since we are connecting to a dummy server, we use anonymous user
        ejbClientContextProps.put("remote.connection." + connectionName + ".connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS", "false");

        return ejbClientContextProps;

    }
}
