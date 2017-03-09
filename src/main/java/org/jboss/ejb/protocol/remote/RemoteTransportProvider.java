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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.ejb.client.AttachmentKey;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBTransportProvider;
import org.kohsuke.MetaInfServices;
import org.wildfly.discovery.ServiceRegistration;
import org.wildfly.discovery.ServiceRegistry;
import org.wildfly.discovery.impl.LocalRegistryAndDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * The JBoss Remoting-based transport provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class RemoteTransportProvider implements EJBTransportProvider {

    static final AttachmentKey<RemoteEJBReceiver> ATTACHMENT_KEY = new AttachmentKey<>();

    private final ServiceRegistry persistentClusterRegistry;
    private final DiscoveryProvider persistentClusterDiscoveryProvider;
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<EJBClientChannel.ClusterDiscKey, ServiceRegistration>>> clusterRegistrationsMap = new ConcurrentHashMap<>();

    public RemoteTransportProvider() {
        final LocalRegistryAndDiscoveryProvider provider = new LocalRegistryAndDiscoveryProvider();
        persistentClusterDiscoveryProvider = provider;
        persistentClusterRegistry = ServiceRegistry.create(provider);
    }

    public void notifyRegistered(final EJBReceiverContext receiverContext) {
        final EJBClientContext clientContext = receiverContext.getClientContext();
        clientContext.putAttachmentIfAbsent(ATTACHMENT_KEY, new RemoteEJBReceiver(this, receiverContext, persistentClusterRegistry, clusterRegistrationsMap));
    }

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

    public EJBReceiver getReceiver(final EJBReceiverContext receiverContext, final String uriScheme) throws IllegalArgumentException {
        switch (uriScheme) {
            case "remote":
            case "remote+http":
            case "remote+https":
            // compatibility
            case "remoting":
            case "http-remoting":
            case "https-remoting": {
                final RemoteEJBReceiver receiver = receiverContext.getClientContext().getAttachment(ATTACHMENT_KEY);
                if (receiver != null) {
                    return receiver;
                }
                // else fall through
            }
            default: {
                throw new IllegalArgumentException("Unsupported EJB receiver protocol " + uriScheme);
            }
        }
    }

    DiscoveryProvider getClusterDiscoveryProvider() {
        return persistentClusterDiscoveryProvider;
    }
}
