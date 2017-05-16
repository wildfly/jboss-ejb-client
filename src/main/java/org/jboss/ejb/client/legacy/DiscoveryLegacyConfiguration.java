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

package org.jboss.ejb.client.legacy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.ClusterNodeSelector;
import org.jboss.ejb.client.EJBClientContext;
import org.kohsuke.MetaInfServices;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceRegistration;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.impl.StaticDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.discovery.spi.ExternalDiscoveryConfigurator;
import org.wildfly.discovery.spi.RegistryProvider;

/**
 * The interface to merge EJB properties into the discovery configuration.
 *
 * We want to make any information concerning configured clustered nodes available via abstract ServiceURLs.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href='mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
@MetaInfServices
public final class DiscoveryLegacyConfiguration implements ExternalDiscoveryConfigurator {
    public void configure(final Consumer<DiscoveryProvider> discoveryProviderConsumer, final Consumer<RegistryProvider> registryProviderConsumer) {
        final JBossEJBProperties ejbProperties = JBossEJBProperties.getCurrent();
        if (ejbProperties == null) {
            return;
        }

        final List<ServiceURL> list = new ArrayList<>();

        // for each node within a cluster, create a ServiceURL representing that cluster node
        //   service:ejb.jboss:node:<node name>;cluster=<cluster name>
        for (Map.Entry<String, JBossEJBProperties.ClusterConfiguration> entry : ejbProperties.getClusterConfigurations().entrySet()) {
            final String clusterName = entry.getKey();
            final JBossEJBProperties.ClusterConfiguration configuration = entry.getValue();

            final AttributeValue clusterValue = AttributeValue.fromString(clusterName);
            for (JBossEJBProperties.ClusterNodeConfiguration nodeConfiguration : configuration.getNodeConfigurations()) {
                final String nodeName = nodeConfiguration.getNodeName();
                // construct an abstract ServiceURL mapping cluster -> node
                final ServiceURL.Builder abstractBuilder = new ServiceURL.Builder();
                abstractBuilder.setAbstractType("ejb");
                abstractBuilder.setAbstractTypeAuthority("jboss");
                abstractBuilder.addAttribute(EJBClientContext.FILTER_ATTR_CLUSTER, clusterValue);
                try {
                    abstractBuilder.setUri(new URI("node", nodeName, null));
                } catch (URISyntaxException e) {
                    Logs.REMOTING.trace("Ignoring cluster node " + nodeName + " because the URI failed to be built", e);
                    continue;
                }
                // add the new ServiceURL to our provider
                list.add(abstractBuilder.create());
            }
        }

        if (! list.isEmpty()) {
            Logs.MAIN.legacyEJBPropertiesDiscoveryConfigurationInUse();
            discoveryProviderConsumer.accept(new StaticDiscoveryProvider(list));
        }
    }
}
