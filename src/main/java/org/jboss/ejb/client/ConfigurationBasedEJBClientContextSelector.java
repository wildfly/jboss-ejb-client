/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import static javax.xml.stream.XMLStreamConstants.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.jboss.ejb._private.Logs;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.client.config.ConfigurationXMLStreamReader;
import org.wildfly.common.Assert;
import org.wildfly.common.selector.Selector;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.impl.StaticDiscoveryProvider;

/**
 * A one-time, configuration-based EJB client context selector.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConfigurationBasedEJBClientContextSelector extends Selector<EJBClientContext> {
    private static final EJBClientContext configuredContext;

    private static final String NS_EJB_CLIENT_3_0 = "urn:jboss:ejb-client:3.0";

    static {
        configuredContext = loadConfiguration();
    }

    private static EJBClientContext loadConfiguration() {
        final ClientConfiguration clientConfiguration = ClientConfiguration.getInstance();
        final ClassLoader classLoader = ConfigurationBasedEJBClientContextSelector.class.getClassLoader();
        if (clientConfiguration != null) try {
            final EJBClientContext.Builder builder = new EJBClientContext.Builder();
            try (final ConfigurationXMLStreamReader streamReader = clientConfiguration.readConfiguration(Collections.singleton(NS_EJB_CLIENT_3_0))) {
                parseEJBClientConfiguration(streamReader, builder);
            }
            loadTransportProviders(builder, classLoader);
            return builder.build();
        } catch (ConfigXMLParseException e) {
            throw new IllegalStateException(e);
        }
        // build a generic config instead
        final EJBClientContext.Builder builder = new EJBClientContext.Builder();
        loadTransportProviders(builder, classLoader);
        return builder.build();
    }

    private static void parseEJBClientConfiguration(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        if (streamReader.nextTag() == START_ELEMENT) {
            if (! streamReader.getNamespaceURI().equals(NS_EJB_CLIENT_3_0) || ! streamReader.getLocalName().equals("jboss-ejb-client")) {
                throw streamReader.unexpectedElement();
            }
            parseEJBClientType(streamReader, builder);
            return;
        }
        throw streamReader.unexpectedContent();
    }

    private static void parseEJBClientType(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        if (streamReader.getAttributeCount() > 0) {
            throw streamReader.unexpectedAttribute(0);
        }
        boolean gotDiscovery = false;
        boolean gotGlobalInterceptors = false;
        boolean gotConnections = false;
        for (;;) {
            final int next = streamReader.nextTag();
            if (next == START_ELEMENT) {
                if (! streamReader.getNamespaceURI().equals(NS_EJB_CLIENT_3_0)) {
                    throw streamReader.unexpectedElement();
                }
                final String localName = streamReader.getLocalName();
                if (localName.equals("discovery") && ! gotDiscovery) {
                    gotDiscovery = true;
                    parseDiscoveryType(streamReader, builder);
                } else if (localName.equals("global-interceptors") && ! gotGlobalInterceptors) {
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

    private static void parseDiscoveryType(final ConfigurationXMLStreamReader streamReader, final EJBClientContext.Builder builder) throws ConfigXMLParseException {
        if (streamReader.getAttributeCount() > 0) {
            throw streamReader.unexpectedAttribute(0);
        }
        List<ServiceURL> staticURLs = null;
        for (;;) {
            final int next = streamReader.nextTag();
            if (next == START_ELEMENT) {
                if (! streamReader.getNamespaceURI().equals(NS_EJB_CLIENT_3_0)) {
                    throw streamReader.unexpectedElement();
                } else if (streamReader.getLocalName().equals("static-node-discovery")) {
                    if (staticURLs == null) staticURLs = new ArrayList<>();
                    parseStaticNodeDiscoveryType(streamReader, staticURLs);
                } else if (streamReader.getLocalName().equals("static-cluster-discovery")) {
                    if (staticURLs == null) staticURLs = new ArrayList<>();
                    parseStaticClusterDiscoveryType(streamReader, staticURLs);
                } else if (streamReader.getLocalName().equals("static-ejb-discovery")) {
                    if (staticURLs == null) staticURLs = new ArrayList<>();
                    parseStaticEjbDiscoveryType(streamReader, staticURLs);
                } else {
                    throw streamReader.unexpectedElement();
                }
            } else if (next == END_ELEMENT) {
                if (staticURLs != null) {
                    builder.addDiscoveryProvider(new StaticDiscoveryProvider(staticURLs));
                }
                return;
            } else {
                throw Assert.unreachableCode();
            }
        }
    }

    private static void parseStaticNodeDiscoveryType(final ConfigurationXMLStreamReader streamReader, final List<ServiceURL> staticURLs) throws ConfigXMLParseException {
        final int attributeCount = streamReader.getAttributeCount();
        String nodeName = null;
        URI uri = null;
        for (int i = 0; i < attributeCount; i ++) {
            if (streamReader.getAttributeNamespace(i) != null) {
                throw streamReader.unexpectedAttribute(i);
            } else if (streamReader.getAttributeLocalName(i).equals("node-name")) {
                nodeName = streamReader.getAttributeValue(i);
            } else if (streamReader.getAttributeLocalName(i).equals("uri")) {
                uri = streamReader.getURIAttributeValue(i);
            } else {
                throw streamReader.unexpectedAttribute(i);
            }
        }
        if (nodeName == null) {
            throw streamReader.missingRequiredAttribute(null, "node-name");
        } else if (uri == null) {
            throw streamReader.missingRequiredAttribute(null, "uri");
        }
        final ServiceURL.Builder urlBuilder = new ServiceURL.Builder();
        urlBuilder.addAttribute("node", AttributeValue.fromString(nodeName));
        urlBuilder.setUri(uri);
        urlBuilder.setAbstractType("ejb");
        urlBuilder.setAbstractTypeAuthority("jboss");
        staticURLs.add(urlBuilder.create());
        if (streamReader.nextTag() != END_ELEMENT) {
            throw streamReader.unexpectedElement();
        }
    }

    private static void parseStaticClusterDiscoveryType(final ConfigurationXMLStreamReader streamReader, final List<ServiceURL> staticURLs) throws ConfigXMLParseException {
        final int attributeCount = streamReader.getAttributeCount();
        String clusterName = null;
        for (int i = 0; i < attributeCount; i ++) {
            if (streamReader.getAttributeNamespace(i) != null) {
                throw streamReader.unexpectedAttribute(i);
            } else if (streamReader.getAttributeLocalName(i).equals("cluster-name")) {
                clusterName = streamReader.getAttributeValue(i);
            } else {
                throw streamReader.unexpectedAttribute(i);
            }
        }
        if (clusterName == null) {
            throw streamReader.missingRequiredAttribute(null, "cluster-name");
        }
        List<URI> connectUris = null;
        for (;;) {
            final int next = streamReader.nextTag();
            if (next == START_ELEMENT) {
                if (! streamReader.getNamespaceURI().equals(NS_EJB_CLIENT_3_0)) {
                    throw streamReader.unexpectedElement();
                } else if (streamReader.getLocalName().equals("connect-to")) {
                    if (connectUris == null) connectUris = new ArrayList<>();
                    connectUris.add(parseConnectTo(streamReader));
                } else {
                    throw streamReader.unexpectedElement();
                }
            } else if (next == END_ELEMENT) {
                if (connectUris != null) for (URI connectUri : connectUris) {
                    final ServiceURL.Builder urlBuilder = new ServiceURL.Builder();
                    urlBuilder.addAttribute("cluster", AttributeValue.fromString(clusterName));
                    urlBuilder.setUri(connectUri);
                    urlBuilder.setAbstractType("ejb");
                    urlBuilder.setAbstractTypeAuthority("jboss");
                    staticURLs.add(urlBuilder.create());
                }
                return;
            } else {
                throw Assert.unreachableCode();
            }
        }
    }

    private static URI parseConnectTo(final ConfigurationXMLStreamReader streamReader) throws ConfigXMLParseException {
        final int attributeCount = streamReader.getAttributeCount();
        URI uri = null;
        for (int i = 0; i < attributeCount; i ++) {
            if (streamReader.getAttributeNamespace(i) != null || ! streamReader.getAttributeLocalName(i).equals("uri")) {
                throw streamReader.unexpectedAttribute(i);
            }
            uri = streamReader.getURIAttributeValue(i);
        }
        if (uri == null) {
            throw streamReader.missingRequiredAttribute(null, "uri");
        }
        if (streamReader.nextTag() != END_ELEMENT) {
            throw streamReader.unexpectedElement();
        }
        return uri;
    }

    private static void parseStaticEjbDiscoveryType(final ConfigurationXMLStreamReader streamReader, final List<ServiceURL> staticURLs) throws ConfigXMLParseException {
        final int attributeCount = streamReader.getAttributeCount();
        String appName = null;
        String moduleName = null;
        String beanName = null;
        String distinctName = null;
        URI uri = null;
        for (int i = 0; i < attributeCount; i ++) {
            if (streamReader.getAttributeNamespace(i) != null) {
                throw streamReader.unexpectedAttribute(i);
            } else if (streamReader.getAttributeLocalName(i).equals("app-name")) {
                appName = streamReader.getAttributeValue(i);
            } else if (streamReader.getAttributeLocalName(i).equals("module-name")) {
                moduleName = streamReader.getAttributeValue(i);
            } else if (streamReader.getAttributeLocalName(i).equals("bean-name")) {
                beanName = streamReader.getAttributeValue(i);
            } else if (streamReader.getAttributeLocalName(i).equals("distinct-name")) {
                distinctName = streamReader.getAttributeValue(i);
            } else if (streamReader.getAttributeLocalName(i).equals("uri")) {
                uri = streamReader.getURIAttributeValue(i);
            } else {
                throw streamReader.unexpectedAttribute(i);
            }
        }
        if (moduleName == null) {
            throw streamReader.missingRequiredAttribute(null, "module-name");
        } else if (beanName == null) {
            throw streamReader.missingRequiredAttribute(null, "bean-name");
        } else if (uri == null) {
            throw streamReader.missingRequiredAttribute(null, "uri");
        }
        final ServiceURL.Builder urlBuilder = new ServiceURL.Builder();
        String primary;
        if (appName == null) {
            primary = moduleName;
        } else {
            primary = appName + "/" + moduleName;
        }
        String secondary;
        if (distinctName == null) {
            secondary = beanName;
        } else {
            secondary = beanName + "/" + distinctName;
        }
        urlBuilder.addAttribute("module", AttributeValue.fromString(primary));
        urlBuilder.addAttribute("bean", AttributeValue.fromString(primary + "/" + secondary));
        urlBuilder.setUri(uri);
        urlBuilder.setAbstractType("ejb");
        urlBuilder.setAbstractTypeAuthority("jboss");
        staticURLs.add(urlBuilder.create());
        if (streamReader.nextTag() != END_ELEMENT) {
            throw streamReader.unexpectedElement();
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
            if (streamReader.getNamespaceURI(i) != null) {
                throw streamReader.unexpectedAttribute(i);
            }
            final String name = streamReader.getAttributeLocalName(i);
            if (name.equals("class")) {
                className = streamReader.getAttributeValue(i);
            } else if (name.equals("moduleName")) {
                moduleName = streamReader.getAttributeValue(i);
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
                cl = Module.getModuleFromCallerModuleLoader(ModuleIdentifier.fromString(moduleName)).getClassLoader();
            } catch (ModuleLoadException e) {
                throw new ConfigXMLParseException(e);
            }
        } else {
            cl = ConfigurationBasedEJBClientContextSelector.class.getClassLoader();
        }
        final Class<? extends EJBClientInterceptor> interceptorClass;
        final EJBClientInterceptor interceptor;
        try {
            interceptorClass = Class.forName(className, false, cl).asSubclass(EJBClientInterceptor.class);
            interceptor = interceptorClass.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | ClassCastException e) {
            throw new ConfigXMLParseException(e);
        }
        builder.addInterceptor(interceptor);
        final int next = streamReader.nextTag();
        if (next == END_ELEMENT) {
            return;
        }
        throw streamReader.unexpectedElement();
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
            if (streamReader.getNamespaceURI(i) != null || ! streamReader.getAttributeLocalName(i).equals("uri") || uri != null) {
                throw streamReader.unexpectedAttribute(i);
            }
            uri = streamReader.getURIAttributeValue(i);
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

    public EJBClientContext get() {
        return configuredContext;
    }
}
