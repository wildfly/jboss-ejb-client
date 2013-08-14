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

package org.jboss.ejb.client.test.properties;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Properties;

import org.jboss.ejb.client.ClusterNodeSelector;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.PropertiesBasedEJBClientConfiguration;
import org.jboss.remoting3.RemotingOptions;
import org.junit.Assert;
import org.junit.Test;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Tests creation of {@link EJBClientConfiguration} from {@link Properties}
 *
 * @author Jaikiran Pai
 */
public class PropertiesBasedEJBClientConfigurationTestCase {

    /**
     * Tests that the properties configured in a properties file are configured correctly to a
     * {@link EJBClientConfiguration}
     *
     * @throws Exception
     */
    @Test
    public void testPropertiesParsing() throws Exception {
        System.setProperty("system.prop.foo", "foo");
        final Properties clientProperties = new Properties();
        final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("ejb-client-configuration.properties");
        clientProperties.load(inputStream);

        final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(clientProperties);
        // endpoint name
        final String endpointName = ejbClientConfiguration.getEndpointName();
        Assert.assertEquals("Unexpected endpoint name parsed from properties", "test-endpoint-name", endpointName);

        // connection configurations
        final Iterator<EJBClientConfiguration.RemotingConnectionConfiguration> connectionConfigs = ejbClientConfiguration.getConnectionConfigurations();
        Assert.assertNotNull("No connection configurations found", connectionConfigs);

        while (connectionConfigs.hasNext()) {
            final EJBClientConfiguration.RemotingConnectionConfiguration connectionConfig = connectionConfigs.next();
            final String hostName = connectionConfig.getHost();
            Assert.assertNotNull("Host name was null in connection configuration", hostName);
            if ("foo".equals(hostName)) {
                this.testConnectionConfigOne(connectionConfig);
            } else if ("bar".equals(hostName)) {
                this.testConnectionConfigTwo(connectionConfig);
            } else {
                Assert.fail("Unexpected host name in connection configuration");
            }
        }

        // test cluster configurations
        final EJBClientConfiguration.ClusterConfiguration clusterConfiguration = ejbClientConfiguration.getClusterConfiguration("foo-cluster");
        Assert.assertNotNull("Cluster configuration for foo-cluster not found", clusterConfiguration);
        this.testClusterConfigurationOne(clusterConfiguration);

        // test invocation timeout
        Assert.assertEquals("Unexpected invocation timeout value", 30000, ejbClientConfiguration.getInvocationTimeout());
        // test deployment node selector
        Assert.assertTrue("Unexpected deployment node selector type", ejbClientConfiguration.getDeploymentNodeSelector() instanceof DummyDeploymentNodeSelector);

    }


    /**
     * Tests that the <code>remote.connection.&lt;connection-name&gt;.connect.eager</code> property can be used to control the connection creation behaviour.
     *
     * @throws Exception
     */
    @Test
    public void testLazyConnectionConfiguration() throws Exception {
        final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("lazy-connection-jboss-ejb-client.properties");
        final Properties clientProperties = new Properties();
        clientProperties.load(inputStream);

        final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(clientProperties);

        // connection configurations
        final Iterator<EJBClientConfiguration.RemotingConnectionConfiguration> connectionConfigs = ejbClientConfiguration.getConnectionConfigurations();
        Assert.assertNotNull("No connection configurations found", connectionConfigs);

        while (connectionConfigs.hasNext()) {
            final EJBClientConfiguration.RemotingConnectionConfiguration connectionConfig = connectionConfigs.next();
            final String hostName = connectionConfig.getHost();
            Assert.assertNotNull("Host name was null in connection configuration", hostName);
            if ("lazy".equals(hostName)) {
                Assert.assertFalse("Connection configuration was expected to be marked as lazy", connectionConfig.isConnectEagerly());
            } else if ("eager".equals(hostName)) {
                Assert.assertTrue("Connection configuration was expected to be marked as eager", connectionConfig.isConnectEagerly());
            } else if ("default".equals(hostName)) {
                Assert.assertTrue("Connection configuration was expected to be marked as eager (by default)", connectionConfig.isConnectEagerly());
            }
        }

    }

