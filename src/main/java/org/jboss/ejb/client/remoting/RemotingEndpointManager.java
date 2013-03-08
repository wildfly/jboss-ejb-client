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

import org.jboss.logging.Logger;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link RemotingEndpointManager} can be used to obtain remoting {@link Endpoint} by passing it the endpoint creation configurations.
 * A {@link RemotingEndpointManager} will act as a central entity for creating the endpoints and internally will interact with pooled
 * endpoints.
 *
 * @author Jaikiran Pai
 */
class RemotingEndpointManager {

    private static final Logger logger = Logger.getLogger(RemotingEndpointManager.class);

    private final EndpointPool endpointPool = EndpointPool.INSTANCE;
    private final List<Endpoint> managedEndpoints = Collections.synchronizedList(new ArrayList<Endpoint>());

    Endpoint getEndpoint(final String endpointName, final OptionMap endpointCreationOptions, final OptionMap remotingConnectionProviderOptions) throws IOException {
        final Endpoint endpoint = this.endpointPool.getEndpoint(endpointName, endpointCreationOptions, remotingConnectionProviderOptions);
        // track this endpoint so that we can close it when appropriate
        this.managedEndpoints.add(endpoint);
        return endpoint;
    }

    /**
     * Closes all the "managed" endpoints that were handed out by this {@link RemotingEndpointManager}. A "close"
     * doesn't necessarily translate to a real close of the {@link Endpoint} since these "managed" endpoints are pooled endpoints.
     */
    void safeClose() {
        synchronized (managedEndpoints) {
            for (final Endpoint endpoint : this.managedEndpoints) {
                try {
                    endpoint.close();
                } catch (Throwable t) {
                    logger.debug("Failed to close " + endpoint, t);
                }
            }
        }
    }

    /**
     * Closes all the "managed" endpoints that were handed out by this {@link RemotingEndpointManager}. A "close"
     * doesn't necessarily translate to a real close of the {@link Endpoint} since these "managed" endpoints are pooled endpoints.
     */
    void closeAsync() {
        synchronized (managedEndpoints) {
            for (final Endpoint endpoint : this.managedEndpoints) {
                endpoint.closeAsync();
            }
        }
    }

    /**
     * Closes all the "managed" endpoints that were handed out by this {@link RemotingEndpointManager}. A "close"
     * doesn't necessarily translate to a real close of the {@link Endpoint} since these "managed" endpoints are pooled endpoints.
     */
    void close() throws IOException {
        synchronized (managedEndpoints) {
            for (final Endpoint endpoint : this.managedEndpoints) {
                endpoint.close();
            }
        }
    }
}
