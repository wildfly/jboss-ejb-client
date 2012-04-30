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

package org.jboss.ejb.client.ipv6;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.PropertiesBasedEJBClientConfiguration;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.ejb.client.test.client.EchoBean;
import org.jboss.ejb.client.test.client.EchoRemote;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Basic testcase for testing IPv6 connection handling within the EJB client project
 *
 * @author Jaikiran Pai
 */
@RunWith(Parameterized.class)
@Ignore("Fails on mac")
public class IPv6TestCase {

    private static final Logger logger = Logger.getLogger(IPv6TestCase.class);

    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private final DummyServer server;
    private ContextSelector<EJBClientContext> previousSelector;

    private final String destinationHost;
    private final int destinationPort;

    /**
     * Looks for any IPv6 addresses on the system and returns those to be used as parameters for the testcase
     *
     * @return
     * @throws Exception
     */
    @Parameterized.Parameters
    public static Collection<Object[]> getIPv6Addresses() throws Exception {
        final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        if (networkInterfaces == null) {
            return Collections.emptyList();
        }
        final List<String> ipv6Addresses = new ArrayList<String>();
        while (networkInterfaces.hasMoreElements()) {
            final NetworkInterface networkInterface = networkInterfaces.nextElement();
            if (!networkInterface.isUp()) {
                continue;
            }
            final Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            if (addresses == null) {
                continue;
            }
            while (addresses.hasMoreElements()) {
                final InetAddress address = addresses.nextElement();
                if (address instanceof Inet6Address) {
                    ipv6Addresses.add(address.getHostAddress());
                }
            }
        }
        final Object[][] data = new Object[ipv6Addresses.size()][];
        for (int i = 0; i < ipv6Addresses.size(); i++) {
            data[i] = new Object[]{ipv6Addresses.get(i)};
        }
        return Arrays.asList(data);
    }

    public IPv6TestCase(final String ipv6Host) {
        this.destinationHost = ipv6Host;
        this.destinationPort = 6999;
        logger.info("Creating server for IPv6 address " + this.destinationHost);
        server = new DummyServer(destinationHost, destinationPort, "ipv6-test-server");
    }


    @Before
    public void beforeTest() throws IOException {
        server.start();

        final EchoBean bean = new EchoBean();

        // deploy on server
        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), bean);

        // setup EJB client context
        final Properties clientProps = new Properties();
        clientProps.put("remote.connectionprovider.create.options.org.xnio.Options.SSL_ENABLED", "false");
        clientProps.put("remote.connections", "ipv6server");
        clientProps.put("remote.connection.ipv6server.host", this.destinationHost);
        clientProps.put("remote.connection.ipv6server.port", String.valueOf(this.destinationPort));
        clientProps.put("remote.connection.ipv6server.connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS", "false");

        final EJBClientConfiguration clientConfiguration = new PropertiesBasedEJBClientConfiguration(clientProps);
        final ConfigBasedEJBClientContextSelector selector = new ConfigBasedEJBClientContextSelector(clientConfiguration);
        this.previousSelector = EJBClientContext.setSelector(selector);
    }

    @After
    public void afterTest() throws IOException {
        try {
            this.server.stop();
        } catch (Exception e) {
            logger.info("Could not stop server", e);
        }
        if (this.previousSelector != null) {
            EJBClientContext.setSelector(previousSelector);
        }
    }


    /**
     * Tests a simple invocation on a IPv6 server which hosts a bean
     *
     * @throws Exception
     */
    @Test
    public void testInvocationOnIPv6Server() throws Exception {
        logger.info("Running test for " + this.destinationHost + ":" + this.destinationPort);
        // create a proxy for invocation
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, APP_NAME, MODULE_NAME, EchoBean.class.getSimpleName(), "");
        final EchoRemote bean = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", bean);
        final String message = "hello to ipv6 server";
        final String echo = bean.echo(message);
        Assert.assertEquals("Unexpected echo received from IPv6 server " + this.destinationHost + ":" + this.destinationPort, message, echo);
    }


}
