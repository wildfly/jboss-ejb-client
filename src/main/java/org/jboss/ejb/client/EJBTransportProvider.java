/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client;

import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * An EJB transport provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface EJBTransportProvider {

    /**
     * Determine whether this transport provider supports the protocol identified by the given URI scheme.
     *
     * @param uriScheme the URI scheme
     * @return {@code true} if this provider supports the protocol, {@code false} otherwise
     */
    boolean supportsProtocol(String uriScheme);

    /**
     * Get an EJB receiver for the protocol identified by the given URI scheme.
     *
     * @param uriScheme the URI scheme
     * @return the non-{@code null} EJB receiver
     * @throws IllegalArgumentException if the protocol is not supported
     */
    EJBReceiver getReceiver(String uriScheme) throws IllegalArgumentException;

    /**
     * Get the discovery provider for this transport provider.  If this transport provider does not provide any
     * discovery mechanism, {@code null} is returned.
     *
     * @return the discovery provider for this transport provider, or {@code null} if there is no discovery method for
     *     this provider
     */
    DiscoveryProvider getDiscoveryProvider();
}
