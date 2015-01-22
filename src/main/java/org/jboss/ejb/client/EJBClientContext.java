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
public final class EJBClientContext extends Attachable {

    private static final EJBClientInterceptor[] NO_INTERCEPTORS = new EJBClientInterceptor[0];
    private static final EJBTransportProvider[] NO_TRANSPORT_PROVIDERS = new EJBTransportProvider[0];

    private static final Selector.Getter<EJBClientContext> SELECTOR_GETTER = Selector.selectorGetterFor(EJBClientContext.class);
    private static final ServiceType EJB_SERVICE_TYPE = ServiceType.of("ejb", "jboss");

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
        discovery = Discovery.create(builder.discoveryProviders.toArray(new DiscoveryProvider[builder.discoveryProviders.size()]));
        invocationTimeout = 0;
    }

    public long getInvocationTimeout() {
        return invocationTimeout;
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
            transportProviders.add(provider);
        }

        public void addDiscoveryProvider(DiscoveryProvider provider) {
            if (provider == null) {
                throw new IllegalArgumentException("provider is null");
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

    /**
     * Get the first EJB receiver which matches the given EJB locator.
     *
     * @param locator the locator of the invocation target
     * @return the first EJB receiver to match, or {@code null} if none match
     */
    EJBReceiver getEJBReceiver(final EJBLocator<?> locator) {
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
        for (EJBTransportProvider transportProvider : transportProviders) {
            if (transportProvider.supportsProtocol(scheme)) {
                return transportProvider.getReceiver(scheme);
            }
        }
        return null;
    }

    /**
     * Get the first EJB receiver which matches the given EJB locator. If there's
     * no such EJB receiver, then this method throws a {@link IllegalStateException}.
     *
     * @param locator the locator of the invocation target
     * @return the first EJB receiver to match
     * @throws IllegalStateException if there's no {@link EJBReceiver} that matches
     */
    EJBReceiver requireEJBReceiver(final EJBLocator<?> locator) throws IllegalStateException {
        final EJBReceiver receiver = getEJBReceiver(locator);
        if (receiver == null) {
            throw Logs.MAIN.noEJBReceiverAvailable(locator.getAffinity());
        }
        return receiver;
    }

    EJBClientInterceptor[] getInterceptors() {
        return interceptors;
    }

    EJBReceiver discoverFirst(EJBLocator<?> locator) {
        final Discovery discovery = this.discovery;
        final FilterSpec filterSpec;
        if (locator.getAffinity() == Affinity.NONE) {
            String primary = locator.getAppName() + '/' + locator.getModuleName();
            String secondary;
            if (locator.getDistinctName() != null) {
                secondary = locator.getBeanName() + '/' + locator.getDistinctName();
            } else {
                secondary = locator.getBeanName();
            }
            filterSpec = FilterSpec.any(
                FilterSpec.equal("module", primary),
                FilterSpec.equal("bean", primary + '/' + secondary)
            );
        } else if (locator.getAffinity() instanceof NodeAffinity) {
            filterSpec = FilterSpec.equal("node", ((NodeAffinity) locator.getAffinity()).getNodeName());
        } else if (locator.getAffinity() instanceof ClusterAffinity) {
            filterSpec = FilterSpec.equal("cluster", ((ClusterAffinity) locator.getAffinity()).getClusterName());
        } else {
            return requireEJBReceiver(locator);
        }

        try (final ServicesQueue servicesQueue = discovery.discover(EJB_SERVICE_TYPE, filterSpec)) {
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
                String scheme = uri.getScheme();
                for (EJBTransportProvider transportProvider : transportProviders) {
                    if (transportProvider.supportsProtocol(scheme)) {
                        return transportProvider.getReceiver(scheme);
                    }
                }
            }
        }
    }
}
