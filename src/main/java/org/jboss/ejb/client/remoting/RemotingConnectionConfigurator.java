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
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
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

/**
 * @author Jaikiran Pai
 */
class RemotingConnectionConfigurator extends RemotingConfigurator {

    private static final Logger logger = Logger.getLogger(RemotingConnectionConfigurator.class);

    private static final String REMOTE_CONNECTIONS_PROP_KEY = "remote.connections";
    // The default options that will be used (unless overridden by the config file) while creating a connection
    private static final OptionMap DEFAULT_CONNECTION_CREATION_OPTIONS = OptionMap.EMPTY;
    private static final long DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS = 5000;

    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";
    private static final String PASSWORD_BASE64_KEY = "password.base64";
    private static final String REALM_KEY = "realm";

    private static final String CALLBACK_HANDLER_KEY = "callback.handler.class";


    private final Endpoint endpoint;

    private RemotingConnectionConfigurator(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    static RemotingConnectionConfigurator configuratorFor(final Endpoint endpoint) {
        return new RemotingConnectionConfigurator(endpoint);
    }

    static boolean containsRemotingConnectionConfigurations(final Properties properties) {
        final String remoteConnectionNames = (String) properties.get(REMOTE_CONNECTIONS_PROP_KEY);
        if (remoteConnectionNames == null || remoteConnectionNames.trim().isEmpty()) {
            return false;
        }
        return true;
    }

    Collection<Connection> createConnections(final Properties properties) {
        final String remoteConnectionNames = (String) properties.get(REMOTE_CONNECTIONS_PROP_KEY);
        // no connections configured, nothing to do!
        if (remoteConnectionNames == null || remoteConnectionNames.trim().isEmpty()) {
            logger.debug("No remoting connections configured in properties");
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
                connection = this.createConnection(connectionName, properties);
            } catch (Exception e) {
                logger.warn("Could not create connection for connection named " + connectionName, e);
            }
            if (connection == null) {
                continue;
            }
            logger.debug("Connection " + connection + " successfully created for connection named " + connectionName);
            // successful connection creation, add it to the list
            remotingConnections.add(connection);
        }

        return remotingConnections;
    }

    Connection createConnection(final String connectionName, final Properties properties) throws IOException, URISyntaxException {
        final Map<String, String> connectionSpecificProps = this.getConnectionSpecificProperties(connectionName, properties);
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
        final OptionMap connectOptionsFromConfiguration = getOptionMapFromProperties(properties, connectOptionsPrefix, getClientClassLoader());
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
        // create the CallbackHandler for this connection
        final CallbackHandler callbackHandler = createCallbackHandler(connectionName, connectionSpecificProps, properties);

        final URI connectionURI = new URI("remote://" + host.trim() + ":" + port);
        final IoFuture<Connection> futureConnection = this.endpoint.connect(connectionURI, connectOptions, callbackHandler);
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

    /**
     * If {@link Thread#getContextClassLoader()} is null then returns the classloader which loaded
     * {@link RemotingConnectionConfigurator} class. Else returns the {@link Thread#getContextClassLoader()}
     *
     * @return
     */
    private static ClassLoader getClientClassLoader() {
        final ClassLoader tccl = SecurityActions.getContextClassLoader();
        if (tccl != null) {
            return tccl;
        }
        return RemotingConnectionConfigurator.class.getClassLoader();
    }

    /**
     * Creates a callback handler for the given remote connection.
     *
     * @param connectionName          The connection name
     * @param connectionSpecificProps
     * @param ejbClientProperties     The connection properties
     * @return The CallbackHandler
     */
    private CallbackHandler createCallbackHandler(final String connectionName, final Map<String, String> connectionSpecificProps, final Properties ejbClientProperties) {
        String callbackClass = connectionSpecificProps.get(CALLBACK_HANDLER_KEY);
        String userName = connectionSpecificProps.get(USERNAME_KEY);
        String password = connectionSpecificProps.get(PASSWORD_KEY);
        String passwordBase64 = connectionSpecificProps.get(PASSWORD_BASE64_KEY);
        String realm = connectionSpecificProps.get(REALM_KEY);

        CallbackHandler handler = resolveCallbackHandler(connectionName, callbackClass, userName, password, passwordBase64, realm);
        if (handler != null) {
            return handler;
        }

        //nothing was specified for this connection, now check the defaults
        callbackClass = ejbClientProperties.getProperty(CALLBACK_HANDLER_KEY);
        userName = ejbClientProperties.getProperty(USERNAME_KEY);
        password = ejbClientProperties.getProperty(PASSWORD_KEY);
        passwordBase64 = ejbClientProperties.getProperty(PASSWORD_BASE64_KEY);
        realm = ejbClientProperties.getProperty(REALM_KEY);

        handler = resolveCallbackHandler(connectionName, callbackClass, userName, password, passwordBase64, realm);
        if (handler != null) {
            return handler;
        }
        //no auth specified, just use the default
        return new AnonymousCallbackHandler();
    }

    private CallbackHandler resolveCallbackHandler(final String connectionName, final String callbackClass, final String userName, final String password, final String passwordBase64, final String realm) {

        if (callbackClass != null && (userName != null || password != null)) {
            throw new RuntimeException("Cannot specify both a callback handler and a username/password for connection " + connectionName);
        }
        if (callbackClass != null) {
            final ClassLoader classLoader = getClientClassLoader();
            try {
                final Class<?> clazz = Class.forName(callbackClass, true, classLoader);
                return (CallbackHandler) clazz.newInstance();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Could not load callback handler class " + callbackClass + " for connection " + connectionName, e);
            } catch (Exception e) {
                throw new RuntimeException("Could not instantiate handler instance of type " + callbackClass + " for connection " + connectionName, e);
            }
        } else if (userName != null) {
            if (password != null && passwordBase64 != null) {
                throw new RuntimeException("Cannot specify both a plain text and base64 encoded password for connection " + connectionName);
            }

            final String decodedPassword;
            if (passwordBase64 != null) {
                try {
                    decodedPassword = DatatypeConverter.printBase64Binary(passwordBase64.getBytes());
                } catch (Exception e) {
                    throw new RuntimeException("Could not decode base64 encoded password for connection " + connectionName, e);
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

    /**
     * A {@link CallbackHandler} which sets <code>anonymous</code> as the name during a {@link NameCallback}
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

}
