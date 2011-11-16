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

package org.jboss.ejb.client;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import org.jboss.ejb.client.remoting.IoFutureHelper;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.xnio.IoFuture;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;

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
class ConfigBasedEJBClientContextSelector implements ContextSelector<EJBClientContext> {

    private static final Logger logger = Logger.getLogger(ConfigBasedEJBClientContextSelector.class);

    private static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS = 5000;

    private static final String EJB_CLIENT_PROPS_FILE_SYS_PROPERTY = "jboss.ejb.client.properties.file.path";
    private static final String EJB_CLIENT_PROPS_SKIP_CLASSLOADER_SCAN_SYS_PROPERTY = "jboss.ejb.client.properties.skip.classloader.scan";

    private static final String EJB_CLIENT_PROPS_FILE_NAME = "jboss-ejb-client.properties";

    private static final String EJB_CLIENT_PROP_KEY_ENDPOINT_NAME = "endpoint.name";
    private static final String EJB_CLIENT_DEFAULT_ENDPOINT_NAME = "config-based-ejb-client-endpoint";

    private static final String ENDPOINT_CREATION_OPTIONS_PREFIX = "endpoint.create.options.";
    private static final String REMOTE_CONNECTION_PROVIDER_CREATE_OPTIONS_PREFIX = "remote.connectionprovider.create.options.";
    private static final String REMOTE_CONNECTIONS_PROP_KEY = "remote.connections";

    // The default options that will be used (unless overridden by the config file) for endpoint creation
    private static final OptionMap DEFAULT_ENDPOINT_CREATION_OPTIONS = OptionMap.create(Options.THREAD_DAEMON, true);

    // The default options that will be used (unless overridden by the config file) while creating a connection
    private static final OptionMap DEFAULT_CONNECTION_CREATION_OPTIONS = OptionMap.EMPTY;

    // The default options that will be used (unless overridden by the config file) while adding a remote connection
    // provider to the endpoint
    private static final OptionMap DEFAULT_CONNECTION_PROVIDER_CREATION_OPTIONS = OptionMap.EMPTY;

    static final ConfigBasedEJBClientContextSelector INSTANCE = new ConfigBasedEJBClientContextSelector();

    private final EJBClientContext ejbClientContext;

    private Endpoint clientEndpoint;

    private ConfigBasedEJBClientContextSelector() {
        // create a empty context
        this.ejbClientContext = EJBClientContext.create();
        // now setup the receivers (if any) for the context
        this.setupEJBReceivers();
    }

    @Override
    public EJBClientContext getCurrent() {
        return this.ejbClientContext;
    }

    private void setupEJBReceivers() {
        // Find EJB client properties (if any)
        final Properties ejbClientProperties = this.findEJBClientProperties();
        if (ejbClientProperties == null) {
            // no properties, so nothing to do, the client context will have no receivers associated
            logger.debug("No " + EJB_CLIENT_PROPS_FILE_NAME + " found in classpath and no " + EJB_CLIENT_PROPS_FILE_SYS_PROPERTY + " system property set. " +
                    "No EJB receivers will be associated with EJB client context " + this.ejbClientContext);
            return;
        }
        // create connections
        final Collection<Connection> remotingConnections = this.createConnections(ejbClientProperties);
        // register with the EJB client context
        for (final Connection remotingConnection : remotingConnections) {
            // register the connection with the client context to create an EJB receiver out of it
            this.ejbClientContext.registerConnection(remotingConnection);
        }
        logger.debug("Registered " + remotingConnections.size() + " remoting EJB receivers for EJB client context " + this.ejbClientContext);
    }

    private Properties findEJBClientProperties() {
        // check system property
        final String ejbClientPropsFilePath = SecurityActions.getSystemProperty(EJB_CLIENT_PROPS_FILE_SYS_PROPERTY);
        if (ejbClientPropsFilePath != null) {
            //
            final InputStream fileStream;
            try {
                fileStream = new FileInputStream(ejbClientPropsFilePath);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Failed to find EJB client configuration file specified in " + EJB_CLIENT_PROPS_FILE_SYS_PROPERTY + " system property", e);
            }
            final Properties ejbClientProps = new Properties();
            try {
                ejbClientProps.load(fileStream);
                return ejbClientProps;

            } catch (IOException e) {
                throw new RuntimeException("Error reading EJB client properties file " + ejbClientPropsFilePath, e);
            }
        }
        // if classpath scan is disabled then skip looking for jboss-ejb-client.properties file in the classpath
        final String skipClasspathScan = SecurityActions.getSystemProperty(EJB_CLIENT_PROPS_SKIP_CLASSLOADER_SCAN_SYS_PROPERTY);
        if (skipClasspathScan != null && Boolean.valueOf(skipClasspathScan.trim())) {
            logger.debug(EJB_CLIENT_PROPS_SKIP_CLASSLOADER_SCAN_SYS_PROPERTY + " system property is set. " +
                    "Skipping classloader search for " + EJB_CLIENT_PROPS_FILE_NAME);
            return null;
        }
        final ClassLoader classLoader = getClientClassLoader();
        logger.debug("Looking for " + EJB_CLIENT_PROPS_FILE_NAME + " using classloader " + classLoader);
        // find from classloader
        final InputStream clientPropsInputStream = classLoader.getResourceAsStream(EJB_CLIENT_PROPS_FILE_NAME);
        if (clientPropsInputStream != null) {
            logger.debug("Found " + EJB_CLIENT_PROPS_FILE_NAME + " using classloader " + classLoader);
            final Properties clientProps = new Properties();
            try {
                clientProps.load(clientPropsInputStream);
                return clientProps;

            } catch (IOException e) {
                throw new RuntimeException("Could not load " + EJB_CLIENT_PROPS_FILE_NAME, e);
            }
        }
        return null;
    }

