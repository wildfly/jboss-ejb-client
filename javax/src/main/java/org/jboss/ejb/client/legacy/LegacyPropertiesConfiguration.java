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
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.ClusterNodeSelector;
import org.jboss.ejb.client.DeploymentNodeSelector;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.wildfly.common.function.ExceptionSupplier;
import org.xnio.OptionMap;

/**
 * Client configuration which is configured through {@link Properties}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class LegacyPropertiesConfiguration {

    public static void configure(final EJBClientContext.Builder builder) {
        final JBossEJBProperties properties = JBossEJBProperties.getCurrent();
        if (properties != null) {
            Logs.MAIN.legacyEJBPropertiesEJBConfigurationInUse();

            final List<JBossEJBProperties.ConnectionConfiguration> connectionList = properties.getConnectionList();
            for (JBossEJBProperties.ConnectionConfiguration connectionConfiguration : connectionList) {
                final String host = connectionConfiguration.getHost();
                if (host == null) {
                    continue;
                }
                final int port = connectionConfiguration.getPort();
                if (port == -1) {
                    continue;
                }
                final OptionMap connectionOptions = connectionConfiguration.getConnectionOptions();
                final URI uri = CommonLegacyConfiguration.getUri(connectionConfiguration, connectionOptions);
                if (uri == null) {
                    continue;
                }
                final EJBClientConnection.Builder connectionBuilder = new EJBClientConnection.Builder();
                connectionBuilder.setDestination(uri);
                builder.addClientConnection(connectionBuilder.build());
            }

            final List<JBossEJBProperties.HttpConnectionConfiguration> httpConnectionList = properties.getHttpConnectionList();
            for (JBossEJBProperties.HttpConnectionConfiguration httpConnection : httpConnectionList) {
                final String uriString = httpConnection.getUri();
                final URI uri;
                try {
                    uri = new URI(uriString);
                } catch (URISyntaxException e) {
                    Logs.MAIN.skippingHttpConnectionCreationDueToInvalidUri(uriString);
                    continue;
                }
                final EJBClientConnection.Builder connectionBuilder = new EJBClientConnection.Builder();
                connectionBuilder.setDestination(uri);
                builder.addClientConnection(connectionBuilder.build());
            }

            final ExceptionSupplier<DeploymentNodeSelector, ReflectiveOperationException> deploymentNodeSelectorSupplier = properties.getDeploymentNodeSelectorSupplier();
            if (deploymentNodeSelectorSupplier != null) {
                final DeploymentNodeSelector deploymentNodeSelector;
                try {
                    deploymentNodeSelector = deploymentNodeSelectorSupplier.get();
                } catch (ReflectiveOperationException e) {
                    throw Logs.MAIN.cannotInstantiateDeploymentNodeSelector(properties.getDeploymentNodeSelectorClassName(), e);
                }
                builder.setDeploymentNodeSelector(deploymentNodeSelector);
            }

            Map<String, JBossEJBProperties.ClusterConfiguration> clusters = properties.getClusterConfigurations();
            if (clusters != null) {
                for (JBossEJBProperties.ClusterConfiguration cluster : clusters.values()) {
                    ExceptionSupplier<ClusterNodeSelector, ReflectiveOperationException> selectorSupplier = cluster.getClusterNodeSelectorSupplier();
                    if (selectorSupplier != null) {
                        try {
                            builder.setClusterNodeSelector(selectorSupplier.get());
                        } catch (ReflectiveOperationException e) {
                            throw Logs.MAIN.cannotInstantiateClustertNodeSelector(cluster.getClusterNodeSelectorClassName(), e);
                        }
                        // We only support one selector currently
                        break;
                    }
                }
                for (JBossEJBProperties.ClusterConfiguration cluster : clusters.values()) {
                    long maximumNodes = cluster.getMaximumAllowedConnectedNodes();
                    if (maximumNodes != -1) {
                        builder.setMaximumConnectedClusterNodes(Long.valueOf(maximumNodes).intValue());
                        // Can be only set once
                        break;
                    }
                }
            }

            if (properties.getInvocationTimeout() != -1L) {
                builder.setInvocationTimeout(properties.getInvocationTimeout());
            }
        }
    }
}
