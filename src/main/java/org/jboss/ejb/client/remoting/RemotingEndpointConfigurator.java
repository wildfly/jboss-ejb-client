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
import org.jboss.remoting3.Remoting;
import org.xnio.OptionMap;
import org.xnio.Options;

import java.io.IOException;
import java.util.Properties;

/**
 * @author Jaikiran Pai
 */
class RemotingEndpointConfigurator extends RemotingConfigurator {

    static final String EJB_CLIENT_PROP_KEY_ENDPOINT_NAME = "endpoint.name";
    private static final String EJB_CLIENT_DEFAULT_ENDPOINT_NAME = "config-based-ejb-client-endpoint";

    static final String ENDPOINT_CREATION_OPTIONS_PREFIX = "endpoint.create.options.";
    // The default options that will be used (unless overridden by the config file) for endpoint creation
    private static final OptionMap DEFAULT_ENDPOINT_CREATION_OPTIONS = OptionMap.create(Options.THREAD_DAEMON, true);


    static Endpoint createFrom(final Properties properties) throws IOException {
        if (properties == null) {
            throw new IllegalArgumentException("Properties cannot be null while creating a remoting endpoint");
        }
        final String clientEndpointName = properties.getProperty(EJB_CLIENT_PROP_KEY_ENDPOINT_NAME, EJB_CLIENT_DEFAULT_ENDPOINT_NAME);
        final OptionMap endPointCreationOptionsFromConfiguration = getOptionMapFromProperties(properties, ENDPOINT_CREATION_OPTIONS_PREFIX, getClientClassLoader());
        // merge with defaults
        final OptionMap endPointCreationOptions = mergeWithDefaults(DEFAULT_ENDPOINT_CREATION_OPTIONS, endPointCreationOptionsFromConfiguration);
        // create the endpoint
        final Endpoint endpoint = Remoting.createEndpoint(clientEndpointName, endPointCreationOptions);

        return endpoint;
    }

    /**
     * If {@link Thread#getContextClassLoader()} is null then returns the classloader which loaded
     * {@link RemotingEndpointConfigurator} class. Else returns the {@link Thread#getContextClassLoader()}
     *
     * @return
     */
    private static ClassLoader getClientClassLoader() {
        final ClassLoader tccl = SecurityActions.getContextClassLoader();
        if (tccl != null) {
            return tccl;
        }
        return RemotingEndpointConfigurator.class.getClassLoader();
    }


}