    /**
     * Tests that the properties configuration can be used to specify <code>remote.connections.connect.eager=false</code> as a default to be applied for all listed
     * connection configurations (unless it's overridden for a specific connection configuration)
     *
     * @throws Exception
     */
    @Test
    public void testDefaultLazyConnectionConfiguration() throws Exception {
        final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("default-lazy-connections-jboss-ejb-client.properties");
        final Properties clientProperties = new Properties();
        clientProperties.load(inputStream);

        final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(clientProperties);

        // connection configurations
        final Iterator<EJBClientConfiguration.RemotingConnectionConfiguration> connectionConfigs = ejbClientConfiguration.getConnectionConfigurations();
        Assert.assertNotNull("No connection configurations found", connectionConfigs);

        while (connectionConfigs.hasNext()) {
            final EJBClientConfiguration.RemotingConnectionConfiguration connectionConfig = connectionConfigs.next();
            final String hostName = connectionConfig.getHost();
            Assert.assertNotNull("Host name was null in connection configuration", hostName);
            if ("lazy".equals(hostName)) {
                Assert.assertFalse("Connection configuration was expected to be marked as lazy", connectionConfig.isConnectEagerly());
            } else if ("eager".equals(hostName)) {
                Assert.assertTrue("Connection configuration was expected to be marked as eager", connectionConfig.isConnectEagerly());
            } else if ("default".equals(hostName)) {
                Assert.assertFalse("Connection configuration was expected to be marked as lazy (by default)", connectionConfig.isConnectEagerly());
            }
        }

    }

    private void testConnectionConfigOne(final EJBClientConfiguration.RemotingConnectionConfiguration connectionConfig) {
        // port
        final int port = connectionConfig.getPort();
        Assert.assertEquals("Unexpected port number for connection configuration", 6999, port);
        // connection options
        final OptionMap connectionOptions = connectionConfig.getConnectionCreationOptions();
        Assert.assertNotNull("Connection options were null for connection configuration", connectionOptions);
        Assert.assertEquals("Unexpected connection config options", false, connectionOptions.get(Options.SASL_POLICY_NOANONYMOUS));

        // connection timeout
        Assert.assertEquals("Unexpected connection timeout for connection configuration", 8000, connectionConfig.getConnectionTimeout());

        // channel creation options
        final OptionMap channelCreationOptions = connectionConfig.getChannelCreationOptions();
        Assert.assertNotNull("Channel creations options were null for connection configuration", channelCreationOptions);
        Assert.assertEquals("Unexpected channel creation options", new Integer(12345), channelCreationOptions.get(RemotingOptions.MAX_OUTBOUND_MESSAGES));
    }

    private void testConnectionConfigTwo(final EJBClientConfiguration.RemotingConnectionConfiguration connectionConfig) {
        // port
        final int port = connectionConfig.getPort();
        Assert.assertEquals("Unexpected port number for connection configuration", 7999, port);
        // connection options
        final OptionMap connectionOptions = connectionConfig.getConnectionCreationOptions();
        Assert.assertNotNull("Connection options were null for connection configuration", connectionOptions);
        Assert.assertEquals("Unexpected connection config options", true, connectionOptions.get(Options.SASL_POLICY_NOANONYMOUS));

    }

    private void testClusterConfigurationOne(final EJBClientConfiguration.ClusterConfiguration clusterConfiguration) {
        Assert.assertEquals("Unexpected cluster name", "foo-cluster", clusterConfiguration.getClusterName());
        Assert.assertEquals("Unexpected connection timeout for cluster", 7500, clusterConfiguration.getConnectionTimeout());
        Assert.assertEquals("Unexpected max connected nodes for cluster", 22, clusterConfiguration.getMaximumAllowedConnectedNodes());
        final ClusterNodeSelector clusterNodeSelector = clusterConfiguration.getClusterNodeSelector();
        Assert.assertTrue("Unexpected clusternode selector", clusterNodeSelector instanceof DummyClusterNodeSelector);
        // connection options
        final OptionMap connectionOptions = clusterConfiguration.getConnectionCreationOptions();
        Assert.assertNotNull("Connection options were null for cluster configuration", connectionOptions);
        Assert.assertEquals("Unexpected connection config options", true, connectionOptions.get(Options.SASL_POLICY_NOANONYMOUS));

        final EJBClientConfiguration.ClusterNodeConfiguration clusterNodeConfiguration = clusterConfiguration.getNodeConfiguration("foo-node");
        Assert.assertNotNull("Node configuration not found for foo-node in foo-cluster", clusterNodeConfiguration);
        Assert.assertEquals("Unexpected node name", "foo-node", clusterNodeConfiguration.getNodeName());
        Assert.assertEquals("Unexpected connection timeout for node", 5550, clusterNodeConfiguration.getConnectionTimeout());

        // connection options
        final OptionMap nodeConnectionOptions = clusterNodeConfiguration.getConnectionCreationOptions();
        Assert.assertNotNull("Connection options were null for node configuration", nodeConnectionOptions);
        Assert.assertEquals("Unexpected connection config options", false, nodeConnectionOptions.get(Options.SASL_POLICY_NOANONYMOUS));

    }
}
