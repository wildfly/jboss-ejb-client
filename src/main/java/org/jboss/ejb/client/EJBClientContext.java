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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ejb.CreateException;

import org.wildfly.common.selector.Selector;

/**
 * The public API for an EJB client context.  An EJB client context may be associated with (and used by) one or more threads concurrently.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientContext extends Attachable {

    private static final EJBClientInterceptor[] NO_INTERCEPTORS = new EJBClientInterceptor[0];
    private static final EJBTransportProvider[] NO_TRANSPORT_PROVIDERS = new EJBTransportProvider[0];
    private static final EJBDiscoveryProvider[] NO_DISCOVERY_PROVIDERS = new EJBDiscoveryProvider[0];

    private static final Selector.Getter<EJBClientContext> SELECTOR_GETTER = Selector.selectorGetterFor(EJBClientContext.class);

    private final EJBClientInterceptor[] interceptors;
    private final EJBTransportProvider[] transportProviders;
    private final EJBDiscoveryProvider[] discoveryProviders;
    private final EJBReceiver noAffinityReceiver;
    private final long invocationTimeout;

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
        final List<EJBDiscoveryProvider> builderDiscoveryProviders = builder.discoveryProviders;
        if (builderDiscoveryProviders == null || builderDiscoveryProviders.isEmpty()) {
            discoveryProviders = NO_DISCOVERY_PROVIDERS;
        } else {
            discoveryProviders = builderDiscoveryProviders.toArray(new EJBDiscoveryProvider[builderDiscoveryProviders.size()]);
        }
        // todo: use a discovery-aware receiver here
        noAffinityReceiver = new EJBReceiver() {
            protected void processInvocation(final EJBClientInvocationContext clientInvocationContext, final EJBReceiverInvocationContext receiverContext) throws Exception {

            }

            protected <T> StatefulEJBLocator<T> openSession(final StatelessEJBLocator<T> statelessLocator) throws IllegalArgumentException, CreateException {
                return null;
            }
        };
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
        List<EJBDiscoveryProvider> discoveryProviders;
        List<EJBTransportProvider> transportProviders;

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

        public void addDiscoveryProvider(EJBDiscoveryProvider provider) {
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
        return getEJBReceiver(locator.getAffinity());
    }

    /**
     * Get the first EJB receiver which matches the given affinity.
     *
     * @param affinity the affinity of the invocation target
     * @return the first EJB receiver to match, or {@code null} if none match
     */
    EJBReceiver getEJBReceiver(final Affinity affinity) {
        final String scheme;
        if (affinity instanceof NodeAffinity) {
            scheme = "node";
        } else if (affinity instanceof ClusterAffinity) {
            scheme = "cluster";
        } else if (affinity instanceof URIAffinity) {
            scheme = ((URIAffinity) affinity).getUri().getScheme();
        } else if (affinity == Affinity.LOCAL) {
            scheme = "local";
        } else {
            assert affinity == Affinity.NONE;
            return noAffinityReceiver;
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
        return requireEJBReceiver(locator.getAffinity());
    }

    /**
     * Get the first EJB receiver which matches the given affinity. If there's
     * no such EJB receiver, then this method throws a {@link IllegalStateException}.
     *
     * @param affinity the affinity of the invocation target
     * @return the first EJB receiver to match
     * @throws IllegalStateException if there's no {@link EJBReceiver} that matches
     */
    EJBReceiver requireEJBReceiver(final Affinity affinity) throws IllegalStateException {
        // try and find a receiver which can handle this combination
        final EJBReceiver ejbReceiver = this.getEJBReceiver(affinity);
        if (ejbReceiver == null) {
            throw Logs.MAIN.noEJBReceiverAvailable(affinity);
        }
        return ejbReceiver;
    }

    EJBClientInterceptor[] getInterceptors() {
        return interceptors;
    }

    EJBReceiver discoverFirst(EJBLocator<?> locator) {
        final LinkedBlockingQueue<URI> queue = new LinkedBlockingQueue<>();
        final ListDiscoveryResult discoveryResult = new ListDiscoveryResult(queue);
        int count = discoveryProviders.length;
        for (EJBDiscoveryProvider discoveryProvider : discoveryProviders) {
            discoveryProvider.discover(locator, discoveryResult);
        }
        while (count > 0) {
            final URI uri;
            try {
                uri = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // cancel operation cleanly
                return null;
            }
            if (uri == DISCOVERY_DONE_MARKER) {
                count--;
            } else {
                final EJBReceiver receiver = getEJBReceiver(URIAffinity.forUri(uri));
                if (receiver != null) {
                    return receiver;
                }
            }
        }
        return null;
    }

    private static final URI DISCOVERY_DONE_MARKER;

    static {
        try {
            DISCOVERY_DONE_MARKER = new URI("done:done");
        } catch (URISyntaxException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final class ListDiscoveryResult implements EJBDiscoveryProvider.DiscoveryResult {
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final BlockingQueue<URI> queue;

        ListDiscoveryResult(final BlockingQueue<URI> queue) {
            this.queue = queue;
        }

        public void complete() {
            if (done.compareAndSet(false, true)) {
                queue.add(DISCOVERY_DONE_MARKER);
            }
        }

        public void addMatch(final URI uri) {
            if (uri != null && ! done.get()) {
                // if the queue is full, drop
                queue.offer(uri);
            }
        }
    }
}
