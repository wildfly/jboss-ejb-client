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

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBClientContextIdentifier;
import org.jboss.ejb.client.EJBClientContextListener;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.IdentityEJBClientContextSelector;
import org.jboss.ejb.client.Logs;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;

/**
 * An EJB client context selector which uses {@link EJBClientConfiguration} to create {@link org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver}s.
 *
 * @author Jaikiran Pai
 */
public class ConfigBasedEJBClientContextSelector implements IdentityEJBClientContextSelector {

    private static final Logger logger = Logger.getLogger(ConfigBasedEJBClientContextSelector.class);

    protected final EJBClientConfiguration ejbClientConfiguration;
    protected final EJBClientContext ejbClientContext;
    private final RemotingEndpointManager remotingEndpointManager = new RemotingEndpointManager();
    private final RemotingConnectionManager remotingConnectionManager = new RemotingConnectionManager();

    private final ConcurrentMap<EJBClientContextIdentifier, EJBClientContext> identifiableContexts = new ConcurrentHashMap<EJBClientContextIdentifier, EJBClientContext>();

    private volatile boolean receiversSetup;


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
        this(ejbClientConfiguration, null);
    }

    /**
     * Creates a {@link ConfigBasedEJBClientContextSelector} using the passed <code>ejbClientConfiguration</code>.
     * <p/>
     * This constructor creates a {@link EJBClientContext} and uses the passed <code>ejbClientConfiguration</code> to create and
     * associated EJB receivers to that context. If the passed <code>ejbClientConfiguration</code> is null, then this selector will create a {@link EJBClientContext}
     * without any associated EJB receivers.
     *
     * @param ejbClientConfiguration The EJB client configuration to use
     * @param classLoader            The classloader that will be used to {@link EJBClientContext#create(org.jboss.ejb.client.EJBClientConfiguration, ClassLoader) create the EJBClientContext}
     */
    public ConfigBasedEJBClientContextSelector(final EJBClientConfiguration ejbClientConfiguration, final ClassLoader classLoader) {
        this.ejbClientConfiguration = ejbClientConfiguration;
        // create a empty context
        if (classLoader == null) {
            this.ejbClientContext = EJBClientContext.create(this.ejbClientConfiguration);
        } else {
            this.ejbClientContext = EJBClientContext.create(this.ejbClientConfiguration, classLoader);
        }
        // register a EJB client context listener which we will use to close endpoints/connections that we created,
        // when the EJB client context closes
        this.ejbClientContext.registerEJBClientContextListener(new ContextCloseListener());

    }

    @Override
    public EJBClientContext getCurrent() {
        if (this.receiversSetup) {
            return this.ejbClientContext;
        }
        synchronized (this) {
            if (this.receiversSetup) {
                return this.ejbClientContext;
            }
            try {
                // now setup the receivers (if any) for the context
                if (this.ejbClientConfiguration == null) {
                    logger.debugf("EJB client context %s will have no EJB receivers associated with it since there was no " +
                            "EJB client configuration available to create the receivers", this.ejbClientContext);
                    return this.ejbClientContext;
                }
                try {
                    this.setupEJBReceivers();
                } catch (IOException ioe) {
                    logger.warn("EJB client context " + this.ejbClientContext + " will have no EJB receivers due to an error setting up EJB receivers", ioe);
                }
            } finally {
                this.receiversSetup = true;
            }

        }
        return this.ejbClientContext;
    }

    private void setupEJBReceivers() throws IOException {
        if (!this.ejbClientConfiguration.getConnectionConfigurations().hasNext()) {
            // no connections configured so no EJB receivers to create
            return;
        }
        // create the endpoint
        final Endpoint endpoint = this.remotingEndpointManager.getEndpoint(this.ejbClientConfiguration.getEndpointName(), this.ejbClientConfiguration.getEndpointCreationOptions(), this.ejbClientConfiguration.getRemoteConnectionProviderCreationOptions());

        final Iterator<EJBClientConfiguration.RemotingConnectionConfiguration> connectionConfigurations = this.ejbClientConfiguration.getConnectionConfigurations();
        int successfulEJBReceiverRegistrations = 0;
        while (connectionConfigurations.hasNext()) {
            final EJBClientConfiguration.RemotingConnectionConfiguration connectionConfiguration = connectionConfigurations.next();
            final String host = connectionConfiguration.getHost();
            final String protocol = connectionConfiguration.getProtocol();
            final int port = connectionConfiguration.getPort();
            final int MAX_RECONNECT_ATTEMPTS = 65535; // TODO: Let's keep this high for now and later allow configuration and a smaller default value
            // create a re-connect handler (which will be used on connection breaking down)
            final ReconnectHandler reconnectHandler = new EJBClientContextConnectionReconnectHandler(ejbClientContext, endpoint, protocol, host, port, connectionConfiguration, MAX_RECONNECT_ATTEMPTS);
            try {
                // wait for the connection to be established
                final Connection connection = this.remotingConnectionManager.getConnection(endpoint, protocol, host, port, connectionConfiguration);
                // create a remoting EJB receiver for this connection
                final EJBReceiver remotingEJBReceiver = new RemotingConnectionEJBReceiver(connection, reconnectHandler, connectionConfiguration.getChannelCreationOptions(), protocol);
                // associate it with the client context
                this.ejbClientContext.registerEJBReceiver(remotingEJBReceiver);
                // keep track of successful registrations for logging purposes
                successfulEJBReceiverRegistrations++;
            } catch (Exception e) {
                // just log the warn but don't throw an exception. Move onto the next connection configuration (if any)
                logger.warn("Could not register a EJB receiver for connection to " + host + ":" + port, e);
                // add a reconnect handler for this connection
                if (reconnectHandler != null) {
                    this.ejbClientContext.registerReconnectHandler(reconnectHandler);
                    logger.debug("Registered a reconnect handler in EJB client context " + this.ejbClientContext + " for remote://" + host + ":" + port);
                }

            }
        }
        logger.debug("Registered " + successfulEJBReceiverRegistrations + " remoting EJB receivers for EJB client context " + this.ejbClientContext);
    }

    @Override
    public void registerContext(final EJBClientContextIdentifier identifier, final EJBClientContext context) {
        final EJBClientContext previousRegisteredContext = this.identifiableContexts.putIfAbsent(identifier, context);
        if (previousRegisteredContext != null) {
            throw Logs.MAIN.ejbClientContextAlreadyRegisteredForIdentifier(identifier);
        }
    }

    @Override
    public EJBClientContext unRegisterContext(final EJBClientContextIdentifier identifier) {
        return this.identifiableContexts.remove(identifier);
    }

    @Override
    public EJBClientContext getContext(final EJBClientContextIdentifier identifier) {
        return this.identifiableContexts.get(identifier);
    }

    private class ContextCloseListener implements EJBClientContextListener {

        @Override
        public void contextClosed(EJBClientContext ejbClientContext) {
            // close the endpoint and connection manager we had used to create the endpoints and connections
            // for the EJB client context that just closed
            remotingConnectionManager.safeClose();
            remotingEndpointManager.safeClose();
        }

        @Override
        public void receiverRegistered(EJBReceiverContext receiverContext) {
        }

        @Override
        public void receiverUnRegistered(EJBReceiverContext receiverContext) {
        }
    }
}
