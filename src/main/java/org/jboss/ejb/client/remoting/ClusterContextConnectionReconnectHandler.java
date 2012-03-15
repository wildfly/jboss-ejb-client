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

import org.jboss.ejb.client.ClusterContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

import javax.security.auth.callback.CallbackHandler;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * A {@link MaxAttemptsReconnectHandler} which creates a {@link RemotingConnectionEJBReceiver} out of the
 * recreated connection and adds it to the {@link org.jboss.ejb.client.ClusterContext}
 *
 * @author Jaikiran Pai
 */
class ClusterContextConnectionReconnectHandler extends MaxAttemptsReconnectHandler {

    private static Logger logger = Logger.getLogger(ClusterContextConnectionReconnectHandler.class);

    private final ClusterContext clusterContext;

    ClusterContextConnectionReconnectHandler(final ClusterContext clusterContext, final Endpoint endpoint, final URI uri,
                                             final OptionMap connectionCreationOptions, final CallbackHandler callbackHandler,
                                             final OptionMap channelCreationOptions, final int maxReconnectAttempts) {
        super(endpoint, uri, connectionCreationOptions, callbackHandler, channelCreationOptions, maxReconnectAttempts);
        this.clusterContext = clusterContext;
    }

    @Override
    public void reconnect() throws IOException {
        Connection connection = null;
        try {
            connection = this.tryConnect(5, TimeUnit.SECONDS);
            if (connection == null) {
                return;
            }
            final EJBReceiver ejbReceiver = new RemotingConnectionEJBReceiver(connection, this, channelCreationOptions);
            this.clusterContext.registerEJBReceiver(ejbReceiver);
        } finally {
            // if we successfully re-connected or if no more attempts are allowed for re-connecting
            // then unregister this ReconnectHandler from the EJBClientContext
            if (connection != null || !this.hasMoreAttempts()) {
                this.clusterContext.getEJBClientContext().unregisterReconnectHandler(this);
            }
        }
    }

}
