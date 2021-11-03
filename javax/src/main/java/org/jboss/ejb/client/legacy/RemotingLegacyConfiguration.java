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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.jboss.ejb._private.Logs;
import org.jboss.remoting3.ConnectionBuilder;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointBuilder;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.EndpointConfigurator;
import org.kohsuke.MetaInfServices;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;

/**
 * The interface to merge EJB properties into the Remoting configuration.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class RemotingLegacyConfiguration implements EndpointConfigurator {

    public Endpoint getConfiguredEndpoint() {
        final JBossEJBProperties properties = JBossEJBProperties.getCurrent();

        if (properties == null) {
            return null;
        }

        Logs.MAIN.legacyEJBPropertiesRemotingConfigurationInUse();

        final EndpointBuilder endpointBuilder = Endpoint.builder();
        final String endpointName = properties.getEndpointName();
        if (endpointName != null) {
            endpointBuilder.setEndpointName(endpointName);
        }
        OptionMap endpointCreationOptions = properties.getEndpointCreationOptions();
        if (endpointCreationOptions != null && endpointCreationOptions.size() > 0) {
            if (! endpointCreationOptions.contains(Options.THREAD_DAEMON)) {
                endpointCreationOptions = OptionMap.builder().addAll(endpointCreationOptions).set(Options.THREAD_DAEMON, true).getMap();
            }
            endpointBuilder.buildXnioWorker(Xnio.getInstance()).populateFromOptions(endpointCreationOptions);
        }

        final List<JBossEJBProperties.ConnectionConfiguration> connectionList = properties.getConnectionList();
        List<URI> uris = new ArrayList<URI>();

        for (JBossEJBProperties.ConnectionConfiguration connectionConfiguration : connectionList) {
            final OptionMap connectionOptions = connectionConfiguration.getConnectionOptions();
            final URI uri = CommonLegacyConfiguration.getUri(connectionConfiguration, connectionOptions);
            if (uri == null) {
                continue;
            }
            if (connectionConfiguration.isConnectEagerly()) {
                uris.add(uri);
            }
            final ConnectionBuilder connectionBuilder = endpointBuilder.addConnection(uri);
            connectionBuilder.setHeartbeatInterval(
                    connectionOptions.get(RemotingOptions.HEARTBEAT_INTERVAL, RemotingOptions.DEFAULT_HEARTBEAT_INTERVAL));
            if (connectionOptions.get(Options.READ_TIMEOUT, -1) != -1) {
                connectionBuilder.setReadTimeout(connectionOptions.get(Options.READ_TIMEOUT, -1));
            }
            if (connectionOptions.get(Options.WRITE_TIMEOUT, -1) != -1) {
                connectionBuilder.setWriteTimeout(connectionOptions.get(Options.WRITE_TIMEOUT, -1));
            }
            connectionBuilder.setTcpKeepAlive(connectionOptions.get(Options.KEEP_ALIVE, false));
        }

        final Endpoint endpoint;
        try {
            endpoint = endpointBuilder.build();
        } catch (IOException e) {
            throw Logs.MAIN.failedToConstructEndpoint(e);
        }

        for (URI uri : uris) {
            endpoint.getConnection(uri, "ejb", "jboss");
        }
        return endpoint;
    }
}
