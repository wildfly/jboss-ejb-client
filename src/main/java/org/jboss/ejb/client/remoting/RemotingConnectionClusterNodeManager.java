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

import org.jboss.ejb.client.ClusterNodeManager;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * A {@link RemotingConnectionClusterNodeManager} uses JBoss Remoting to create a {@link EJBReceiver}
 * for a cluster node
 *
 * @author Jaikiran Pai
 */
class RemotingConnectionClusterNodeManager implements ClusterNodeManager {

    private static final Logger logger = Logger.getLogger(RemotingConnectionClusterNodeManager.class);

    private final String clusterName;
    private final ClusterNode clusterNode;
    private final Endpoint endpoint;
    private final EJBClientConfiguration ejbClientConfiguration;

    RemotingConnectionClusterNodeManager(final String clusterName, final ClusterNode clusterNode, final Endpoint endpoint, final EJBClientConfiguration ejbClientConfiguration) {
        this.clusterName = clusterName;
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
            logger.error("Cannot create a EJB receiver for " + this.clusterNode + " since there was no match for a target destination");
            return null;
        }
        final Connection connection;
        try {
            connection = this.createConnection();
        } catch (Exception e) {
            throw new RuntimeException("Could not create a connection for cluster node " + this.clusterNode + " in cluster " + this.clusterName);
        }
        return new RemotingConnectionEJBReceiver(connection);
    }

    private Connection createConnection() throws IOException, URISyntaxException {
        final URI connectionURI = new URI("remote://" + this.clusterNode.getDestinationAddress() + ":" + this.clusterNode.getDestinationPort());
        if (this.ejbClientConfiguration != null) {
            final EJBClientConfiguration.ClusterConfiguration clusterConfiguration = this.ejbClientConfiguration.getClusterConfiguration(this.clusterName);
            if (clusterConfiguration == null) {
                // use default configurations
                final IoFuture<Connection> futureConnection = endpoint.connect(connectionURI, OptionMap.EMPTY, ejbClientConfiguration.getCallbackHandler());
                // wait for the connection to be established
                return IoFutureHelper.get(futureConnection, 5000, TimeUnit.MILLISECONDS);
            } else {
                // use the specified configurations
                final IoFuture<Connection> futureConnection = endpoint.connect(connectionURI, clusterConfiguration.getConnectionCreationOptions(), clusterConfiguration.getCallbackHandler());
                // wait for the connection to be established
                return IoFutureHelper.get(futureConnection, clusterConfiguration.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            }

        }
        return createConnectionUsingDefaults();
    }

    private Connection createConnectionUsingDefaults() throws IOException, URISyntaxException {
        final URI connectionURI = new URI("remote://" + this.clusterNode.getDestinationAddress() + ":" + this.clusterNode.getDestinationPort());
        // use default configurations
        final IoFuture<Connection> futureConnection = endpoint.connect(connectionURI, OptionMap.EMPTY, new AnonymousCallbackHandler());
        // wait for the connection to be established
        return IoFutureHelper.get(futureConnection, 5000, TimeUnit.MILLISECONDS);
    }

    /**
     * A {@link javax.security.auth.callback.CallbackHandler} which sets <code>anonymous</code> as the name during a {@link javax.security.auth.callback.NameCallback}
     */
    private class AnonymousCallbackHandler implements CallbackHandler {

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName("anonymous");
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }
        }
    }
}
