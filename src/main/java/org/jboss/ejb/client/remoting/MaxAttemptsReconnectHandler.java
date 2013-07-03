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
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;

/**
 * A {@link ReconnectHandler} which has a upper bound on the reconnect attempt
 *
 * @author Jaikiran Pai
 */
abstract class MaxAttemptsReconnectHandler implements ReconnectHandler {

    private static final Logger logger = Logger.getLogger(MaxAttemptsReconnectHandler.class);

    protected final Endpoint endpoint;
    protected final String protocol;
    protected final String host;
    protected final int port;
    protected final EJBClientConfiguration.CommonConnectionCreationConfiguration connectionConfiguration;
    protected final int maxReconnectAttempts;
    private final RemotingConnectionManager remotingConnectionManager = new RemotingConnectionManager();

    protected volatile int reconnectAttempts;


    MaxAttemptsReconnectHandler(final Endpoint endpoint, final String protocol, final String host, final int port, final EJBClientConfiguration.CommonConnectionCreationConfiguration connectionConfiguration, final int maxReconnectAttempts) {
        this.endpoint = endpoint;
        this.connectionConfiguration = connectionConfiguration;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.protocol = protocol;
        this.host = host;
        this.port = port;
    }

    protected Connection tryConnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            return null;
        }
        reconnectAttempts++;
        try {
            final Connection connection = remotingConnectionManager.getConnection(endpoint, protocol, host, port, connectionConfiguration);
            logger.debug("Successfully reconnected on attempt#" + reconnectAttempts + " to connection " + connection);
            return connection;

        } catch (Exception e) {
            logger.debug("Re-connect attempt# " + reconnectAttempts + " failed for " + host + ":" + port, e);
            return null;
        }

    }

    protected boolean hasMoreAttempts() {
        return this.reconnectAttempts < maxReconnectAttempts;
    }
}
