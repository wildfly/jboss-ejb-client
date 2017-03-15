/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
