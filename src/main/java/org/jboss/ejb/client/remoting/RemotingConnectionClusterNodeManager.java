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
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
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

    private final ClusterNode clusterNode;
    private final Endpoint endpoint;

    RemotingConnectionClusterNodeManager(final ClusterNode clusterNode, final Endpoint endpoint) {
        this.clusterNode = clusterNode;
        this.endpoint = endpoint;
    }

    @Override
    public String getNodeName() {
        return this.clusterNode.getNodeName();
    }

    @Override
    public EJBReceiver getEJBReceiver() {
        final Connection connection;
        try {
            connection = this.createConnection();
        } catch (Exception e) {
            throw new RuntimeException("Could not create a connection for cluster node " + this.clusterNode);
        }
        return new RemotingConnectionEJBReceiver(connection);
    }

    private Connection createConnection() throws IOException, URISyntaxException {
        final RemotingConnectionConfigurator remotingConnectionConfigurator = RemotingConnectionConfigurator.configuratorFor(this.endpoint);
        // TODO: We need a better way to manage these connection options for each cluster node
        return remotingConnectionConfigurator.createConnection(this.clusterNode, OptionMap.EMPTY, new AnonymousCallbackHandler(), 5, TimeUnit.SECONDS);
    }

    /**
     * A {@link javax.security.auth.callback.CallbackHandler} which sets <code>anonymous</code> as the name during a {@link javax.security.auth.callback.NameCallback}
     */
    // TODO: Get rid of this class as soon as we have a better way for configuring cluster node properties
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
