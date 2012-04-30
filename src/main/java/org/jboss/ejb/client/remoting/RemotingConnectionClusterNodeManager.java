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

import org.jboss.ejb.client.AnonymousCallbackHandler;
import org.jboss.ejb.client.ClusterContext;
import org.jboss.ejb.client.ClusterNodeManager;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;

import javax.security.auth.callback.CallbackHandler;
import java.util.concurrent.TimeUnit;

/**
 * A {@link RemotingConnectionClusterNodeManager} uses JBoss Remoting to create a {@link EJBReceiver}
 * for a cluster node
 *
 * @author Jaikiran Pai
 */
class RemotingConnectionClusterNodeManager implements ClusterNodeManager {

    private static final Logger logger = Logger.getLogger(RemotingConnectionClusterNodeManager.class);

    private static final OptionMap DEFAULT_CONNECTION_CREATION_OPTIONS = OptionMap.create(Options.SASL_PROPERTIES, Sequence.of(Property.of("jboss.sasl.local-user.quiet-auth", "true")), Options.SASL_POLICY_NOPLAINTEXT, false);

    private final ClusterContext clusterContext;
    private final ClusterNode clusterNode;
    private final Endpoint endpoint;
    private final EJBClientConfiguration ejbClientConfiguration;

    RemotingConnectionClusterNodeManager(final ClusterContext clusterContext, final ClusterNode clusterNode, final Endpoint endpoint, final EJBClientConfiguration ejbClientConfiguration) {
        this.clusterContext = clusterContext;
        this.clusterNode = clusterNode;
        this.endpoint = endpoint;
        this.ejbClientConfiguration = ejbClientConfiguration;
    }

    @Override
    public String getNodeName() {
        return this.clusterNode.getNodeName();
    }

    @Override
    public EJBReceiver getEJBReceiver() {
        if (!this.clusterNode.isDestinationResolved()) {
            logger.info("Cannot create a EJB receiver for " + this.clusterNode + " since there was no match for a target destination");
            return null;
        }
        Connection connection = null;
        final ReconnectHandler reconnectHandler;
        OptionMap channelCreationOptions = OptionMap.EMPTY;
        final int MAX_RECONNECT_ATTEMPTS = 65535; // TODO: Let's keep this high for now and later allow configuration and a smaller default value
        try {
            // if the client configuration is available create the connection using those configs
            if (this.ejbClientConfiguration != null) {
                final EJBClientConfiguration.ClusterConfiguration clusterConfiguration = this.ejbClientConfiguration.getClusterConfiguration(clusterContext.getClusterName());
                if (clusterConfiguration == null) {
                    // use default configurations
                    final CallbackHandler callbackHandler = ejbClientConfiguration.getCallbackHandler();
                    final IoFuture<Connection> futureConnection = NetworkUtil.connect(endpoint, clusterNode.getDestinationAddress(), clusterNode.getDestinationPort(), null, DEFAULT_CONNECTION_CREATION_OPTIONS, callbackHandler);
                    // wait for the connection to be established
                    connection = IoFutureHelper.get(futureConnection, 5000, TimeUnit.MILLISECONDS);
                    // create a re-connect handler (which will be used on connection breaking down)
                    reconnectHandler = new ClusterContextConnectionReconnectHandler(clusterContext, endpoint, clusterNode.getDestinationAddress(), clusterNode.getDestinationPort(), DEFAULT_CONNECTION_CREATION_OPTIONS, callbackHandler, channelCreationOptions, MAX_RECONNECT_ATTEMPTS, 5000, TimeUnit.MILLISECONDS);

                } else {
                    final EJBClientConfiguration.ClusterNodeConfiguration clusterNodeConfiguration = clusterConfiguration.getNodeConfiguration(this.getNodeName());
                    // use the specified configurations
                    channelCreationOptions = clusterNodeConfiguration == null ? clusterConfiguration.getChannelCreationOptions() : clusterNodeConfiguration.getChannelCreationOptions();
                    final OptionMap connectionCreationOptions = clusterNodeConfiguration == null ? clusterConfiguration.getConnectionCreationOptions() : clusterNodeConfiguration.getConnectionCreationOptions();
                    final CallbackHandler callbackHandler = clusterNodeConfiguration == null ? clusterConfiguration.getCallbackHandler() : clusterNodeConfiguration.getCallbackHandler();
                    final IoFuture<Connection> futureConnection = NetworkUtil.connect(endpoint, clusterNode.getDestinationAddress(), clusterNode.getDestinationPort(), null, connectionCreationOptions, callbackHandler);
                    final long timeout = clusterNodeConfiguration == null ? clusterConfiguration.getConnectionTimeout() : clusterNodeConfiguration.getConnectionTimeout();
                    // wait for the connection to be established
                    connection = IoFutureHelper.get(futureConnection, timeout, TimeUnit.MILLISECONDS);
                    // create a re-connect handler (which will be used on connection breaking down)
                    reconnectHandler = new ClusterContextConnectionReconnectHandler(clusterContext, endpoint, clusterNode.getDestinationAddress(), clusterNode.getDestinationPort(), connectionCreationOptions, callbackHandler, channelCreationOptions, MAX_RECONNECT_ATTEMPTS, timeout, TimeUnit.MILLISECONDS);
                }

            } else {
                // create the connection using defaults
                final CallbackHandler callbackHandler = new AnonymousCallbackHandler();
                final IoFuture<Connection> futureConnection = NetworkUtil.connect(endpoint, clusterNode.getDestinationAddress(), clusterNode.getDestinationPort(), null, DEFAULT_CONNECTION_CREATION_OPTIONS, callbackHandler);
                // wait for the connection to be established
                connection = IoFutureHelper.get(futureConnection, 5000, TimeUnit.MILLISECONDS);
                // create a re-connect handler (which will be used on connection breaking down)
                reconnectHandler = new ClusterContextConnectionReconnectHandler(clusterContext, endpoint, clusterNode.getDestinationAddress(), clusterNode.getDestinationPort(), DEFAULT_CONNECTION_CREATION_OPTIONS, callbackHandler, channelCreationOptions, MAX_RECONNECT_ATTEMPTS, 5000, TimeUnit.MILLISECONDS);

            }
        } catch (Exception e) {
            logger.info("Could not create a connection for cluster node " + this.clusterNode + " in cluster " + clusterContext.getClusterName(), e);
            return null;
        } finally {
            if (connection != null) {
                // keep track of the created connection to auto close on JVM shutdown
                AutoConnectionCloser.INSTANCE.addConnection(connection);
            }
        }
        return new RemotingConnectionEJBReceiver(connection, reconnectHandler, channelCreationOptions);
    }

}
