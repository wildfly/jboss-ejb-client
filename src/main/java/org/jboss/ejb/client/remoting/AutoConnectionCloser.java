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

import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;

/**
 * The {@link AutoConnectionCloser} creates a {@link Runtime#addShutdownHook(Thread) JVM shutdown hook}
 * to close the remoting endpoints and connections that have been registered with it. The EJB client API uses
 * this {@link AutoConnectionCloser} to keep track of remoting endpoints and connections which it has "auto"
 * created based on either the properties configured by the user or some other means. Keeping track of such resources
 * here, allows for closing them down when the JVM shuts down.
 * <p/>
 * Note that the close performed here, is an last ditch attempt to cleanup the resource usage. Ideally, these resources
 * are expected to be closed whenever they are no longer needed. {@link AutoConnectionCloser} is *not* a substitute
 * for proper resource management.
 *
 * @author Jaikiran Pai
 */
class AutoConnectionCloser implements Runnable {

    static final AutoConnectionCloser INSTANCE = new AutoConnectionCloser();

    private final RemotingCleanupHandler remotingCleanupHandler = new RemotingCleanupHandler();

    private AutoConnectionCloser() {
        // add a shutdown hook
        SecurityActions.addShutdownHook(new Thread(this));
    }

    @Override
    public void run() {
        this.remotingCleanupHandler.closeAll();
    }

    void addEndpoint(final Endpoint endpoint) {
        this.remotingCleanupHandler.addEndpoint(endpoint);
    }

    void addConnection(final Connection connection) {
        this.remotingCleanupHandler.addConnection(connection);
    }
}
