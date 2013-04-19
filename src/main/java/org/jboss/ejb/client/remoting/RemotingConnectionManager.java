/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link RemotingConnectionManager} can be used to obtain remoting {@link Connection} by passing it the connection configurations.
 * A {@link RemotingConnectionManager} will act as a central entity for creating the connections and internally will interact with pooled
 * connections.
 *
 * @author: Jaikiran Pai
 */
class RemotingConnectionManager {

    private static final Logger logger = Logger.getLogger(RemotingConnectionManager.class);

    private final ConnectionPool connectionPool = ConnectionPool.INSTANCE;

    private final List<Connection> managedConnections = Collections.synchronizedList(new ArrayList<Connection>());

    Connection getConnection(final Endpoint endpoint, final String protocol, final String host, final int port, final EJBClientConfiguration.CommonConnectionCreationConfiguration connectionConfiguration) throws IOException {
        final Connection connection = this.connectionPool.getConnection(endpoint, protocol, host, port, connectionConfiguration);
        // track this connection so that we can release it back to the pool when appropriate
        trackConnection(connection);
        return connection;
    }

    /**
     * Closes all the "managed" connections that were handed out by this {@link RemotingConnectionManager}. A "close"
     * doesn't necessarily translate to a real close of the {@link Connection} since these "managed" connections are pooled connections
     */
    void safeClose() {
        synchronized (managedConnections) {
            for (final Connection connection : this.managedConnections) {
                try {
                    connection.close();
                } catch (Throwable t) {
                    logger.debug("Failed to close " + connection, t);
                }
            }
        }
    }

    /**
     * Closes all the "managed" connections that were handed out by this {@link RemotingConnectionManager}. A "close"
     * doesn't necessarily translate to a real close of the {@link Connection} since these "managed" connections are pooled connections
     */
    void closeAsync() {
        synchronized (managedConnections) {
            for (final Connection connection : this.managedConnections) {
                connection.closeAsync();
            }
        }
    }

    /**
     * Closes all the "managed" connections that were handed out by this {@link RemotingConnectionManager}. A "close"
     * doesn't necessarily translate to a real close of the {@link Connection} since these "managed" connections are pooled connections
     */
    void close() throws IOException {
        synchronized (managedConnections) {
            for (final Connection connection : this.managedConnections) {
                connection.close();
            }
        }
    }

    private void trackConnection(final Connection connection) {
        this.managedConnections.add(connection);
    }
}
