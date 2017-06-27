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

package org.jboss.ejb.client;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.legacy.LegacyPropertiesConfiguration;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.client.config.ConfigurationXMLStreamReader;
import org.wildfly.common.Assert;

/**
 * A one-time, configuration-based EJB client context configurator.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ConfigurationBasedEJBClientContextSelector {
    private static final EJBClientContext configuredContext;

    private static final String NS_EJB_CLIENT_3_0 = "urn:jboss:wildfly-client-ejb:3.0";
    private static final String NS_INCORRECT = "urn:jboss:ejb-client:3.0";

    static {
        configuredContext = loadConfiguration();
    }

    private ConfigurationBasedEJBClientContextSelector() {
    }

    private static EJBClientContext loadConfiguration() {
        final ClientConfiguration clientConfiguration = ClientConfiguration.getInstance();
        final ClassLoader classLoader = ConfigurationBasedEJBClientContextSelector.class.getClassLoader();
        final EJBClientContext.Builder builder = new EJBClientContext.Builder();
        loadTransportProviders(builder, classLoader);
        if (clientConfiguration != null) try {
            try (final ConfigurationXMLStreamReader streamReader = clientConfiguration.readConfiguration(Collections.singleton(NS_EJB_CLIENT_3_0))) {
                parseEJBClientConfiguration(streamReader, builder);
            }
        } catch (ConfigXMLParseException e) {
            throw new IllegalStateException(e);
        }
        LegacyPropertiesConfiguration.configure(builder);
        return builder.build();
    }

    private static void parseEJBClientConfiguration(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        if (streamReader.hasNext()) {
            if (streamReader.nextTag() == START_ELEMENT) {
                String namespaceURI = streamReader.getNamespaceURI();
                // TODO: temporary
                if (namespaceURI.equals(NS_INCORRECT)) {
                    throw new ConfigXMLParseException("The namespace \"" + NS_INCORRECT + "\" was incorrect; replace with \"" + NS_EJB_CLIENT_3_0 + "\"", streamReader);
                }
                if (! namespaceURI.equals(NS_EJB_CLIENT_3_0) || ! streamReader.getLocalName().equals("jboss-ejb-client")) {
                    throw streamReader.unexpectedElement();
                }
                parseEJBClientType(streamReader, builder);
                return;
            }
            throw streamReader.unexpectedContent();
        }
    }

    private static void parseEJBClientType(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        if (streamReader.getAttributeCount() > 0) {
            throw streamReader.unexpectedAttribute(0);
        }
        boolean gotGlobalInterceptors = false;
        boolean gotConnections = false;
        for (;;) {
            final int next = streamReader.nextTag();
            if (next == START_ELEMENT) {
                if (! streamReader.getNamespaceURI().equals(NS_EJB_CLIENT_3_0)) {
                    throw streamReader.unexpectedElement();
                }
                final String localName = streamReader.getLocalName();
                if (localName.equals("global-interceptors") && ! gotGlobalInterceptors) {
                    gotGlobalInterceptors = true;
                    parseInterceptorsType(streamReader, builder);
                } else if (localName.equals("connections") && ! gotConnections) {
                    gotConnections = true;
                    parseConnectionsType(streamReader, builder);
                } else {
                    throw streamReader.unexpectedElement();
                }
            } else if (next == END_ELEMENT) {
                return;
            } else {
                throw Assert.unreachableCode();
            }
        }
    }

    private static void parseInterceptorsType(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        if (streamReader.getAttributeCount() > 0) {
            throw streamReader.unexpectedAttribute(0);
        }
        for (;;) {
            final int next = streamReader.nextTag();
            if (next == START_ELEMENT) {
                if (! streamReader.getNamespaceURI().equals(NS_EJB_CLIENT_3_0) || ! streamReader.getLocalName().equals("interceptor")) {
                    throw streamReader.unexpectedElement();
                }
                parseInterceptorType(streamReader, builder);
            } else if (next == END_ELEMENT) {
                return;
            } else {
                throw Assert.unreachableCode();
            }
        }
    }

    private static void parseInterceptorType(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        final int attributeCount = streamReader.getAttributeCount();
        String className = null;
        String moduleName = null;
        for (int i = 0; i < attributeCount; i++) {
            if (streamReader.getAttributeNamespace(i) != null && ! streamReader.getAttributeNamespace(i).isEmpty()) {
                throw streamReader.unexpectedAttribute(i);
            }
            final String name = streamReader.getAttributeLocalName(i);
            if (name.equals("class")) {
                className = streamReader.getAttributeValueResolved(i);
            } else if (name.equals("moduleName")) {
                moduleName = streamReader.getAttributeValueResolved(i);
            } else {
                throw streamReader.unexpectedAttribute(i);
            }
        }
        if (className == null) {
            throw streamReader.missingRequiredAttribute(null, "class");
        }
        ClassLoader cl;
        if (moduleName != null) {
            try {
                cl = ModuleLoadDelegate.loadModule(moduleName);
            } catch (LinkageError e) {
                throw Logs.MAIN.noJBossModules(streamReader);
            }
        } else {
            cl = ConfigurationBasedEJBClientContextSelector.class.getClassLoader();
        }
        final Class<? extends EJBClientInterceptor> interceptorClass;
        try {
            interceptorClass = Class.forName(className, false, cl).asSubclass(EJBClientInterceptor.class);
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new ConfigXMLParseException(e);
        }
        builder.addInterceptor(interceptorClass);
        final int next = streamReader.nextTag();
        if (next == END_ELEMENT) {
            return;
        }
        throw streamReader.unexpectedElement();
    }

    static final class ModuleLoadDelegate {
        static ClassLoader loadModule(String moduleName) throws ConfigXMLParseException {
            try {
                return Module.getModuleFromCallerModuleLoader(ModuleIdentifier.fromString(moduleName)).getClassLoader();
            } catch (ModuleLoadException e) {
                throw new ConfigXMLParseException(e);
            }
        }
    }

    private static void parseConnectionsType(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        if (streamReader.getAttributeCount() > 0) {
            throw streamReader.unexpectedAttribute(0);
        }
        for (;;) {
            final int next = streamReader.nextTag();
            if (next == START_ELEMENT) {
                if (! streamReader.getNamespaceURI().equals(NS_EJB_CLIENT_3_0)) {
                    throw streamReader.unexpectedElement();
                }
                final String localName = streamReader.getLocalName();
                if (localName.equals("connection")) {
                    parseConnectionType(streamReader, builder);
                } else {
                    throw streamReader.unexpectedElement();
                }
            } else if (next == END_ELEMENT) {
                return;
            } else {
                throw Assert.unreachableCode();
            }
        }
    }

    private static void parseConnectionType(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        URI uri = null;
        final int attributeCount = streamReader.getAttributeCount();
        for (int i = 0; i < attributeCount; i ++) {
            if (streamReader.getAttributeNamespace(i) != null && ! streamReader.getAttributeNamespace(i).isEmpty() || ! streamReader.getAttributeLocalName(i).equals("uri") || uri != null) {
                throw streamReader.unexpectedAttribute(i);
            }
            uri = streamReader.getURIAttributeValueResolved(i);
        }
        if (uri == null) {
            throw streamReader.missingRequiredAttribute(null, "uri");
        }
        final int next = streamReader.nextTag();
        if (next == START_ELEMENT) {
            if (! streamReader.getNamespaceURI().equals(NS_EJB_CLIENT_3_0)) {
                throw streamReader.unexpectedElement();
            }
            final String localName = streamReader.getLocalName();
            if (localName.equals("interceptors")) {
                // todo...
                streamReader.skipContent();
            }
        } else if (next == END_ELEMENT) {
            final EJBClientConnection.Builder connBuilder = new EJBClientConnection.Builder();
            connBuilder.setDestination(uri);
            builder.addClientConnection(connBuilder.build());
            return;
        } else {
            throw Assert.unreachableCode();
        }
    }

    private static void loadTransportProviders(final EJBClientContext.Builder builder, final ClassLoader classLoader) {
        final ServiceLoader<EJBTransportProvider> serviceLoader = ServiceLoader.load(EJBTransportProvider.class, classLoader);
        Iterator<EJBTransportProvider> iterator = serviceLoader.iterator();
        for (;;) try {
            if (! iterator.hasNext()) break;
            final EJBTransportProvider transportProvider = iterator.next();
            builder.addTransportProvider(transportProvider);
        } catch (ServiceConfigurationError ignored) {
            Logs.MAIN.error("Failed to load service", ignored);
        }
    }

    static EJBClientContext get() {
        return configuredContext;
    }
}
