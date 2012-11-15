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

package org.jboss.ejb.client.remoting;

import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBClientContextListener;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link EJBClientContextListener} which closes all remoting endpoints and connections registered with it,
 * in its {@link #contextClosed(org.jboss.ejb.client.EJBClientContext)} method, which is invoked when the
 * {@link EJBClientContext} to which it is registered, closes
 *
 * @author Jaikiran Pai
 */
class RemotingCleanupHandler implements EJBClientContextListener {

    private static final Logger logger = Logger.getLogger(RemotingCleanupHandler.class);

    private final List<Connection> connections = new ArrayList<Connection>();
    private final List<Endpoint> endpoints = new ArrayList<Endpoint>();

    @Override
    public void contextClosed(EJBClientContext ejbClientContext) {
        this.closeAll();
    }

    void addEndpoint(final Endpoint endpoint) {
        if (endpoint == null) {
            return;
        }
        this.endpoints.add(endpoint);
    }

    void addConnection(final Connection connection) {
        if (connection == null) {
            return;
        }
        this.connections.add(connection);
    }

    void closeAll() {
        synchronized (this.connections) {
            for (final Connection connection : connections) {
                safeClose(connection);
            }
        }
        synchronized (this.endpoints) {
            for (final Endpoint endpoint : endpoints) {
                safeClose(endpoint);
            }
        }
    }

    private void safeClose(Closeable closable) {
        try {
            logger.debug("Closing " + closable);
            closable.close();
        } catch (Throwable e) {
            logger.debug("Exception closing " + closable, e);
        }
    }
}
