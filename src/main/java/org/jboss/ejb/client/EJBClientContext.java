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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.jboss.ejb._private.Logs;
import org.jboss.marshalling.Pair;
import org.wildfly.common.selector.DefaultSelector;
import org.wildfly.common.selector.Selector;
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
@DefaultSelector(ConfigurationBasedEJBClientContextSelector.class)
public final class EJBClientContext extends Attachable {

    /**
     * The service type to use for EJB discovery.
     */
    public static final ServiceType EJB_SERVICE_TYPE = ServiceType.of("ejb", "jboss");

    private static final EJBClientInterceptor[] NO_INTERCEPTORS = new EJBClientInterceptor[0];
    private static final EJBTransportProvider[] NO_TRANSPORT_PROVIDERS = new EJBTransportProvider[0];

    private static final Selector.Getter<EJBClientContext> SELECTOR_GETTER = Selector.selectorGetterFor(EJBClientContext.class);

    private final EJBClientInterceptor[] interceptors;
    private final EJBTransportProvider[] transportProviders;
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
        discovery = Discovery.create(discoveryProviders.toArray(new DiscoveryProvider[discoveryProviders.size()]));
        invocationTimeout = 0;
    }

    public long getInvocationTimeout() {
        return invocationTimeout;
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
        return SELECTOR_GETTER.getSelector().get();
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

    Pair<EJBReceiver, EJBLocator<?>> findEJBReceiver(final EJBLocator<?> locator) {
        final Affinity affinity = locator.getAffinity();
        final String scheme;
        if (affinity instanceof NodeAffinity) {
            return discoverFirst(locator);
        } else if (affinity instanceof ClusterAffinity) {
            return discoverFirst(locator);
        } else if (affinity == Affinity.LOCAL) {
            scheme = "local";
        } else if (affinity instanceof URIAffinity) {
            scheme = ((URIAffinity) affinity).getUri().getScheme();
        } else {
            assert affinity == Affinity.NONE;
            return discoverFirst(locator);
        }
        return new Pair<>(getTransportProvider(scheme), locator);
    }

    Pair<EJBReceiver, EJBLocator<?>> discoverFirst(EJBLocator<?> locator) {
        final FilterSpec filterSpec;
        final Affinity affinity = locator.getAffinity();
        if (affinity == Affinity.NONE) {
            final String appName = locator.getAppName();
            final String moduleName = locator.getModuleName();
            final String beanName = locator.getBeanName();
            final String distinctName = locator.getDistinctName();
            if (distinctName != null && ! distinctName.isEmpty()) {
                filterSpec = FilterSpec.any(
                    FilterSpec.equal("ejb-app", appName),
                    FilterSpec.equal("ejb-module", appName + '/' + moduleName),
                    FilterSpec.equal("ejb-bean", appName + '/' + moduleName + '/' + beanName),
                    FilterSpec.equal("ejb-distinct", distinctName),
                    FilterSpec.equal("ejb-app-distinct", appName + '/' + distinctName),
                    FilterSpec.equal("ejb-module-distinct", appName + '/' + moduleName + '/' + distinctName),
                    FilterSpec.equal("ejb-bean-distinct", appName + '/' + moduleName + '/' + beanName + '/' + distinctName)
                );
            } else {
                filterSpec = FilterSpec.any(
                    FilterSpec.equal("ejb-app", appName),
                    FilterSpec.equal("ejb-module", appName + '/' + moduleName),
                    FilterSpec.equal("ejb-bean", appName + '/' + moduleName + '/' + beanName)
                );
            }
        } else if (affinity instanceof NodeAffinity) {
            filterSpec = FilterSpec.equal("node", ((NodeAffinity) affinity).getNodeName());
        } else if (affinity instanceof ClusterAffinity) {
            filterSpec = FilterSpec.equal("cluster", ((ClusterAffinity) affinity).getClusterName());
        } else {
            final Pair<EJBReceiver, EJBLocator<?>> info = findEJBReceiver(locator);
            if (info == null) {
                throw Logs.MAIN.noEJBReceiverAvailable(locator);
            }
            return info;
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
                    final EJBLocator<?> newLocator = locator.withNewAffinity(Affinity.forUri(uri));
                    return new Pair<EJBReceiver, EJBLocator<?>>(receiver, newLocator);
                }
            }
        }
    }

    EJBClientInterceptor[] getInterceptors() {
        return interceptors;
    }
}
