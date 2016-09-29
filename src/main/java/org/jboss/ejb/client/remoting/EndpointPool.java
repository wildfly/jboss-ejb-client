/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.DuplicateRegistrationException;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.HandleableCloseable;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.ServiceRegistrationException;
import org.jboss.remoting3.UnknownURISchemeException;
import org.jboss.remoting3.remote.HttpUpgradeConnectionProviderFactory;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.sasl.util.SaslFactories;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslClientFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool which creates and hands out a {@link Endpoint} based on the endpoint creation attributes
 *
 * @author Jaikiran Pai
 *         Courtesy: Remote naming project
 */
class EndpointPool {

    private static final Logger logger = Logger.getLogger(EndpointPool.class);

    static final EndpointPool INSTANCE = new EndpointPool();

    static final Thread SHUTDOWN_TASK = new Thread(new ShutdownTask(INSTANCE));

    static {
        SecurityActions.addShutdownHook(SHUTDOWN_TASK);
    }

    private final ConcurrentMap<CacheKey, PooledEndpoint> cache = new ConcurrentHashMap<CacheKey, PooledEndpoint>();

    private EndpointPool() {

    }

    synchronized Endpoint getEndpoint(final String endpointName, final OptionMap endPointCreationOptions, final OptionMap remoteConnectionProviderOptions) throws IOException {
        final CacheKey key = new CacheKey(remoteConnectionProviderOptions, endPointCreationOptions, endpointName);
        PooledEndpoint pooledEndpoint = cache.get(key);
        if (pooledEndpoint == null) {
            final Endpoint endpoint = Endpoint.getCurrent();

            // We don't want to hold stale endpoint(s), so add a close handler which removes the entry
            // from the cache when the endpoint is closed
            endpoint.addCloseHandler(new CacheEntryRemovalHandler(key));

            pooledEndpoint = new PooledEndpoint(key, endpoint);
            cache.putIfAbsent(key, pooledEndpoint);
        }
        pooledEndpoint.referenceCount.incrementAndGet();
        return pooledEndpoint;
    }

    private synchronized void release(final CacheKey endpointHash, final boolean async) {
        final PooledEndpoint pooledEndpoint = cache.get(endpointHash);
        if (pooledEndpoint.referenceCount.decrementAndGet() == 0) {
            try {
                if (async) {
                    pooledEndpoint.underlyingEndpoint.closeAsync();
                } else {
                    safeClose(pooledEndpoint.underlyingEndpoint);
                }
            } finally {
                cache.remove(endpointHash);
            }
        }
    }

    private synchronized void shutdown() {
        for (Map.Entry<CacheKey, PooledEndpoint> entry : cache.entrySet()) {
            safeClose(entry.getValue().underlyingEndpoint);
        }
        cache.clear();

        if(Thread.currentThread().getId() != SHUTDOWN_TASK.getId())
            SecurityActions.removeShutdownHook(SHUTDOWN_TASK);
    }

    /**
     * The pooled endpoint
     */
    private class PooledEndpoint implements Endpoint {
        private final AtomicInteger referenceCount = new AtomicInteger(0);
        private final CacheKey endpointHash;
        private final Endpoint underlyingEndpoint;

        private PooledEndpoint(final CacheKey endpointHash, final Endpoint endpoint) {
            this.endpointHash = endpointHash;
            this.underlyingEndpoint = endpoint;
        }

        public String getName() {
            return underlyingEndpoint.getName();
        }

        public Registration registerService(String s, OpenListener openListener, OptionMap optionMap) throws ServiceRegistrationException {
            return underlyingEndpoint.registerService(s, openListener, optionMap);
        }

        public IoFuture<Connection> getConnection(URI destination) {
            return underlyingEndpoint.getConnection(destination);
        }

        public IoFuture<Connection> connect(URI uri) throws IOException {
            return underlyingEndpoint.connect(uri);
        }

        public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions) throws IOException {
            return underlyingEndpoint.connect(destination, connectOptions, AuthenticationContext.captureCurrent(), SaslFactories.getElytronSaslClientFactory());
        }

