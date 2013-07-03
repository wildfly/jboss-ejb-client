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

import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;

import java.io.IOException;

/**
 * A {@link MaxAttemptsReconnectHandler} which creates a {@link RemotingConnectionEJBReceiver} out of the
 * recreated connection and adds it to the {@link EJBClientContext}
 *
 * @author Jaikiran Pai
 */
class EJBClientContextConnectionReconnectHandler extends MaxAttemptsReconnectHandler {

    private final EJBClientContext ejbClientContext;
    private final RemotingCleanupHandler remotingCleanupHandler = new RemotingCleanupHandler();

    EJBClientContextConnectionReconnectHandler(final EJBClientContext clientContext, final Endpoint endpoint, final String protocol, final String host, final int port, final EJBClientConfiguration.CommonConnectionCreationConfiguration connectionConfiguration, final int maxReconnectAttempts) {
        super(endpoint, protocol, host, port, connectionConfiguration, maxReconnectAttempts);
        this.ejbClientContext = clientContext;
        // register a EJB client context listener so that we can close the connections we create, when the
        // EJB client context is closed
        this.ejbClientContext.registerEJBClientContextListener(this.remotingCleanupHandler);
    }

    @Override
    public void reconnect() throws IOException {
        Connection connection = null;
        try {
            connection = this.tryConnect();
            if (connection == null) {
                return;
            }
            // keep track of this connection so that we can close it when the EJB client context is closed
            this.remotingCleanupHandler.addConnection(connection);
            final EJBReceiver ejbReceiver = new RemotingConnectionEJBReceiver(connection, this, connectionConfiguration.getChannelCreationOptions(), protocol);
            this.ejbClientContext.registerEJBReceiver(ejbReceiver);
        } finally {
            // if we successfully re-connected or if no more attempts are allowed for re-connecting
            // then unregister this ReconnectHandler from the EJBClientContext
            if (connection != null || !this.hasMoreAttempts()) {
                this.ejbClientContext.unregisterReconnectHandler(this);
            }
        }
    }
}