    private Collection<Connection> createConnections(final Properties ejbClientProperties) {
        final String remoteConnectionNames = (String) ejbClientProperties.get(REMOTE_CONNECTIONS_PROP_KEY);
        // no connections configured, nothing to do!
        if (remoteConnectionNames == null || remoteConnectionNames.trim().isEmpty()) {
            logger.debug("No remoting connections configured in EJB client configuration file");
            return Collections.emptySet();
        }
        // parse the comma separated string of connection names
        final StringTokenizer tokenizer = new StringTokenizer(remoteConnectionNames, ",");
        final Collection<Connection> remotingConnections = new ArrayList<Connection>();
        while (tokenizer.hasMoreTokens()) {
            final String connectionName = tokenizer.nextToken().trim();
            if (connectionName.isEmpty()) {
                continue;
            }
            Connection connection = null;
            try {
                connection = this.createConnection(connectionName, ejbClientProperties);
            } catch (Exception e) {
                logger.error("Could not create connection for connection named " + connectionName, e);
            }
            if (connection == null) {
                logger.info("Connection " + connectionName + " will not be available in EJB client context " + this.ejbClientContext);
                continue;
            }
            logger.debug("Connection " + connection + " successfully created for connection named " + connectionName);
            // successful connection creation, add it to the list
            remotingConnections.add(connection);
        }

        return remotingConnections;
    }

