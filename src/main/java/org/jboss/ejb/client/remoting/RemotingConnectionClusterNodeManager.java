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

package org.jboss.ejb.client.remoting;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.ejb.client.ClusterContext;
import org.jboss.ejb.client.ClusterNodeManager;
import org.jboss.ejb.client.DefaultCallbackHandler;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.Logs;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

/**
 * A {@link RemotingConnectionClusterNodeManager} uses JBoss Remoting to create a {@link EJBReceiver}
 * for a cluster node
 *
 * @author Jaikiran Pai
 */
class RemotingConnectionClusterNodeManager implements ClusterNodeManager {

    private static final Logger logger = Logger.getLogger(RemotingConnectionClusterNodeManager.class);

    private static final OptionMap DEFAULT_CONNECTION_CREATION_OPTIONS = OptionMap.EMPTY;

    private final ClusterContext clusterContext;
    private final ClusterNode clusterNode;
    private final Endpoint endpoint;
    private final EJBClientConfiguration ejbClientConfiguration;
    private final RemotingConnectionManager remotingConnectionManager = new RemotingConnectionManager();
    private final EJBClientConfiguration.CommonConnectionCreationConfiguration connectionConfiguration;

    RemotingConnectionClusterNodeManager(final ClusterContext clusterContext, final ClusterNode clusterNode, final Endpoint endpoint, final EJBClientConfiguration ejbClientConfiguration) {
        this.clusterContext = clusterContext;
        this.clusterNode = clusterNode;
        this.endpoint = endpoint;
        this.ejbClientConfiguration = ejbClientConfiguration;
        this.connectionConfiguration = createConnectionConfiguration();
    }

    @Override
    public String getNodeName() {
        return this.clusterNode.getNodeName();
    }

    @Override
    public EJBReceiver getEJBReceiver() {
        if (!this.clusterNode.isDestinationResolved()) {
            Logs.REMOTING.cannotCreateEJBReceiverDueToUnknownTarget(this.clusterNode.toString());
            return null;
        }
        try {
            final Connection connection = remotingConnectionManager.getConnection(endpoint, clusterNode.getDestinationAddress(), clusterNode.getDestinationPort(), connectionConfiguration);
            // create a re-connect handler (which will be used on connection breaking down)
            final int MAX_RECONNECT_ATTEMPTS = 65535; // TODO: Let's keep this high for now and later allow configuration and a smaller default value
            final ReconnectHandler reconnectHandler = new ClusterContextConnectionReconnectHandler(clusterContext, endpoint, clusterNode.getDestinationAddress(), clusterNode.getDestinationPort(), connectionConfiguration, MAX_RECONNECT_ATTEMPTS);
            return new RemotingConnectionEJBReceiver(connection, reconnectHandler, connectionConfiguration.getChannelCreationOptions());
        } catch (Exception e) {
            logger.info("Could not create a connection for cluster node " + this.clusterNode + " in cluster " + clusterContext.getClusterName(), e);
            return null;
        }
    }

    private EJBClientConfiguration.CommonConnectionCreationConfiguration createConnectionConfiguration() {
        final EJBClientConfiguration.CommonConnectionCreationConfiguration connectionConfiguration;
        // if the client configuration is available create the connection using those configs
        if (this.ejbClientConfiguration != null) {
            final EJBClientConfiguration.ClusterConfiguration clusterConfiguration = this.ejbClientConfiguration.getClusterConfiguration(clusterContext.getClusterName());
            if (clusterConfiguration == null) {
                // use default configurations
                final CallbackHandler callbackHandler = ejbClientConfiguration.getCallbackHandler();
                final OptionMap connectionCreationOpts = RemotingConnectionUtil.addSilentLocalAuthOptionsIfApplicable(callbackHandler, DEFAULT_CONNECTION_CREATION_OPTIONS);
                connectionConfiguration = new ConnectionConfig(connectionCreationOpts, callbackHandler, 5000, OptionMap.EMPTY);
            } else {
                final EJBClientConfiguration.ClusterNodeConfiguration clusterNodeConfiguration = clusterConfiguration.getNodeConfiguration(this.getNodeName());
                // use the specified configurations
                final OptionMap channelCreationOptions = clusterNodeConfiguration == null ? clusterConfiguration.getChannelCreationOptions() : clusterNodeConfiguration.getChannelCreationOptions();
                final CallbackHandler callbackHandler = clusterNodeConfiguration == null ? clusterConfiguration.getCallbackHandler() : clusterNodeConfiguration.getCallbackHandler();
                OptionMap connectionCreationOptions = clusterNodeConfiguration == null ? clusterConfiguration.getConnectionCreationOptions() : clusterNodeConfiguration.getConnectionCreationOptions();
                connectionCreationOptions = RemotingConnectionUtil.addSilentLocalAuthOptionsIfApplicable(callbackHandler, connectionCreationOptions);
                final long timeout = clusterNodeConfiguration == null ? clusterConfiguration.getConnectionTimeout() : clusterNodeConfiguration.getConnectionTimeout();
                connectionConfiguration = new ConnectionConfig(connectionCreationOptions, callbackHandler, timeout, channelCreationOptions);
            }
        } else {
            // create the connection using defaults
            final CallbackHandler callbackHandler = new DefaultCallbackHandler();
            final OptionMap connectionCreationOpts = RemotingConnectionUtil.addSilentLocalAuthOptionsIfApplicable(callbackHandler, DEFAULT_CONNECTION_CREATION_OPTIONS);
            connectionConfiguration = new ConnectionConfig(connectionCreationOpts, callbackHandler, 5000, OptionMap.EMPTY);
        }
        return connectionConfiguration;
    }

    private class ConnectionConfig implements EJBClientConfiguration.CommonConnectionCreationConfiguration {

        private final long connectionTimeout;
        private final OptionMap connectionCreationOptions;
        private final OptionMap channelCreationOptions;
        private final CallbackHandler callbackHandler;

        ConnectionConfig(final OptionMap connectionCreationOptions, final CallbackHandler callbackHandler, final long connectionTimeout,
                         final OptionMap channelCreationOptions) {

            this.callbackHandler = callbackHandler == null ? new DefaultCallbackHandler() : callbackHandler;
            this.connectionCreationOptions = connectionCreationOptions;
            this.channelCreationOptions = channelCreationOptions;
            this.connectionTimeout = connectionTimeout;
        }

        @Override
        public OptionMap getConnectionCreationOptions() {
            return this.connectionCreationOptions;
        }

        @Override
        public CallbackHandler getCallbackHandler() {
            return this.callbackHandler;
        }

        @Override
        public long getConnectionTimeout() {
            return this.connectionTimeout;
        }

        @Override
        public OptionMap getChannelCreationOptions() {
            return this.channelCreationOptions;
        }

        @Override
        public boolean isConnectEagerly() {
            // connecting to cluster node is always on-demand and not eager. So return false.
            return false;
        }
    }
}
