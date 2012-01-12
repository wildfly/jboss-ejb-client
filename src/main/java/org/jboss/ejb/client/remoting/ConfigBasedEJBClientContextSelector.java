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

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * An EJB client context selector which uses {@link EJBClientConfiguration} to create {@link org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver}s.
 *
 * @author Jaikiran Pai
 */
public class ConfigBasedEJBClientContextSelector implements ContextSelector<EJBClientContext> {

    private static final Logger logger = Logger.getLogger(ConfigBasedEJBClientContextSelector.class);

    private final EJBClientConfiguration ejbClientConfiguration;
    private final EJBClientContext ejbClientContext;

    /**
     * Creates a {@link ConfigBasedEJBClientContextSelector} using the passed <code>ejbClientConfiguration</code>.
     * <p/>
     * This constructor creates a {@link EJBClientContext} and uses the passed <code>ejbClientConfiguration</code> to create and
     * associated EJB receivers to that context. If the passed <code>ejbClientConfiguration</code> is null, then this selector will create a {@link EJBClientContext}
     * without any associated EJB receivers.
     *
     * @param ejbClientConfiguration The EJB client configuration to use
     */
    public ConfigBasedEJBClientContextSelector(final EJBClientConfiguration ejbClientConfiguration) {
        this.ejbClientConfiguration = ejbClientConfiguration;
        // create a empty context
        this.ejbClientContext = EJBClientContext.create(this.ejbClientConfiguration);
        // now setup the receivers (if any) for the context
        if (this.ejbClientConfiguration == null) {
            logger.debug("EJB client context " + this.ejbClientContext + " will have no EJB receivers associated with it since there was no " +
                    "EJB client configuration available to create the receivers");
            return;
        }
        try {
            this.setupEJBReceivers();
        } catch (IOException ioe) {
            logger.warn("EJB client context " + this.ejbClientContext + " will have no EJB receivers due to an error setting up EJB receivers", ioe);
        }
    }

    @Override
    public EJBClientContext getCurrent() {
        return this.ejbClientContext;
    }

    private void setupEJBReceivers() throws IOException {
        if (!this.ejbClientConfiguration.getConnectionConfigurations().hasNext()) {
            // no connections configured so no EJB receivers to create
            return;
        }
        // create the endpoint
        final Endpoint endpoint = Remoting.createEndpoint(this.ejbClientConfiguration.getEndpointName(), this.ejbClientConfiguration.getEndpointCreationOptions());
        // register the remote connection provider
        final OptionMap remoteConnectionProviderOptions = this.ejbClientConfiguration.getRemoteConnectionProviderCreationOptions();
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), remoteConnectionProviderOptions);

        final Iterator<EJBClientConfiguration.RemotingConnectionConfiguration> connectionConfigurations = this.ejbClientConfiguration.getConnectionConfigurations();
        int successfulEJBReceiverRegistrations = 0;
        while (connectionConfigurations.hasNext()) {
            final EJBClientConfiguration.RemotingConnectionConfiguration connectionConfiguration = connectionConfigurations.next();
            final String host = connectionConfiguration.getHost();
            final int port = connectionConfiguration.getPort();
            try {
                final URI connectionURI = new URI("remote://" + host + ":" + port);
                final IoFuture<Connection> futureConnection = endpoint.connect(connectionURI, connectionConfiguration.getConnectionCreationOptions(), connectionConfiguration.getCallbackHandler());
                // wait for the connection to be established
                final Connection connection = IoFutureHelper.get(futureConnection, connectionConfiguration.getConnectionTimeout(), TimeUnit.MILLISECONDS);
                // create a remoting EJB receiver for this connection
                final EJBReceiver remotingEJBReceiver = new RemotingConnectionEJBReceiver(connection);
                // associate it with the client context
                this.ejbClientContext.registerEJBReceiver(remotingEJBReceiver);
                // keep track of successful registrations for logging purposes
                successfulEJBReceiverRegistrations++;
            } catch (Exception e) {
                // just log the warn but don't throw an exception. Move onto the next connection configuration (if any)
                logger.warn("Could not register a EJB receiver for connection to remote://" + host + ":" + port, e);
            }
        }
        logger.debug("Registered " + successfulEJBReceiverRegistrations + " remoting EJB receivers for EJB client context " + this.ejbClientContext);
    }

}
