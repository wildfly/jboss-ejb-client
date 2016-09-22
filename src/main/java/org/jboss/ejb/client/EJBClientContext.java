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

import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.jboss.ejb._private.Logs;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.discovery.Discovery;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceType;
import org.wildfly.discovery.ServicesQueue;
import org.wildfly.discovery.spi.DiscoveryProvider;

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

    static final Supplier<EJBClientContext> GETTER = doPrivileged((PrivilegedAction<Supplier<EJBClientContext>>) CONTEXT_MANAGER::getPrivilegedSupplier);

    private static final EJBClientInterceptor[] NO_INTERCEPTORS = new EJBClientInterceptor[0];
    private static final EJBTransportProvider[] NO_TRANSPORT_PROVIDERS = new EJBTransportProvider[0];
    private static final DiscoveryProvider[] NO_DISCOVERY_PROVIDERS = new DiscoveryProvider[0];

    static final String FILTER_ATTR_EJB_APP = "ejb-app";
    static final String FILTER_ATTR_EJB_MODULE = "ejb-module";
    static final String FILTER_ATTR_EJB_BEAN = "ejb-bean";
    static final String FILTER_ATTR_EJB_DISTINCT = "ejb-distinct";
    static final String FILTER_ATTR_EJB_APP_DISTINCT = "ejb-app-distinct";
    static final String FILTER_ATTR_EJB_MODULE_DISTINCT = "ejb-module-distinct";
    static final String FILTER_ATTR_EJB_BEAN_DISTINCT = "ejb-bean-distinct";

    static {
        CONTEXT_MANAGER.setGlobalDefaultSupplier(new ConfigurationBasedEJBClientContextSelector());
    }

    private final EJBClientInterceptor[] interceptors;
    private final EJBTransportProvider[] transportProviders;
    private final DiscoveryProvider[] discoveryProviders;
    private final long invocationTimeout;
    private final Discovery discovery;

    EJBClientContext(Builder builder) {
        final List<EJBClientInterceptor> builderInterceptors = builder.interceptors;
        if (builderInterceptors == null || builderInterceptors.isEmpty()) {
            interceptors = NO_INTERCEPTORS;
        } else {
            interceptors = builderInterceptors.toArray(new EJBClientInterceptor[builderInterceptors.size()]);
        }
        final List<EJBTransportProvider> builderTransportProviders = builder.transportProviders;
        if (builderTransportProviders == null || builderTransportProviders.isEmpty()) {
            transportProviders = NO_TRANSPORT_PROVIDERS;
        } else {
            transportProviders = builderTransportProviders.toArray(new EJBTransportProvider[builderTransportProviders.size()]);
        }
        final ArrayList<DiscoveryProvider> discoveryProviders = new ArrayList<>();
        for (EJBTransportProvider transportProvider : transportProviders) {
            final DiscoveryProvider discoveryProvider = transportProvider.getDiscoveryProvider();
            if (discoveryProvider != null) {
                discoveryProviders.add(discoveryProvider);
            }
        }
        if (builder.discoveryProviders != null) discoveryProviders.addAll(builder.discoveryProviders);
        discovery = Discovery.create(this.discoveryProviders = discoveryProviders.toArray(NO_DISCOVERY_PROVIDERS));
        invocationTimeout = 0;
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
     * Get a copy of this context with the given discovery provider(s) added.  If the array is {@code null} or empty, the
     * current context is returned as-is.
     *
     * @param discoveryProviders the discovery providers(s) to add
     * @return the new context (not {@code null})
     */
    public EJBClientContext withAddedDiscoveryProviders(DiscoveryProvider... discoveryProviders) {
        if (discoveryProviders == null) {
            return this;
        }
        final int length = discoveryProviders.length;
        if (length == 0) {
            return this;
        }
        final Builder builder = new Builder(this);
        boolean construct = false;
        for (DiscoveryProvider discoveryProvider : discoveryProviders) {
            if (discoveryProvider != null) {
                builder.addDiscoveryProvider(discoveryProvider);
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
                return transportProvider.getReceiver(scheme);
            }
        }
        return null;
    }

    ServicesQueue discover(final FilterSpec filterSpec) {
        return discovery.discover(EJB_SERVICE_TYPE, filterSpec);
    }

    EJBTransportProvider[] getTransportProviders() {
        return transportProviders;
    }

    Discovery getDiscovery() {
        return discovery;
    }

    DiscoveryProvider[] getDiscoveryProviders() {
        return discoveryProviders;
    }

    /**
     * A builder for EJB client contexts.
     */
    public static final class Builder {

        List<EJBClientInterceptor> interceptors;
        List<EJBTransportProvider> transportProviders;
        List<DiscoveryProvider> discoveryProviders;

        /**
         * Construct a new instance.
         */
        public Builder() {
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
            final DiscoveryProvider[] discoveryProviders = ejbClientContext.getDiscoveryProviders();
            if (discoveryProviders.length > 0) {
                this.discoveryProviders = new ArrayList<>(Arrays.asList(discoveryProviders));
            }
        }

        public void addInterceptor(EJBClientInterceptor interceptor) {
            if (interceptor == null) {
                throw new IllegalArgumentException("interceptor is null");
            }
            if (interceptors == null) {
                interceptors = new ArrayList<>();
            }
            interceptors.add(interceptor);
        }

        public void addTransportProvider(EJBTransportProvider provider) {
            if (provider == null) {
                throw new IllegalArgumentException("provider is null");
            }
            if (transportProviders == null) {
                transportProviders = new ArrayList<>();
            }
            transportProviders.add(provider);
        }

        public void addDiscoveryProvider(DiscoveryProvider provider) {
            if (provider == null) {
                throw new IllegalArgumentException("provider is null");
            }
            if (discoveryProviders == null) {
                discoveryProviders = new ArrayList<>();
            }
            discoveryProviders.add(provider);
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
        return GETTER.get();
    }

    /**
     * Get the current client context for this thread, throwing an exception if none is set.
     *
     * @return the current client context
     * @throws IllegalStateException if the current client context is not set
     */
    public static EJBClientContext requireCurrent() throws IllegalStateException {
        final EJBClientContext clientContext = getCurrent();
        if (clientContext == null) {
            throw Logs.MAIN.noEJBClientContextAvailable();
        }
        return clientContext;
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
            scheme = ((URIAffinity) affinity).getUri().getScheme();
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
                filterSpec = FilterSpec.any(
                    FilterSpec.equal(FILTER_ATTR_EJB_APP, appName),
                    FilterSpec.equal(FILTER_ATTR_EJB_MODULE, appName + '/' + moduleName),
                    FilterSpec.equal(FILTER_ATTR_EJB_BEAN, appName + '/' + moduleName + '/' + beanName),
                    FilterSpec.equal(FILTER_ATTR_EJB_DISTINCT, distinctName),
                    FilterSpec.equal(FILTER_ATTR_EJB_APP_DISTINCT, appName + '/' + distinctName),
                    FilterSpec.equal(FILTER_ATTR_EJB_MODULE_DISTINCT, appName + '/' + moduleName + '/' + distinctName),
                    FilterSpec.equal(FILTER_ATTR_EJB_BEAN_DISTINCT, appName + '/' + moduleName + '/' + beanName + '/' + distinctName)
                );
            } else {
                filterSpec = FilterSpec.any(
                    FilterSpec.equal(FILTER_ATTR_EJB_APP, appName),
                    FilterSpec.equal(FILTER_ATTR_EJB_MODULE, appName + '/' + moduleName),
                    FilterSpec.equal(FILTER_ATTR_EJB_BEAN, appName + '/' + moduleName + '/' + beanName)
                );
            }
        } else if (affinity instanceof NodeAffinity) {
            filterSpec = FilterSpec.equal("node", ((NodeAffinity) affinity).getNodeName());
        } else if (affinity instanceof ClusterAffinity) {
            filterSpec = FilterSpec.equal("cluster", ((ClusterAffinity) affinity).getClusterName());
        } else {
            return performLocatedAction(locator, locatedAction);
        }

        try (final ServicesQueue servicesQueue = discover(filterSpec)) {
            for (;;) {
                final URI uri;
                try {
                    uri = servicesQueue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                if (uri == null) {
                    return null;
                }
                final EJBReceiver receiver = getTransportProvider(uri.getScheme());
                if (receiver != null) {
                    return locatedAction.execute(receiver, locator, Affinity.forUri(uri));
                }
            }
        }
    }

    EJBClientInterceptor[] getInterceptors() {
        return interceptors;
    }
}
