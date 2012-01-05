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

import org.jboss.logging.Logger;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * A {@link RemotingEJBReceiversConfiguration} which is configured through {@link Properties}. Some well known
 * properties will be looked for in the {@link Properties} that is passed to the {@link #PropertiesBasedRemotingEJBReceiversConfiguration(java.util.Properties) constructor},
 * for setting up the configurations
 *
 * @author Jaikiran Pai
 */
class PropertiesBasedRemotingEJBReceiversConfiguration implements RemotingEJBReceiversConfiguration {

    private static final Logger logger = Logger.getLogger(PropertiesBasedRemotingEJBReceiversConfiguration.class);

    private static final String PROPERTY_KEY_ENDPOINT_NAME = "endpoint.name";
    private static final String DEFAULT_ENDPOINT_NAME = "config-based-ejb-client-endpoint";

    private static final String ENDPOINT_CREATION_OPTIONS_PREFIX = "endpoint.create.options.";
    // The default options that will be used (unless overridden by the config file) for endpoint creation
    private static final OptionMap DEFAULT_ENDPOINT_CREATION_OPTIONS = OptionMap.create(Options.THREAD_DAEMON, true);

    // The default options that will be used (unless overridden by the config file) while adding a remote connection
    // provider to the endpoint
    private static final OptionMap DEFAULT_CONNECTION_PROVIDER_CREATION_OPTIONS = OptionMap.EMPTY;
    private static final String REMOTE_CONNECTION_PROVIDER_CREATE_OPTIONS_PREFIX = "remote.connectionprovider.create.options.";

    private static final String PROPERTY_KEY_REMOTE_CONNECTIONS = "remote.connections";
    // The default options that will be used (unless overridden by the config file) while creating a connection
    private static final OptionMap DEFAULT_CONNECTION_CREATION_OPTIONS = OptionMap.EMPTY;
    private static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS = 5000;

    private static final String PROPERTY_KEY_USERNAME = "username";
    private static final String PROPERTY_KEY_PASSWORD = "password";
    private static final String PROPERTY_KEY_PASSWORD_BASE64 = "password.base64";
    private static final String PROPERTY_KEY_REALM = "realm";
    private static final String PROPERTY_KEY_CALLBACK_HANDLER_CLASS = "callback.handler.class";

    private static final String PROPERTY_KEY_CLUSTERS = "remote.clusters";


    private final Properties ejbReceiversConfigurationProperties;

    private String endPointName;
    private OptionMap endPointCreationOptions;
    private OptionMap remoteConnectionProviderCreationOptions;
    private CallbackHandler callbackHandler;
    private Collection<RemotingConnectionConfiguration> remotingConnectionConfigurations = new ArrayList<RemotingConnectionConfiguration>();
    private Map<String, RemotingClusterConfiguration> clusterConfigurations = new HashMap<String, RemotingClusterConfiguration>();

    PropertiesBasedRemotingEJBReceiversConfiguration(final Properties properties) {
        this.ejbReceiversConfigurationProperties = properties == null ? new Properties() : properties;
        // parse the properties and setup this configuration
        this.parseProperties();
    }

    @Override
    public String getEndpointName() {
        return this.endPointName;
    }

    @Override
    public OptionMap getEndpointCreationOptions() {
        return this.endPointCreationOptions;
    }

    @Override
    public OptionMap getRemoteConnectionProviderCreationOptions() {
        return this.remoteConnectionProviderCreationOptions;
    }

    @Override
    public CallbackHandler getCallbackHandler() {
        return this.callbackHandler;
    }

    @Override
    public Iterator<RemotingConnectionConfiguration> getConnectionConfigurations() {
        return this.remotingConnectionConfigurations.iterator();
    }

    @Override
    public Iterator<RemotingClusterConfiguration> getClusterConfigurations() {
        return this.clusterConfigurations.values().iterator();
    }

    @Override
    public RemotingClusterConfiguration getClusterConfiguration(String clusterName) {
        return this.clusterConfigurations.get(clusterName);
    }


    private void parseProperties() {
        // endpoint name
        this.endPointName = this.ejbReceiversConfigurationProperties.getProperty(PROPERTY_KEY_ENDPOINT_NAME, DEFAULT_ENDPOINT_NAME);

        // callback handler
        this.callbackHandler = this.getDefaultCallbackHandler();

        // endpoint creation options
        final OptionMap endPointCreationOptionsFromConfiguration = getOptionMapFromProperties(ejbReceiversConfigurationProperties, ENDPOINT_CREATION_OPTIONS_PREFIX, getClientClassLoader());
        // merge with defaults
        this.endPointCreationOptions = mergeWithDefaults(DEFAULT_ENDPOINT_CREATION_OPTIONS, endPointCreationOptionsFromConfiguration);

        // remote connection provider creation options
        final OptionMap remoteConnectionProviderOptionsFromConfiguration = getOptionMapFromProperties(ejbReceiversConfigurationProperties, REMOTE_CONNECTION_PROVIDER_CREATE_OPTIONS_PREFIX, getClientClassLoader());
        // merge with defaults
        this.remoteConnectionProviderCreationOptions = mergeWithDefaults(DEFAULT_CONNECTION_PROVIDER_CREATION_OPTIONS, remoteConnectionProviderOptionsFromConfiguration);

        // connection configurations
        this.parseConnectionConfigurations();

        // cluster configurations
        this.parseClusterConfigurations();

    }

    private OptionMap getOptionMapFromProperties(final Properties properties, final String propertyPrefix, final ClassLoader classLoader) {
        final OptionMap.Builder optionMapBuilder = OptionMap.builder().parseAll(properties, propertyPrefix, classLoader);
        final OptionMap optionMap = optionMapBuilder.getMap();
        logger.debug(propertyPrefix + " has the following options " + optionMap);
        return optionMap;
    }

    /**
     * Merges the passed <code>defaults</code> and the <code>overrides</code> to return a combined
     * {@link OptionMap}. If the passed <code>overrides</code> has a {@link org.xnio.Option} for
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
     * {@link PropertiesBasedRemotingEJBReceiversConfiguration} class. Else returns the {@link Thread#getContextClassLoader()}
     *
     * @return
     */
    private static ClassLoader getClientClassLoader() {
        final ClassLoader tccl = SecurityActions.getContextClassLoader();
        if (tccl != null) {
            return tccl;
        }
        return PropertiesBasedRemotingEJBReceiversConfiguration.class.getClassLoader();
    }

    private void parseClusterConfigurations() {
        final String clusterNames = (String) ejbReceiversConfigurationProperties.get(PROPERTY_KEY_CLUSTERS);
        // no clusters configured, nothing to do!
        if (clusterNames == null || clusterNames.trim().isEmpty()) {
            logger.debug("No clusters configured in properties");
            return;
        }
        // parse the comma separated string of cluster names
        final StringTokenizer tokenizer = new StringTokenizer(clusterNames, ",");
        while (tokenizer.hasMoreTokens()) {
            final String clusterName = tokenizer.nextToken().trim();
            if (clusterName.isEmpty()) {
                continue;
            }
            RemotingClusterConfiguration clusterConfiguration = null;
            try {
                clusterConfiguration = this.createClusterConfiguration(clusterName);
            } catch (Exception e) {
                logger.warn("Could not create cluster configuration for cluster named " + clusterName, e);
            }
            if (clusterConfiguration == null) {
                continue;
            }
            logger.debug("Cluster configuration for cluster " + clusterName + " successfully created");
            // add it to the cluster configuration map
            this.clusterConfigurations.put(clusterName, clusterConfiguration);
        }
    }

    private RemotingClusterConfiguration createClusterConfiguration(final String clusterName) {
        final String clusterSpecificPrefix = this.getClusterSpecificPrefix(clusterName);
        final Map<String, String> clusterSpecificProperties = this.getPropertiesWithPrefix(clusterSpecificPrefix);
        if (clusterSpecificProperties.isEmpty()) {
            return null;
        }
        // get "max-connected-nodes" for the cluster
        final String maxConnectedNodesStringVal = clusterSpecificProperties.get("max-allowed-connected-nodes");
        long maxAllowedConnectedNodes = 10; // default to 10
        if (maxConnectedNodesStringVal != null && !maxConnectedNodesStringVal.trim().isEmpty()) {
            try {
                maxAllowedConnectedNodes = Long.parseLong(maxConnectedNodesStringVal.trim());
            } catch (NumberFormatException nfe) {
                logger.info("Incorrect max-allowed-connected-nodes value: " + maxConnectedNodesStringVal + " specified for cluster named " + clusterName + ". Defaulting to " + maxAllowedConnectedNodes);
            }
        }
        // get the connection creation options applicable for all the nodes (unless explicitly overridden) in this
        // cluster
        final String connectOptionsPrefix = this.getClusterSpecificConnectOptionsPrefix(clusterName);
        final OptionMap connectOptionsFromConfiguration = getOptionMapFromProperties(ejbReceiversConfigurationProperties, connectOptionsPrefix, getClientClassLoader());
        // merge with defaults
        final OptionMap connectOptions = mergeWithDefaults(DEFAULT_CONNECTION_CREATION_OPTIONS, connectOptionsFromConfiguration);

        // get the connection timeout applicable for all nodes (unless explicitly overridden) in this cluster
        long connectionTimeout = DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS;
        final String connectionTimeoutValue = clusterSpecificProperties.get("connect.timeout");
        // if a connection timeout is specified, use it
        if (connectionTimeoutValue != null && !connectionTimeoutValue.trim().isEmpty()) {
            try {
                connectionTimeout = Long.parseLong(connectionTimeoutValue.trim());
            } catch (NumberFormatException nfe) {
                logger.info("Incorrect timeout value " + connectionTimeoutValue + " specified for cluster named "
                        + clusterName + ". Falling back to default connection timeout value " + DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS + " milli secondss");
            }
        }

        // create the CallbackHandler applicable for all nodes (unless explicitly overridden) in this cluster
        final CallbackHandler callbackHandler = createCallbackHandler(clusterSpecificProperties, this.getDefaultCallbackHandler());

        final RemotingClusterConfigurationImpl clusterConfiguration = new RemotingClusterConfigurationImpl(clusterName, maxAllowedConnectedNodes, connectOptions, callbackHandler, connectionTimeout);
        // parse the node configurations for this cluster
        final Collection<RemotingClusterNodeConfiguration> nodeConfigurations = this.parseClusterNodeConfigurations(clusterConfiguration, clusterSpecificProperties);
        // add them to the cluster configuration
        clusterConfiguration.addNodeConfigurations(nodeConfigurations);
        // return the cluster configuration
        return clusterConfiguration;
    }

    private Collection<RemotingClusterNodeConfiguration> parseClusterNodeConfigurations(final RemotingClusterConfiguration clusterConfiguration, final Map<String, String> clusterSpecificProperties) {
        final Collection<RemotingClusterNodeConfiguration> nodeConfigurations = new ArrayList<RemotingClusterNodeConfiguration>();
        for (final String key : clusterSpecificProperties.keySet()) {
            if (!key.startsWith("node.")) {
                continue;
            }
            final String keyWithoutNodeDotPrefix = key.substring(0, "node.".length());
            final int nextDotIndex = keyWithoutNodeDotPrefix.indexOf(".");
            if (nextDotIndex == -1) {
                continue;
            }
            final String nodeName = keyWithoutNodeDotPrefix.substring(0, nextDotIndex);
            // create a node configuration for the node name
            final RemotingClusterNodeConfiguration nodeConfiguration = this.createClusterNodeConfiguration(clusterConfiguration, nodeName);
            if (nodeConfiguration == null) {
                continue;
            }
            // add it to the collection to be returned
            nodeConfigurations.add(nodeConfiguration);
        }
        return nodeConfigurations;
    }

    private RemotingClusterNodeConfiguration createClusterNodeConfiguration(final RemotingClusterConfiguration clusterConfiguration, final String nodeName) {
        final String clusterName = clusterConfiguration.getClusterName();
        final String nodeSpecificPrefix = this.getClusterSpecificPrefix(clusterName) + "node." + nodeName + ".";
        // get the cluster node specific properties
        final Map<String, String> nodeSpecificProperties = this.getPropertiesWithPrefix(nodeSpecificPrefix);
        if (nodeSpecificProperties.isEmpty()) {
            return null;
        }
        // get the connection creation options for the cluster node
        final String connectOptionsPrefix = this.getClusterNodeSpecificConnectOptionsPrefix(clusterName, nodeName);
        final OptionMap connectOptionsFromConfiguration = getOptionMapFromProperties(ejbReceiversConfigurationProperties, connectOptionsPrefix, getClientClassLoader());
        // merge with defaults (== connection creation options applicable to the entire cluster)
        final OptionMap connectOptions = mergeWithDefaults(clusterConfiguration.getConnectionCreationOptions(), connectOptionsFromConfiguration);

        // get the connection timeout applicable for the cluster node
        long connectionTimeout = clusterConfiguration.getConnectionTimeout(); // default to the timeout applicable to the entire cluster
        final String connectionTimeoutValue = nodeSpecificProperties.get("connect.timeout");
        // if a connection timeout is specified, use it
        if (connectionTimeoutValue != null && !connectionTimeoutValue.trim().isEmpty()) {
            try {
                connectionTimeout = Long.parseLong(connectionTimeoutValue.trim());
            } catch (NumberFormatException nfe) {
                logger.info("Incorrect timeout value " + connectionTimeoutValue + " specified for node named " + nodeName + " in cluster "
                        + clusterName + ". Falling back to default connection timeout value " + connectionTimeout + " milli seconds");
            }
        }

        // create the CallbackHandler applicable for the cluster node (default to the callback handler applicable to the entire cluster)
        final CallbackHandler callbackHandler = createCallbackHandler(nodeSpecificProperties, clusterConfiguration.getCallbackHandler());

        return new RemotingClusterNodeConfigurationImpl(nodeName, connectOptions, callbackHandler, connectionTimeout);
    }

    private void parseConnectionConfigurations() {
        final String remoteConnectionNames = (String) ejbReceiversConfigurationProperties.get(PROPERTY_KEY_REMOTE_CONNECTIONS);
        // no connections configured, nothing to do!
        if (remoteConnectionNames == null || remoteConnectionNames.trim().isEmpty()) {
            logger.debug("No remoting connections configured in properties");
            return;
        }
        // parse the comma separated string of connection names
        final StringTokenizer tokenizer = new StringTokenizer(remoteConnectionNames, ",");
        while (tokenizer.hasMoreTokens()) {
            final String connectionName = tokenizer.nextToken().trim();
            if (connectionName.isEmpty()) {
                continue;
            }
            RemotingConnectionConfiguration connectionConfiguration = null;
            try {
                connectionConfiguration = this.createConnectionConfiguration(connectionName);
            } catch (Exception e) {
                logger.warn("Could not create connection for connection named " + connectionName, e);
            }
            if (connectionConfiguration == null) {
                continue;
            }
            logger.debug("Connection " + connectionConfiguration + " successfully created for connection named " + connectionName);
            // add it to the collection of connection configurations
            this.remotingConnectionConfigurations.add(connectionConfiguration);
        }
    }

    private RemotingConnectionConfiguration createConnectionConfiguration(final String connectionName) throws IOException, URISyntaxException {
        final String connectionSpecificPrefix = this.getConnectionSpecificPrefix(connectionName);
        final Map<String, String> connectionSpecificProps = this.getPropertiesWithPrefix(connectionSpecificPrefix);
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
        final OptionMap connectOptionsFromConfiguration = getOptionMapFromProperties(ejbReceiversConfigurationProperties, connectOptionsPrefix, getClientClassLoader());
        // merge with defaults
        final OptionMap connectOptions = mergeWithDefaults(DEFAULT_CONNECTION_CREATION_OPTIONS, connectOptionsFromConfiguration);
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
        // create the CallbackHandler for this connection configuration
        final CallbackHandler callbackHandler = createCallbackHandler(connectionSpecificProps, this.getDefaultCallbackHandler());

        return new RemotingConnectionConfigurationImpl(host, port, connectOptions, connectionTimeout, callbackHandler);

    }

    private String getConnectionSpecificPrefix(final String connectionName) {
        return "remote.connection." + connectionName + ".";
    }

    private String getConnectionSpecificConnectOptionsPrefix(final String connectionName) {
        return "remote.connection." + connectionName + ".connect.options.";
    }

    private Map<String, String> getPropertiesWithPrefix(final String prefix) {
        final Map<String, String> propertiesWithPrefix = new HashMap<String, String>();
        for (final String fullPropName : this.ejbReceiversConfigurationProperties.stringPropertyNames()) {
            if (fullPropName.startsWith(prefix)) {
                // strip the "prefix" from the full property name and just get the trailing part.
                // Example, If remote.cluster.foo.bar is the full property name,
                // then this step will return "bar" as the property name for the prefix "remote.cluster.foo.".
                String propName = fullPropName.substring(prefix.length());
                // get the value of the (full) property name
                final String propValue = this.ejbReceiversConfigurationProperties.getProperty(fullPropName);
                propertiesWithPrefix.put(propName, propValue);
            }
        }
        return propertiesWithPrefix;
    }

    private String getClusterSpecificPrefix(final String clusterName) {
        return "remote.cluster." + clusterName + ".";
    }

    private String getClusterSpecificConnectOptionsPrefix(final String clusterName) {
        return "remote.cluster." + clusterName + ".connect.options.";
    }

    private String getClusterNodeSpecificConnectOptionsPrefix(final String clusterName, final String nodeName) {
        return "remote.cluster." + clusterName + "node." + nodeName + ".connect.options.";
    }


    /**
     * Creates a callback handler
     *
     * @param properties
     * @return The CallbackHandler
     */
    private CallbackHandler createCallbackHandler(final Map<String, String> properties, final CallbackHandler defaultCallbackHandler) {
        String callbackClass = properties.get(PROPERTY_KEY_CALLBACK_HANDLER_CLASS);
        String userName = properties.get(PROPERTY_KEY_USERNAME);
        String password = properties.get(PROPERTY_KEY_PASSWORD);
        String passwordBase64 = properties.get(PROPERTY_KEY_PASSWORD_BASE64);
        String realm = properties.get(PROPERTY_KEY_REALM);

        CallbackHandler handler = resolveCallbackHandler(callbackClass, userName, password, passwordBase64, realm);
        if (handler != null) {
            return handler;
        }

        return defaultCallbackHandler;
    }

    private CallbackHandler resolveCallbackHandler(final String callbackClass, final String userName, final String password, final String passwordBase64, final String realm) {

        if (callbackClass != null && (userName != null || password != null)) {
            throw new IllegalStateException("Cannot specify both a callback handler and a username/password");
        }
        if (callbackClass != null) {
            final ClassLoader classLoader = getClientClassLoader();
            try {
                final Class<?> clazz = Class.forName(callbackClass, true, classLoader);
                return (CallbackHandler) clazz.newInstance();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Could not load callback handler class " + callbackClass, e);
            } catch (Exception e) {
                throw new RuntimeException("Could not instantiate handler instance of type " + callbackClass, e);
            }
        } else if (userName != null) {
            if (password != null && passwordBase64 != null) {
                throw new IllegalStateException("Cannot specify both a plain text and base64 encoded password");
            }

            final String decodedPassword;
            if (passwordBase64 != null) {
                try {
                    decodedPassword = DatatypeConverter.printBase64Binary(passwordBase64.getBytes());
                } catch (Exception e) {
                    throw new RuntimeException("Could not decode base64 encoded password", e);
                }
            } else if (password != null) {
                decodedPassword = password;
            } else {
                decodedPassword = null;
            }
            return new AuthenticationCallbackHandler(userName, decodedPassword.toCharArray(), realm);
        }
        return null;
    }

    private CallbackHandler getDefaultCallbackHandler() {
        final String callbackClass = this.ejbReceiversConfigurationProperties.getProperty(PROPERTY_KEY_CALLBACK_HANDLER_CLASS);
        final String userName = this.ejbReceiversConfigurationProperties.getProperty(PROPERTY_KEY_USERNAME);
        final String password = this.ejbReceiversConfigurationProperties.getProperty(PROPERTY_KEY_PASSWORD);
        final String passwordBase64 = this.ejbReceiversConfigurationProperties.getProperty(PROPERTY_KEY_PASSWORD_BASE64);
        final String realm = this.ejbReceiversConfigurationProperties.getProperty(PROPERTY_KEY_REALM);

        CallbackHandler handler = resolveCallbackHandler(callbackClass, userName, password, passwordBase64, realm);
        if (handler != null) {
            return handler;
        }
        // no auth specified, just use the default
        return new AnonymousCallbackHandler();
    }

    /**
     * A {@link CallbackHandler} which sets <code>anonymous</code> as the name during a {@link javax.security.auth.callback.NameCallback}
     */
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

    private class AuthenticationCallbackHandler implements CallbackHandler {

        private final String realm;
        private final String username;
        private final char[] password;

        private AuthenticationCallbackHandler(final String username, final char[] password, final String realm) {
            this.username = username;
            this.password = password;
            this.realm = realm;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

            for (Callback current : callbacks) {
                if (current instanceof RealmCallback) {
                    RealmCallback rcb = (RealmCallback) current;
                    if (realm == null) {
                        String defaultText = rcb.getDefaultText();
                        rcb.setText(defaultText); // For now just use the realm suggested.
                    } else {
                        rcb.setText(realm);
                    }
                } else if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName(username);
                } else if (current instanceof PasswordCallback) {
                    PasswordCallback pcb = (PasswordCallback) current;
                    pcb.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }
        }
    }

    private class RemotingConnectionConfigurationImpl implements RemotingConnectionConfiguration {
        final String host;
        final int port;
        final OptionMap connectionCreationOptions;
        final long connectionTimeout;
        final CallbackHandler callbackHandler;

        RemotingConnectionConfigurationImpl(final String host, final int port, final OptionMap connectionCreationOptions,
                                            final long connectionTimeout, final CallbackHandler callbackHandler) {
            this.host = host;
            this.port = port;
            this.connectionCreationOptions = connectionCreationOptions;
            this.connectionTimeout = connectionTimeout;
            this.callbackHandler = callbackHandler;
        }

        @Override
        public String getHost() {
            return this.host;
        }

        @Override
        public int getPort() {
            return this.port;
        }

        @Override
        public long getConnectionTimeout() {
            return this.connectionTimeout;
        }

        @Override
        public OptionMap getConnectionCreationOptions() {
            return this.connectionCreationOptions;
        }

        @Override
        public CallbackHandler getCallbackHandler() {
            return this.callbackHandler;
        }
    }

    private class RemotingClusterConfigurationImpl implements RemotingClusterConfiguration {

        private final String clusterName;
        private final long maxAllowedConnectedNodes;
        private final Map<String, RemotingClusterNodeConfiguration> nodeConfigurations = new HashMap<String, RemotingClusterNodeConfiguration>();
        private final CallbackHandler callbackHandler;
        private final OptionMap connectionCreationOptions;
        private final long connectionTimeout;

        RemotingClusterConfigurationImpl(final String clusterName, final long maxAllowedConnectedNodes, final OptionMap connectionCreationOptions,
                                         final CallbackHandler callbackHandler, final long connectionTimeout) {
            this.clusterName = clusterName;
            this.maxAllowedConnectedNodes = maxAllowedConnectedNodes;
            this.connectionCreationOptions = connectionCreationOptions;
            this.callbackHandler = callbackHandler;
            this.connectionTimeout = connectionTimeout;
        }

        @Override
        public String getClusterName() {
            return this.clusterName;
        }

        @Override
        public long getMaximumAllowedConnectedNodes() {
            return this.maxAllowedConnectedNodes;
        }

        @Override
        public Iterator<RemotingClusterNodeConfiguration> getNodeConfigurations() {
            return this.nodeConfigurations.values().iterator();
        }

        @Override
        public RemotingClusterNodeConfiguration getNodeConfiguration(String nodeName) {
            return this.nodeConfigurations.get(nodeName);
        }

        @Override
        public OptionMap getConnectionCreationOptions() {
            return this.connectionCreationOptions;
        }

        @Override
        public CallbackHandler getCallbackHandler() {
            return this.callbackHandler;
        }

        @Override
        public long getConnectionTimeout() {
            return this.connectionTimeout;
        }

        void addNodeConfigurations(final Collection<RemotingClusterNodeConfiguration> nodeConfigurations) {
            if (nodeConfigurations != null) {
                for (final RemotingClusterNodeConfiguration nodeConfiguration : nodeConfigurations) {
                    this.nodeConfigurations.put(nodeConfiguration.getNodeName(), nodeConfiguration);
                }
            }

        }

    }

    private class RemotingClusterNodeConfigurationImpl implements RemotingClusterNodeConfiguration {

        private final String nodeName;
        private final OptionMap connectionCreationOptions;
        private final CallbackHandler callbackHandler;
        private final long connectionTimeout;

        RemotingClusterNodeConfigurationImpl(final String nodeName, final OptionMap connectionCreationOptions, final CallbackHandler callbackHandler,
                                             final long connectionTimeout) {
            this.nodeName = nodeName;
            this.connectionCreationOptions = connectionCreationOptions;
            this.callbackHandler = callbackHandler;
            this.connectionTimeout = connectionTimeout;
        }

        @Override
        public String getNodeName() {
            return this.nodeName;
        }

        @Override
        public OptionMap getConnectionCreationOptions() {
            return this.connectionCreationOptions;
        }

        @Override
        public CallbackHandler getCallbackHandler() {
            return this.callbackHandler;
        }

        @Override
        public long getConnectionTimeout() {
            return this.connectionTimeout;
        }
    }
}
