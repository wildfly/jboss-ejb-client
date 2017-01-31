/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client.legacy;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
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
        }
    }
}
