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

import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for closing down all that auto created remoting connections and endpoints that this {@link AutoConnectionCloser}
 * keeps tracks of
 *
 * @author Jaikiran Pai
 */
class AutoConnectionCloser implements Runnable {

    private static Logger logger = Logger.getLogger(AutoConnectionCloser.class);

    static final AutoConnectionCloser INSTANCE = new AutoConnectionCloser();

    private final List<Connection> connections = new ArrayList<Connection>();
    private final List<Endpoint> endpoints = new ArrayList<Endpoint>();

    private AutoConnectionCloser() {
        // add a shutdown hook
        SecurityActions.addShutdownHook(new Thread(this));
    }

    @Override
    public void run() {
        this.closeAll();
    }

    void addEndpoint(final Endpoint endpoint) {
        if (endpoint == null) {
            return;
        }
        synchronized (this.endpoints) {
            this.endpoints.add(endpoint);
        }
    }

    void addConnection(final Connection connection) {
        if (connection == null) {
            return;
        }
        synchronized (this.connections) {
            this.connections.add(connection);
        }
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
