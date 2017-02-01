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

import static java.security.AccessController.doPrivileged;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb._private.NetworkUtil;
import org.wildfly.common.Assert;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.Discovery;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceType;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.ServicesQueue;

/**
 * The public API for an EJB client context.  An EJB client context may be associated with (and used by) one or more threads concurrently.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientContext extends Attachable implements Contextual<EJBClientContext> {

    /**
     * The service type to use for EJB discovery.
     */
    public static final ServiceType EJB_SERVICE_TYPE = ServiceType.of("ejb", "jboss");

    private static final ContextManager<EJBClientContext> CONTEXT_MANAGER = new ContextManager<EJBClientContext>(EJBClientContext.class, "jboss.ejb.client");

    private static final Supplier<Discovery> DISCOVERY_SUPPLIER = doPrivileged((PrivilegedAction<Supplier<Discovery>>) Discovery.getContextManager()::getPrivilegedSupplier);

    private static final Supplier<EJBClientContext> GETTER = doPrivileged((PrivilegedAction<Supplier<EJBClientContext>>) CONTEXT_MANAGER::getPrivilegedSupplier);

    private static final EJBClientInterceptor[] NO_INTERCEPTORS = new EJBClientInterceptor[0];
    private static final EJBTransportProvider[] NO_TRANSPORT_PROVIDERS = new EJBTransportProvider[0];

    /**
     * The discovery attribute name which contains the application and module name of the located EJB.
     */
    public static final String FILTER_ATTR_EJB_MODULE = "ejb-module";
    /**
     * The discovery attribute name which contains the application and module name with the distinct name of the located EJB.
     */
    public static final String FILTER_ATTR_EJB_MODULE_DISTINCT = "ejb-module-distinct";
    /**
     * The discovery attribute name which contains a node name.
     */
    public static final String FILTER_ATTR_NODE = "node";
    /**
     * The discovery attribute name which contains a cluster name.
     */
    public static final String FILTER_ATTR_CLUSTER = "cluster";
    /**
     * The discovery attribute name for when a rule only applies to a specific source IP address range.
     */
    public static final String FILTER_ATTR_SOURCE_IP = "source-ip";

    static {
        CONTEXT_MANAGER.setGlobalDefaultSupplier(new ConfigurationBasedEJBClientContextSelector());
    }

    private final EJBClientInterceptor[] interceptors;
    private final EJBTransportProvider[] transportProviders;
    private final long invocationTimeout;
    private final EJBReceiverContext receiverContext;
    private final List<EJBClientConnection> configuredConnections;

    EJBClientContext(Builder builder) {
        final List<EJBClientInterceptor> builderInterceptors = builder.interceptors;
        if (builderInterceptors == null || builderInterceptors.isEmpty()) {
            interceptors = NO_INTERCEPTORS;
        } else {
            interceptors = builderInterceptors.toArray(NO_INTERCEPTORS);
        }
        final List<EJBTransportProvider> builderTransportProviders = builder.transportProviders;
        if (builderTransportProviders == null || builderTransportProviders.isEmpty()) {
            transportProviders = NO_TRANSPORT_PROVIDERS;
        } else {
            transportProviders = builderTransportProviders.toArray(new EJBTransportProvider[builderTransportProviders.size()]);
        }
        invocationTimeout = 0;
        receiverContext = new EJBReceiverContext(this);
        final List<EJBClientConnection> clientConnections = builder.clientConnections;
        if (clientConnections == null || clientConnections.isEmpty()) {
            configuredConnections = Collections.emptyList();
        } else if (clientConnections.size() == 1) {
            configuredConnections = Collections.singletonList(clientConnections.get(0));
        } else {
            configuredConnections = Collections.unmodifiableList(new ArrayList<>(clientConnections));
        }
        // this must be last
        for (EJBTransportProvider transportProvider : transportProviders) {
            transportProvider.notifyRegistered(receiverContext);
        }
    }

    /**
     * Get the context manager.  Simply calls the {@code static} method {@link #getContextManager()}.
     *
     * @return the context manager (not {@code null})
     */
    public ContextManager<EJBClientContext> getInstanceContextManager() {
        return getContextManager();
    }

    /**
     * Get the context manager.
     *
     * @return the context manager (not {@code null})
     */
    public static ContextManager<EJBClientContext> getContextManager() {
        return CONTEXT_MANAGER;
    }

    /**
     * Get the configured invocation timeout.  A value of zero indicates that invocations never time out.
     *
     * @return the configured invocation timeout
     */
    public long getInvocationTimeout() {
        return invocationTimeout;
    }

    /**
     * Get the pre-configured connections for this context.  This information may not be used by some transport providers
     * and mainly exists for legacy compatibility purposes.
     *
     * @return the pre-configured connections for this context (not {@code null})
     */
    public List<EJBClientConnection> getConfiguredConnections() {
        return configuredConnections;
    }

    /**
     * Get a copy of this context with the given interceptor(s) added.  If the array is {@code null} or empty, the
     * current context is returned as-is.
     *
     * @param interceptors the interceptor(s) to add
     * @return the new context (not {@code null})
     */
    public EJBClientContext withAddedInterceptors(EJBClientInterceptor... interceptors) {
        if (interceptors == null) {
            return this;
        }
        final int length = interceptors.length;
        if (length == 0) {
            return this;
        }
        final Builder builder = new Builder(this);
        boolean construct = false;
        for (EJBClientInterceptor interceptor : interceptors) {
            if (interceptor != null) {
                builder.addInterceptor(interceptor);
                construct = true;
            }
        }
        return construct ? builder.build() : this;
    }

    /**
     * Get a copy of this context with the given transport provider(s) added.  If the array is {@code null} or empty, the
     * current context is returned as-is.
     *
     * @param transportProviders the transport providers(s) to add
     * @return the new context (not {@code null})
     */
    public EJBClientContext withAddedTransportProviders(EJBTransportProvider... transportProviders) {
        if (transportProviders == null) {
            return this;
        }
        final int length = transportProviders.length;
        if (length == 0) {
            return this;
        }
        final Builder builder = new Builder(this);
        boolean construct = false;
        for (EJBTransportProvider transportProvider : transportProviders) {
            if (transportProvider != null) {
                builder.addTransportProvider(transportProvider);
                construct = true;
            }
        }
        return construct ? builder.build() : this;
    }

    EJBReceiver getTransportProvider(final String scheme) {
        for (EJBTransportProvider transportProvider : transportProviders) {
            if (transportProvider.supportsProtocol(scheme)) {
                return transportProvider.getReceiver(receiverContext, scheme);
            }
        }
        return null;
    }

    ServicesQueue discover(final FilterSpec filterSpec) {
        return getDiscovery().discover(EJB_SERVICE_TYPE, filterSpec);
    }

    EJBTransportProvider[] getTransportProviders() {
        return transportProviders;
    }

    Discovery getDiscovery() {
        return DISCOVERY_SUPPLIER.get();
    }

    /**
     * A builder for EJB client contexts.
     */
    public static final class Builder {

        List<EJBClientInterceptor> interceptors;
        List<EJBTransportProvider> transportProviders;
        List<EJBClientConnection> clientConnections;

        /**
         * Construct a new instance.
         */
        public Builder() {
            interceptors = new ArrayList<>();
            interceptors.add(new TransactionInterceptor());
        }

        Builder(final EJBClientContext ejbClientContext) {
            final EJBClientInterceptor[] interceptors = ejbClientContext.getInterceptors();
            if (interceptors.length > 0) {
                this.interceptors = new ArrayList<>(Arrays.asList(interceptors));
            }
            final EJBTransportProvider[] transportProviders = ejbClientContext.getTransportProviders();
            if (transportProviders.length > 0) {
                this.transportProviders = new ArrayList<>(Arrays.asList(transportProviders));
            }
            clientConnections = new ArrayList<>(ejbClientContext.getConfiguredConnections());
        }

        public void addInterceptor(EJBClientInterceptor interceptor) {
            Assert.checkNotNullParam("interceptor", interceptor);
            if (interceptors == null) {
                interceptors = new ArrayList<>();
            }
            interceptors.add(interceptor);
        }

        public void addTransportProvider(EJBTransportProvider provider) {
            Assert.checkNotNullParam("provider", provider);
            if (transportProviders == null) {
                transportProviders = new ArrayList<>();
            }
            transportProviders.add(provider);
        }

        public void addClientConnection(EJBClientConnection connection) {
            Assert.checkNotNullParam("connection", connection);
            if (clientConnections == null) {
                clientConnections = new ArrayList<>();
            }
            clientConnections.add(connection);
        }

        public EJBClientContext build() {
            return new EJBClientContext(this);
        }
    }

    /**
     * Get the current client context for this thread.
     *
     * @return the current client context
     */
    public static EJBClientContext getCurrent() {
        final EJBClientContext clientContext = GETTER.get();
        if (clientContext == null) {
            throw Logs.MAIN.noEJBClientContextAvailable();
        }
        return clientContext;
    }

    /**
     * Get the current client context for this thread.  Delegates to {@link #getCurrent()}.
     *
     * @return the current client context for this thread
     */
    public static EJBClientContext requireCurrent() {
        return getCurrent();
    }

    <T> StatefulEJBLocator<T> createSession(final StatelessEJBLocator<T> statelessLocator) throws Exception {
        final LocatedAction<StatefulEJBLocator<T>, StatelessEJBLocator<T>, T> action =
            (receiver, originalLocator, newAffinity) -> receiver.createSession(originalLocator.withNewAffinity(newAffinity));
        return performLocatedAction(statelessLocator, action);
    }

    interface LocatedAction<R, L extends EJBLocator<T>, T> {
        R execute(EJBReceiver receiver, L originalLocator, Affinity newAffinity) throws Exception;
    }

    <R, L extends EJBLocator<T>, T> R performLocatedAction(final L locator, final LocatedAction<R, L, T> locatedAction) throws Exception {
        final Affinity affinity = locator.getAffinity();
        final String scheme;
        if (affinity instanceof NodeAffinity) {
            return discoverFirst(locator, locatedAction);
        } else if (affinity instanceof ClusterAffinity) {
            return discoverFirst(locator, locatedAction);
        } else if (affinity == Affinity.LOCAL) {
            scheme = "local";
        } else if (affinity instanceof URIAffinity) {
            scheme = affinity.getUri().getScheme();
        } else {
            assert affinity == Affinity.NONE;
            return discoverFirst(locator, locatedAction);
        }
        final EJBReceiver transportProvider = getTransportProvider(scheme);
        if (transportProvider == null) {
            throw Logs.MAIN.noEJBReceiverAvailable(locator);
        } else {
            return locatedAction.execute(transportProvider, locator, locator.getAffinity());
        }
    }

    <R, L extends EJBLocator<T>, T> R discoverFirst(L locator, final LocatedAction<R, L, T> locatedAction) throws Exception {
        final FilterSpec filterSpec;
        final Affinity affinity = locator.getAffinity();
        if (affinity == Affinity.NONE) {
            final String appName = locator.getAppName();
            final String moduleName = locator.getModuleName();
            final String beanName = locator.getBeanName();
            final String distinctName = locator.getDistinctName();
            if (distinctName != null && ! distinctName.isEmpty()) {
                if (appName.isEmpty()) {
                    filterSpec = FilterSpec.equal(FILTER_ATTR_EJB_MODULE_DISTINCT, '"' + moduleName + '/' + distinctName + '"');
                } else {
                    filterSpec = FilterSpec.equal(FILTER_ATTR_EJB_MODULE_DISTINCT, '"' + appName + '/' + moduleName + '/' + distinctName + '"');
                }
            } else {
                if (appName.isEmpty()) {
                    filterSpec = FilterSpec.equal(FILTER_ATTR_EJB_MODULE, '"' + moduleName + '"');
                } else {
                    filterSpec = FilterSpec.equal(FILTER_ATTR_EJB_MODULE, '"' + appName + '/' + moduleName + '"');
                }
            }
        } else if (affinity instanceof NodeAffinity) {
            filterSpec = FilterSpec.equal(FILTER_ATTR_NODE, ((NodeAffinity) affinity).getNodeName());
        } else if (affinity instanceof ClusterAffinity) {
            filterSpec = FilterSpec.equal(FILTER_ATTR_CLUSTER, ((ClusterAffinity) affinity).getClusterName());
        } else {
            return performLocatedAction(locator, locatedAction);
        }

        ServiceURL serviceURL;
        EJBReceiver receiver;
        try (final ServicesQueue servicesQueue = discover(filterSpec)) {
            for (;;) {
                try {
                    serviceURL = servicesQueue.takeService();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw Logs.MAIN.operationInterrupted();
                }
                if (serviceURL == null) {
                    throw Logs.MAIN.noEJBReceiverAvailable(locator);
                }
                // check that we support the URI scheme
                final URI uri = serviceURL.getLocationURI();
                receiver = getTransportProvider(uri.getScheme());
                if (receiver == null) {
                    // unsupported, skip it
                    continue;
                }
                // check that we satisfy the service URL
                final List<AttributeValue> values = serviceURL.getAttributeValues(FILTER_ATTR_SOURCE_IP);
                boolean matches = values.isEmpty();
                if (matches) {
                    // we got one!
                    break;
                } else {
                    SocketAddress sourceAddress = receiver.getSourceAddress(uri);
                    InetAddress inetAddress;
                    if (sourceAddress instanceof InetSocketAddress) {
                        inetAddress = ((InetSocketAddress) sourceAddress).getAddress();
                    } else {
                        inetAddress = null;
                    }
                    int bestNetmask;
                    for (AttributeValue value : values) try {
                        if (! value.isString()) {
                            continue;
                        }
                        final String string = value.toString();
                        if (string.codePointAt(0) != '[') {
                            continue;
                        }
                        int closeBrace = string.indexOf(']', 1);
                        if (closeBrace == -1) {
                            continue;
                        }
                        final InetAddress matchAddress = InetAddress.getByName(string.substring(1, closeBrace));
                        if (string.codePointAt(closeBrace + 1) != '/') {
                            continue;
                        }
                        final int mask = Integer.parseInt(string.substring(closeBrace + 2));

                        // now do the test
                        if (inetAddress == null) {
                            if (mask == 0) {
                                // it's zero, so we can just break out now because we have the only match we're gonna get
                                break;
                            }
                            // else fall out because we have no source address to test
                        } else if (NetworkUtil.belongsToNetwork(inetAddress, matchAddress, mask)) {
                            // matched!
                            break;
                        }
                    } catch (RuntimeException ignored) {
                        // it's not a valid entry, so ignore it
                    }

                    // try again
                }
            }
        }
        final URI uri = serviceURL.getLocationURI();
        return locatedAction.execute(receiver, locator, Affinity.forUri(uri));
    }

    EJBClientInterceptor[] getInterceptors() {
        return interceptors;
    }

    private static int getInt(byte[] b, int offs) {
        return (b[offs] & 0xff) << 24 | (b[offs + 1] & 0xff) << 16 | (b[offs + 2] & 0xff) << 8 | b[offs + 3] & 0xff;
    }

    private static long getLong(byte[] b, int offs) {
        return (getInt(b, offs) & 0xFFFFFFFFL) << 32 | getInt(b, offs + 4) & 0xFFFFFFFFL;
    }

    private static int nwsl(int arg, int places) {
        return places <= 0 ? arg : places >= 32 ? 0 : arg << places;
    }

    private static long nwsl(long arg, int places) {
        return places <= 0 ? arg : places >= 64 ? 0L : arg << places;
    }
}
