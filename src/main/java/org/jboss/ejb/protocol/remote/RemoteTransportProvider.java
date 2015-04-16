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

package org.jboss.ejb.protocol.remote;

import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBTransportProvider;
import org.kohsuke.MetaInfServices;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * The JBoss Remoting-based transport provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class RemoteTransportProvider implements EJBTransportProvider {

    private final RemoteEJBReceiver receiver = new RemoteEJBReceiver();

    public boolean supportsProtocol(final String uriScheme) {
        switch (uriScheme) {
            case "remote":
            case "remote+http":
            case "remote+https":
            // compatibility
            case "remoting":
            case "http-remoting":
            case "https-remoting": {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public EJBReceiver getReceiver(final String uriScheme) throws IllegalArgumentException {
        switch (uriScheme) {
            case "remote":
            case "remote+http":
            case "remote+https":
            // compatibility
            case "remoting":
            case "http-remoting":
            case "https-remoting": {
                break;
            }
            default: {
                throw new IllegalArgumentException("Unsupported EJB receiver protocol " + uriScheme);
            }
        }
        return receiver;
    }

    public DiscoveryProvider getDiscoveryProvider() {
        return receiver;
    }
}