    private void createEndpoint(final Properties ejbClientProperties) throws IOException {
        final String clientEndpointName = ejbClientProperties.getProperty(EJB_CLIENT_PROP_KEY_ENDPOINT_NAME, EJB_CLIENT_DEFAULT_ENDPOINT_NAME);
        final OptionMap endPointCreationOptionsFromConfiguration = this.getOptionMapFromProperties(ejbClientProperties, ENDPOINT_CREATION_OPTIONS_PREFIX);
        // merge with defaults
        final OptionMap endPointCreationOptions = this.mergeWithDefaults(DEFAULT_ENDPOINT_CREATION_OPTIONS, endPointCreationOptionsFromConfiguration);
        // create the endpoint
        this.clientEndpoint = Remoting.createEndpoint(clientEndpointName, endPointCreationOptions);
        // add a connection provider for the "remote" URI scheme
        final OptionMap remoteConnectionProivderOptionsFromConfiguration = this.getOptionMapFromProperties(ejbClientProperties, REMOTE_CONNECTION_PROVIDER_CREATE_OPTIONS_PREFIX);
        // merge with defaults
        final OptionMap remoteConnectionProivderOptions = this.mergeWithDefaults(DEFAULT_CONNECTION_PROVIDER_CREATION_OPTIONS, remoteConnectionProivderOptionsFromConfiguration);
        this.clientEndpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), remoteConnectionProivderOptions);
    }

    private Connection createConnection(final String connectionName, final Properties ejbClientProperties) throws IOException, URISyntaxException {
        final Map<String, String> connectionSpecificProps = this.getConnectionSpecificProperties(connectionName, ejbClientProperties);
        if (connectionSpecificProps.isEmpty()) {
            return null;
        }
        // get "host" for the connection
        final String host = connectionSpecificProps.get("host");
        if (host == null || host.trim().isEmpty()) {
            logger.info("No host configured for connection named " + connectionName + ". Skipping connection creation");
            return null;
        }
        // get "port" for the connection
        final String portStringVal = connectionSpecificProps.get("port");
        if (portStringVal == null || portStringVal.trim().isEmpty()) {
            logger.info("No port configured for connection named " + connectionName + ". Skipping connection creation");
            return null;
        }
        final Integer port;
        try {
            port = Integer.parseInt(portStringVal.trim());
        } catch (NumberFormatException nfe) {
            logger.info("Incorrect port value: " + portStringVal + " specified for connection named " + connectionName + ". Skipping connection creation");
            return null;
        }
        // get connect options for the connection
        final String connectOptionsPrefix = this.getConnectionSpecificConnectOptionsPrefix(connectionName);
        final OptionMap connectOptionsFromConfiguration = this.getOptionMapFromProperties(ejbClientProperties, connectOptionsPrefix);
        // merge with defaults
        final OptionMap connectOptions = this.mergeWithDefaults(DEFAULT_CONNECTION_CREATION_OPTIONS, connectOptionsFromConfiguration);
        // create the connection, but first create the endpoint if it isn't already created
        if (this.clientEndpoint == null) {
            this.createEndpoint(ejbClientProperties);
        }
        long connectionTimeout = DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS;
        final String connectionTimeoutValue = connectionSpecificProps.get("connect.timeout");
        // if a connection timeout is specified, use it
        if (connectionTimeoutValue != null && !connectionTimeoutValue.trim().isEmpty()) {
            try {
                connectionTimeout = Long.parseLong(connectionTimeoutValue.trim());
            } catch (NumberFormatException nfe) {
                logger.info("Incorrect timeout value " + connectionTimeoutValue + " specified for connection named "
                        + connectionName + ". Falling back to default connection timeout value " + DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS + " milli secondss");
            }
        }
        final URI connectionURI = new URI("remote://" + host.trim() + ":" + port);
        // TODO: FIXME: The AnonymousCallbackHandler being passed here is a hack, till we have
        // a better way of configuring security via EJB client configuration file
        final IoFuture<Connection> futureConnection = this.clientEndpoint.connect(connectionURI, connectOptions, new AnonymousCallbackHandler());
        // wait for the connection to be established
        return IoFutureHelper.get(futureConnection, connectionTimeout, TimeUnit.MILLISECONDS);
    }

    private Map<String, String> getConnectionSpecificProperties(final String connectionName, final Properties ejbClientProperties) {
        final String connectionSpecificPropertyPrefix = this.getConnectionSpecificPrefix(connectionName);
        final Map<String, String> connectionSpecificProps = new HashMap<String, String>();
        for (final String fullPropName : ejbClientProperties.stringPropertyNames()) {
            if (fullPropName.startsWith(connectionSpecificPropertyPrefix)) {
                // strip the "prefix" from the full property name and just get the trailing part.
                // Example, If remote.connection.one.host is the full property name,
                // then this step will return "host" as the property name for the connection named "one".
                String propName = fullPropName.substring(connectionSpecificPropertyPrefix.length());
                // get the value of the (full) property name
                final String propValue = ejbClientProperties.getProperty(fullPropName);
                connectionSpecificProps.put(propName, propValue);
            }
        }
        return connectionSpecificProps;
    }

    private String getConnectionSpecificPrefix(final String connectionName) {
        return "remote.connection." + connectionName + ".";
    }

    private String getConnectionSpecificConnectOptionsPrefix(final String connectionName) {
        return "remote.connection." + connectionName + ".connect.options.";
    }

    private OptionMap getOptionMapFromProperties(final Properties properties, final String propertyPrefix) {
        final ClassLoader classLoader = getClientClassLoader();
        final OptionMap.Builder optionMapBuilder = OptionMap.builder().parseAll(properties, propertyPrefix, classLoader);
        final OptionMap optionMap = optionMapBuilder.getMap();
        logger.debug(propertyPrefix + " has the following options " + optionMap);
        return optionMap;
    }

    /**
     * Merges the passed <code>defaults</code> and the <code>overrides</code> to return a combined
     * {@link OptionMap}. If the passed <code>overrides</code> has a {@link Option} for
     * which matches the one in <code>defaults</code> then the default option value is ignored and instead the
     * overridden one is added to the combined {@link OptionMap}. If however, the <code>overrides</code> doesn't
     * contain a option which is present in the <code>defaults</code>, then the default option is added to the
     * combined {@link OptionMap}
     *
     * @param defaults  The default options
     * @param overrides The overridden options
     * @return
     */
    private OptionMap mergeWithDefaults(final OptionMap defaults, final OptionMap overrides) {
        // copy all the overrides
        final OptionMap.Builder combinedOptionsBuilder = OptionMap.builder().addAll(overrides);
        // Skip all the defaults which have been overridden and just add the rest of the defaults
        // to the combined options
        for (final Option defaultOption : defaults) {
            if (combinedOptionsBuilder.getMap().contains(defaultOption)) {
                continue;
            }
            final Object defaultValue = defaults.get(defaultOption);
            combinedOptionsBuilder.set(defaultOption, defaultValue);
        }
        final OptionMap combinedOptions = combinedOptionsBuilder.getMap();
        if (logger.isTraceEnabled()) {
            logger.trace("Options " + overrides + " have been merged with defaults " + defaults + " to form " + combinedOptions);
        }
        return combinedOptions;
    }

    /**
     * If {@link Thread#getContextClassLoader()} is null then returns the classloader which loaded
     * {@link ConfigBasedEJBClientContextSelector}. Else returns the {@link Thread#getContextClassLoader()}
     *
     * @return
     */
    private static ClassLoader getClientClassLoader() {
        final ClassLoader tccl = SecurityActions.getContextClassLoader();
        if (tccl != null) {
            return tccl;
        }
        return ConfigBasedEJBClientContextSelector.class.getClassLoader();
    }

    // TODO: This is a hack for now, till we have a way to configure callback handlers
    // or other mechanism via the EJB client configuration file for connection creation
    private class AnonymousCallbackHandler implements CallbackHandler {

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName("anonymous");
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }
        }
    }
}
