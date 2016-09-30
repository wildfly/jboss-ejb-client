/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client;

import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Note that this testcase *must* be run in a new JVM instance so that the {@link ConfigBasedEJBClientContextSelector}
 * is initialized with the correct set of properties that are set in the {@link #beforeClass()} of this testcase. We
 * use the forkMode=always of the Maven surefire plugin to ensure this behaviour (see the pom.xml of this project).
 *
 * @author Jaikiran Pai
 */
public class MultipleConnectionsConfigBasedSelectorTestCase {

    private static final Logger logger = Logger.getLogger(MultipleConnectionsConfigBasedSelectorTestCase.class);
    private static DummyServer serverOne;
    private static DummyServer serverTwo;
    private static final String SERVER_ONE_ENDPOINT_NAME = "test-endpoint-one";
    private static final String SERVER_TWO_ENDPOINT_NAME = "test-endpoint-two";

    @BeforeClass
    public static void beforeClass() throws Exception {
        // Setup the -Djboss.ejb.client.properties system property which points to a
        // EJB client configuration file with multiple remoting connections configured
        final String fileName = "multiple-connections-ejb-client-config.properties";
        final URL url = SingleConnectionConfigBasedSelectorTestCase.class.getClassLoader().getResource(fileName);
        if (url == null) {
            throw new IllegalStateException("Missing file " + fileName);
        }
        System.setProperty("jboss.ejb.client.properties.file.path", url.getPath());

        serverOne = new DummyServer("localhost", 6999);
        serverOne.start();
        serverOne.register("dummy-app-one", "dummy-module-one", "", EchoBean.class.getSimpleName(), new EchoBean());
        serverOne.register("dummy-app-on-both-servers", "dummy-module-on-both-servers", "", EchoBean.class.getSimpleName(), new EchoBean());

        serverTwo = new DummyServer("localhost", 7999);
        serverTwo.start();
        serverTwo.register("dummy-app-two", "dummy-module-two", "", EchoBean.class.getSimpleName(), new EchoBean());
        serverTwo.register("dummy-app-on-both-servers", "dummy-module-on-both-servers", "", EchoBean.class.getSimpleName(), new EchoBean());
    }


    @AfterClass
    public static void afterClass() throws Exception {
        if (serverOne != null) {
            serverOne.stop();
        }
        if (serverTwo != null) {
            serverTwo.stop();
        }
    }

    @Test
    public void testRemotingEJBReceivers() throws Exception {
        final Properties properties = EJBClientPropertiesLoader.loadEJBClientProperties();
        final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(properties);
        final ConfigBasedEJBClientContextSelector configBasedEJBClientContextSelector = new ConfigBasedEJBClientContextSelector(ejbClientConfiguration);

        final EJBClientContext ejbClientContext = configBasedEJBClientContextSelector.getCurrent();
        Assert.assertNotNull("No client context found " + ejbClientContext);
        // find the receiver for dummy-app-one
        // should find server one receiver
        final Collection<EJBReceiver> appOneReceivers = ejbClientContext.getEJBReceivers("dummy-app-one", "dummy-module-one", "");
        Assert.assertNotNull("No EJB receivers found ", appOneReceivers);
        Assert.assertEquals("Unexpected number of EJB receivers", 1, appOneReceivers.size());
        final EJBReceiver serverOneReceiver = appOneReceivers.iterator().next();
        Assert.assertEquals("Unexpected EJB receiver type", RemotingConnectionEJBReceiver.class, serverOneReceiver.getClass());
        Assert.assertEquals("Unexpected EJB receiver", SERVER_ONE_ENDPOINT_NAME, serverOneReceiver.getNodeName());

        // find the receiver for dummy-app-two
        // should find server two receiver
        final Collection<EJBReceiver> appTwoReceivers = ejbClientContext.getEJBReceivers("dummy-app-two", "dummy-module-two", "");
        Assert.assertNotNull("No EJB receivers found ", appTwoReceivers);
        Assert.assertEquals("Unexpected number of EJB receivers", 1, appTwoReceivers.size());
        final EJBReceiver serverTwoReceiver = appTwoReceivers.iterator().next();
        Assert.assertEquals("Unexpected EJB receiver type", RemotingConnectionEJBReceiver.class, serverTwoReceiver.getClass());
        Assert.assertEquals("Unexpected EJB receiver", SERVER_TWO_ENDPOINT_NAME, serverTwoReceiver.getNodeName());

        // find the receivers for dummy-app-on-both-servers
        // should find both the server receivers
        final Collection<EJBReceiver> bothServerReceivers = ejbClientContext.getEJBReceivers("dummy-app-on-both-servers", "dummy-module-on-both-servers", "");
        Assert.assertNotNull("No EJB receivers found ", bothServerReceivers);
        Assert.assertEquals("Unexpected number of EJB receivers", 2, bothServerReceivers.size());
        final Set<String> receiverNodeNames = new HashSet<String>();
        for (final EJBReceiver ejbReceiver : bothServerReceivers) {
            Assert.assertEquals("Unexpected EJB receiver type", RemotingConnectionEJBReceiver.class, ejbReceiver.getClass());
            receiverNodeNames.add(ejbReceiver.getNodeName());
        }
        Assert.assertEquals("Unexpected number of receiver node names", 2, receiverNodeNames.size());
        Assert.assertTrue("Receiver for nodename " + SERVER_ONE_ENDPOINT_NAME + " not found", receiverNodeNames.contains(SERVER_ONE_ENDPOINT_NAME));
        Assert.assertTrue("Receiver for nodename " + SERVER_TWO_ENDPOINT_NAME + " not found", receiverNodeNames.contains(SERVER_TWO_ENDPOINT_NAME));

    }

}
