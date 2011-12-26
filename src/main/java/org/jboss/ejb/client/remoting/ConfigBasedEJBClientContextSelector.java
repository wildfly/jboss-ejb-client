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
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;

import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

/**
 * An EJB client context selector which parses a properties file to create {@link org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver}s
 * out of the properties configured in that file.
 * <p/>
 * By default this selector looks for a file named <code>jboss-ejb-client.properties</code> in the classpath of the application. The
 * location and the name of the file can be explicitly specified by passing setting the value for <code>jboss.ejb.client.properties.file.path</code>
 * system property. If this system property is set then this selector uses the value as the file path for the EJB client
 * context configuration properties file and <i></i>doesn't</i> further look for the <code>jboss-ejb-client.properties</code>
 * in the classpath.
 * <p/>
 * Applications can also disable classpath scanning of <code>jboss-ejb-client.properties</code>, by this selector,
 * by setting the <code>jboss.ejb.client.properties.skip.classloader.scan</code> system property to <code>true</code>
 *
 * @author Jaikiran Pai
 */
public class ConfigBasedEJBClientContextSelector implements ContextSelector<EJBClientContext> {

    private static final Logger logger = Logger.getLogger(ConfigBasedEJBClientContextSelector.class);

    private final Properties ejbClientProperties;
    private final EJBClientContext ejbClientContext;

    private Endpoint clientEndpoint;

    public ConfigBasedEJBClientContextSelector(final Properties properties) {
        this.ejbClientProperties = properties == null ? new Properties() : properties;
        // create a empty context
        this.ejbClientContext = EJBClientContext.create();
        // now setup the receivers (if any) for the context
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
        if (!RemotingConnectionConfigurator.containsRemotingConnectionConfigurations(this.ejbClientProperties)) {
            logger.debug("No remote connections configured in the EJB client configuration properties");
            return;
        }
        // create the endpoint
        this.clientEndpoint = RemotingEndpointConfigurator.createFrom(this.ejbClientProperties);
        // setup a remote connection provider
        RemotingConnectionProviderConfigurator.configuratorFor(this.clientEndpoint).from(this.ejbClientProperties);

        // create connections
        final Collection<Connection> remotingConnections = RemotingConnectionConfigurator.configuratorFor(this.clientEndpoint).createConnections(this.ejbClientProperties);
        // register with the EJB client context
        for (final Connection remotingConnection : remotingConnections) {
            // register the connection with the client context to create an EJB receiver out of it
            this.ejbClientContext.registerConnection(remotingConnection);
        }
        logger.debug("Registered " + remotingConnections.size() + " remoting EJB receivers for EJB client context " + this.ejbClientContext);
    }

}
