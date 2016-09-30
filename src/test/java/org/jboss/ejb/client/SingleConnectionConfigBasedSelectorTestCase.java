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
import java.util.Properties;

/**
 * Note that this testcase *must* be run in a new JVM instance so that the {@link ConfigBasedEJBClientContextSelector}
 * is initialized with the correct set of properties that are set in the {@link #beforeClass()} of this testcase. We
 * use the forkMode=always of the Maven surefire plugin to ensure this behaviour (see the pom.xml of this project).
 *
 * @author Jaikiran Pai
 */
public class SingleConnectionConfigBasedSelectorTestCase {

    private static final Logger logger = Logger.getLogger(SingleConnectionConfigBasedSelectorTestCase.class);

    private static DummyServer server;
    private static final String SERVER_ENDPOINT_NAME = "test-endpoint-two";

    @BeforeClass
    public static void beforeClass() throws Exception {
        // Setup the -Djboss.ejb.client.properties system property which points to a
        // EJB client configuration file with one remoting connection configured
        final String fileName = "one-connection-ejb-client-config.properties";
        final URL url = SingleConnectionConfigBasedSelectorTestCase.class.getClassLoader().getResource(fileName);
        if (url == null) {
            throw new IllegalStateException("Missing file " + fileName);
        }
        System.setProperty("jboss.ejb.client.properties.file.path", url.getPath());

        server = new DummyServer("localhost", 7999);
        server.start();
        server.register("dummy-app", "dummy-module", "", EchoBean.class.getSimpleName(), new EchoBean());
    }


    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testEJBClientContextAvailability() throws Exception {
        final Properties properties = EJBClientPropertiesLoader.loadEJBClientProperties();
        final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(properties);
        final ConfigBasedEJBClientContextSelector configBasedEJBClientContextSelector = new ConfigBasedEJBClientContextSelector(ejbClientConfiguration);
        final EJBClientContext ejbClientContext = configBasedEJBClientContextSelector.getCurrent();
        Assert.assertNotNull("No client context found ", ejbClientContext);
    }

    @Test
    public void testRemotingEJBReceiver() throws Exception {
        final Properties properties = EJBClientPropertiesLoader.loadEJBClientProperties();
        final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(properties);
        final ConfigBasedEJBClientContextSelector configBasedEJBClientContextSelector = new ConfigBasedEJBClientContextSelector(ejbClientConfiguration);

        final EJBClientContext ejbClientContext = configBasedEJBClientContextSelector.getCurrent();
        logger.info("Found EJB client context " + ejbClientContext);
        Assert.assertNotNull("No client context found " + ejbClientContext);
        final Collection<EJBReceiver> ejbReceivers = ejbClientContext.getEJBReceivers("dummy-app", "dummy-module", "");
        Assert.assertNotNull("No EJB receivers found ", ejbReceivers);
        Assert.assertEquals("Unexpected number of EJB receivers", 1, ejbReceivers.size());
        final EJBReceiver receiver = ejbReceivers.iterator().next();
        Assert.assertEquals("Unexpected EJB receiver type", RemotingConnectionEJBReceiver.class, receiver.getClass());
        Assert.assertEquals("Unexpected EJB receiver", SERVER_ENDPOINT_NAME, receiver.getNodeName());
    }

}
