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
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import javax.security.auth.callback.CallbackHandler;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ReconnectHandler} which has a upper bound on the reconnect attempt
 *
 * @author Jaikiran Pai
 */
abstract class MaxAttemptsReconnectHandler implements ReconnectHandler {

    private static final Logger logger = Logger.getLogger(MaxAttemptsReconnectHandler.class);

    protected final Endpoint endpoint;
    protected final URI connectionURI;
    protected final OptionMap connectionCreationOptions;
    protected final CallbackHandler callbackHandler;
    protected final OptionMap channelCreationOptions;
    protected final int maxReconnectAttempts;

    protected volatile int reconnectAttempts;


    MaxAttemptsReconnectHandler(final Endpoint endpoint, final URI uri, final OptionMap connectionCreationOptions,
                                final CallbackHandler callbackHandler, final OptionMap channelCreationOptions, final int maxReconnectAttempts) {
        this.endpoint = endpoint;
        this.connectionURI = uri;
        this.connectionCreationOptions = connectionCreationOptions;
        this.callbackHandler = callbackHandler;
        this.channelCreationOptions = channelCreationOptions == null ? OptionMap.EMPTY : channelCreationOptions;
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    protected Connection tryConnect(final long connectionTimeout, final TimeUnit unit) {
        if (reconnectAttempts >= maxReconnectAttempts) {
            return null;
        }
        reconnectAttempts++;
        try {
            final IoFuture<Connection> futureConnection = endpoint.connect(connectionURI, connectionCreationOptions, callbackHandler);
            final Connection connection = IoFutureHelper.get(futureConnection, connectionTimeout, unit);
            logger.debug("Successfully reconnected on attempt#" + reconnectAttempts + " to connection " + connection);
            return connection;

        } catch (Exception e) {
            logger.debug("Re-connect attempt# " + reconnectAttempts + " failed for connection URI " + this.connectionURI, e);
            return null;
        }

    }

    protected boolean hasMoreAttempts() {
        return this.reconnectAttempts < maxReconnectAttempts;
    }
}
