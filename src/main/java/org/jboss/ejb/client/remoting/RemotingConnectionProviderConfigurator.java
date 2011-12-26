/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.xnio.OptionMap;

import java.io.IOException;
import java.util.Properties;

/**
 * @author Jaikiran Pai
 */
class RemotingConnectionProviderConfigurator extends RemotingConfigurator {

    // The default options that will be used (unless overridden by the config file) while adding a remote connection
    // provider to the endpoint
    private static final OptionMap DEFAULT_CONNECTION_PROVIDER_CREATION_OPTIONS = OptionMap.EMPTY;
    private static final String REMOTE_CONNECTION_PROVIDER_CREATE_OPTIONS_PREFIX = "remote.connectionprovider.create.options.";


    private final Endpoint endpoint;

    private RemotingConnectionProviderConfigurator(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Returns a {@link RemotingConnectionProviderConfigurator} for the passed <code>endpoint</code>,
     * which can then be used for configuring a connection provider for the remote:// URI scheme for the
     * <code>endpoint</code>
     *
     * @param endpoint The remoting endpoint
     * @return
     */
    static RemotingConnectionProviderConfigurator configuratorFor(final Endpoint endpoint) {
        return new RemotingConnectionProviderConfigurator(endpoint);
    }

    /**
     * Configures a {@link org.jboss.remoting3.spi.ConnectionProvider} for the remote:// URI scheme, on the
     * {@link Endpoint} represented by this {@link RemotingConnectionProviderConfigurator}
     *
     * @param properties The properties which will be used for configuring the remote connection provider
     * @return Returns back the {@link RemotingConnectionProviderConfigurator} for whose {@link Endpoint}, we just configured a
     *         connection provider
     * @throws IOException
     */
    RemotingConnectionProviderConfigurator from(final Properties properties) throws IOException {
        // add a connection provider for the "remote" URI scheme
        final OptionMap remoteConnectionProivderOptionsFromConfiguration = getOptionMapFromProperties(properties, REMOTE_CONNECTION_PROVIDER_CREATE_OPTIONS_PREFIX, getClientClassLoader());
        // merge with defaults
        final OptionMap remoteConnectionProivderOptions = mergeWithDefaults(DEFAULT_CONNECTION_PROVIDER_CREATION_OPTIONS, remoteConnectionProivderOptionsFromConfiguration);
        this.endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), remoteConnectionProivderOptions);

        return this;
    }

    /**
     * If {@link Thread#getContextClassLoader()} is null then returns the classloader which loaded
     * {@link RemotingConnectionProviderConfigurator} class. Else returns the {@link Thread#getContextClassLoader()}
     *
     * @return
     */
    private static ClassLoader getClientClassLoader() {
        final ClassLoader tccl = SecurityActions.getContextClassLoader();
        if (tccl != null) {
            return tccl;
        }
        return RemotingConnectionProviderConfigurator.class.getClassLoader();
    }

}
