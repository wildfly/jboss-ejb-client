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

import java.util.function.Consumer;

import org.jboss.ejb.client.EJBClientContext;
import org.kohsuke.MetaInfServices;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryRequest;
import org.wildfly.discovery.spi.ExternalDiscoveryConfigurator;
import org.wildfly.discovery.spi.RegistryProvider;

@MetaInfServices
public final class RemoteEJBDiscoveryConfigurator implements ExternalDiscoveryConfigurator {
    public RemoteEJBDiscoveryConfigurator() {
    }

    public void configure(final Consumer<DiscoveryProvider> discoveryProviderConsumer, final Consumer<RegistryProvider> registryProviderConsumer) {
        discoveryProviderConsumer.accept((serviceType, filterSpec, result) -> {
            final RemoteEJBReceiver receiver = EJBClientContext.getCurrent().getAttachment(RemoteTransportProvider.ATTACHMENT_KEY);
            if (receiver == null) {
                result.complete();
                return DiscoveryRequest.NULL;
            }
            return receiver.getDiscoveredNodeRegistry().discover(serviceType, filterSpec, result);
        });
    }
}