        public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, SaslClientFactory saslClientFactory) throws IOException {
            return underlyingEndpoint.connect(destination, connectOptions, AuthenticationContext.captureCurrent(), saslClientFactory);
        }

        public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext) throws IOException {
            return underlyingEndpoint.connect(destination, connectOptions, authenticationContext, SaslFactories.getElytronSaslClientFactory());
        }

        public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext, SaslClientFactory saslClientFactory) throws IOException {
            return underlyingEndpoint.connect(destination, null, connectOptions, authenticationContext, saslClientFactory);
        }

        public IoFuture<Connection> connect(URI destination, InetSocketAddress bindAddress, OptionMap connectOptions, AuthenticationContext authenticationContext, SaslClientFactory saslClientFactory) throws IOException {
            return underlyingEndpoint.connect(destination, bindAddress, connectOptions, authenticationContext, saslClientFactory);
        }

        public boolean isConnected(URI uri) {
            return underlyingEndpoint.isConnected(uri);
        }

        public Registration addConnectionProvider(String uriScheme, ConnectionProviderFactory providerFactory, OptionMap optionMap) throws DuplicateRegistrationException, IOException {
            return underlyingEndpoint.addConnectionProvider(uriScheme, providerFactory, optionMap);
        }

        public <T> T getConnectionProviderInterface(String uriScheme, Class<T> expectedType) throws UnknownURISchemeException, ClassCastException {
            return underlyingEndpoint.getConnectionProviderInterface(uriScheme, expectedType);
        }

        public boolean isValidUriScheme(String s) {
            return underlyingEndpoint.isValidUriScheme(s);
        }

        public XnioWorker getXnioWorker() {
            return underlyingEndpoint.getXnioWorker();
        }

        public void close() throws IOException {
            EndpointPool.this.release(endpointHash, false);
        }

        public void awaitClosed() throws InterruptedException {
            underlyingEndpoint.awaitClosed();
        }

        public void awaitClosedUninterruptibly() {
            underlyingEndpoint.awaitClosedUninterruptibly();
        }

        public void closeAsync() {
            EndpointPool.this.release(endpointHash, true);
        }

        public Key addCloseHandler(CloseHandler<? super Endpoint> closeHandler) {
            return underlyingEndpoint.addCloseHandler(closeHandler);
        }

        public Attachments getAttachments() {
            return underlyingEndpoint.getAttachments();
        }
    }

    private static class CacheKey {
        final String endpointName;
        final OptionMap connectOptions;
        final OptionMap remoteConnectionProviderOptions;

        private CacheKey(final OptionMap remoteConnectionProviderOptions, final OptionMap connectOptions, final String endpointName) {
            this.remoteConnectionProviderOptions = remoteConnectionProviderOptions;
            this.connectOptions = connectOptions;
            this.endpointName = endpointName;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final CacheKey cacheKey = (CacheKey) o;

            if (connectOptions != null ? !connectOptions.equals(cacheKey.connectOptions) : cacheKey.connectOptions != null)
                return false;
            if (endpointName != null ? !endpointName.equals(cacheKey.endpointName) : cacheKey.endpointName != null)
                return false;
            if (remoteConnectionProviderOptions != null ? !remoteConnectionProviderOptions.equals(cacheKey.remoteConnectionProviderOptions) : cacheKey.remoteConnectionProviderOptions != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = endpointName != null ? endpointName.hashCode() : 0;
            result = 31 * result + (connectOptions != null ? connectOptions.hashCode() : 0);
            result = 31 * result + (remoteConnectionProviderOptions != null ? remoteConnectionProviderOptions.hashCode() : 0);
            return result;
        }
    }

    private static void safeClose(Closeable closable) {
        try {
            closable.close();
        } catch (Throwable t) {
            logger.debug("Failed to close endpoint ", t);
        }
    }

    /**
     * A {@link Runtime#addShutdownHook(Thread) shutdown task} which {@link org.jboss.ejb.client.remoting.EndpointPool#shutdown() shuts down}
     * the endpoint pool
     */
    private static final class ShutdownTask implements Runnable {
        private final EndpointPool pool;

        ShutdownTask(final EndpointPool pool) {
            this.pool = pool;
        }

        @Override
        public void run() {
            // close all pooled connections
            pool.shutdown();
        }
    }

    /**
     * A {@link CloseHandler} which removes a entry from the {@link EndpointPool}.
     */
    private class CacheEntryRemovalHandler implements CloseHandler<HandleableCloseable> {

        private final CacheKey key;

        CacheEntryRemovalHandler(final CacheKey key) {
            this.key = key;
        }

        @Override
        public void handleClose(HandleableCloseable closable, IOException e) {
            cache.remove(this.key);
        }
    }

}
