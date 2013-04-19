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

import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.HandleableCloseable;
import org.jboss.remoting3.security.UserInfo;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import java.io.Closeable;
import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool which creates and hands out remoting {@link Connection}s and maintains a reference count to close the connections handed
 * out, when the count reaches zero.
 *
 * @author Jaikiran Pai
 * Courtesy: Remote naming project
 */
class ConnectionPool {
    private static final Logger logger = Logger.getLogger(ConnectionPool.class);

    static final ConnectionPool INSTANCE = new ConnectionPool();

    static {
        SecurityActions.addShutdownHook(new Thread(new ShutdownTask(INSTANCE)));
    }

    private final ConcurrentMap<CacheKey, PooledConnection> cache = new ConcurrentHashMap<CacheKey, PooledConnection>();

    private ConnectionPool() {

    }

    synchronized Connection getConnection(final Endpoint clientEndpoint, final String protocol, final String host, final int port, final EJBClientConfiguration.CommonConnectionCreationConfiguration connectionConfiguration) throws IOException {
        final CacheKey key = new CacheKey(clientEndpoint, connectionConfiguration.getCallbackHandler().getClass(), connectionConfiguration.getConnectionCreationOptions(), host, port, protocol);
        PooledConnection pooledConnection = cache.get(key);
        if (pooledConnection == null) {
            final IoFuture<Connection> futureConnection = NetworkUtil.connect(clientEndpoint, protocol, host, port, null, connectionConfiguration.getConnectionCreationOptions(), connectionConfiguration.getCallbackHandler(), null);
            // wait for the connection to be established
            final Connection connection = IoFutureHelper.get(futureConnection, connectionConfiguration.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            // We don't want to hold stale connection(s), so add a close handler which removes the entry
            // from the cache when the connection is closed
            connection.addCloseHandler(new CacheEntryRemovalHandler(key));

            pooledConnection = new PooledConnection(key, connection);
            // add it to the cache
            cache.put(key, pooledConnection);
        }
        pooledConnection.referenceCount.incrementAndGet();
        return pooledConnection;
    }

    synchronized void release(final CacheKey connectionHash, final boolean async) {
        final PooledConnection pooledConnection = cache.get(connectionHash);
        if (pooledConnection == null) {
            return;
        }
        if (pooledConnection.referenceCount.decrementAndGet() == 0) {
            try {
                final Connection connection = pooledConnection.underlyingConnection;
                if (async) {
                    connection.closeAsync();
                } else {
                    safeClose(connection);
                }
            } finally {
                cache.remove(connectionHash);
            }
        }
    }

    private synchronized void shutdown() {
        for (Map.Entry<CacheKey, PooledConnection> entry : cache.entrySet()) {
            final Connection connection = entry.getValue().underlyingConnection;
            safeClose(connection);
        }
        cache.clear();
    }

    /**
     * The key to the pooled connection
     */
    private static final class CacheKey {
        final Endpoint endpoint;
        final String host;
        final int port;
        final String protocol;
        final OptionMap connectOptions;
        final Class<?> callbackHandlerClass;

        private CacheKey(final Endpoint endpoint, final Class<?> callbackHandlerClass, final OptionMap connectOptions, final String host, final int port, final String protocol) {
            this.endpoint = endpoint;
            this.callbackHandlerClass = callbackHandlerClass;
            this.connectOptions = connectOptions;
            this.host = host;
            this.port = port;
            this.protocol = protocol;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (port != cacheKey.port) return false;
            if (callbackHandlerClass != null ? !callbackHandlerClass.equals(cacheKey.callbackHandlerClass) : cacheKey.callbackHandlerClass != null)
                return false;
            if (connectOptions != null ? !connectOptions.equals(cacheKey.connectOptions) : cacheKey.connectOptions != null)
                return false;
            if (endpoint != null ? !endpoint.equals(cacheKey.endpoint) : cacheKey.endpoint != null) return false;
            if (host != null ? !host.equals(cacheKey.host) : cacheKey.host != null) return false;
            if (protocol != null ? !protocol.equals(cacheKey.protocol) : cacheKey.protocol != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = endpoint != null ? endpoint.hashCode() : 0;
            result = 31 * result + (host != null ? host.hashCode() : 0);
            result = 31 * result + port;
            result = 31 * result + (connectOptions != null ? connectOptions.hashCode() : 0);
            result = 31 * result + (callbackHandlerClass != null ? callbackHandlerClass.hashCode() : 0);
            result = 31 * result + (protocol != null ? protocol.hashCode() : 0);
            return result;
        }
    }


    private static void safeClose(Closeable closable) {
        try {
            closable.close();
        } catch (Throwable t) {
            logger.debug("Failed to close " + closable, t);
        }
    }

    /**
     * The pooled connection
     */
    private final class PooledConnection implements Connection {
        private final AtomicInteger referenceCount = new AtomicInteger(0);
        private final CacheKey cacheKey;
        private final Connection underlyingConnection;

        PooledConnection(final CacheKey key, final Connection connection) {
            this.cacheKey = key;
            this.underlyingConnection = connection;
        }

        @Override
        public void close() throws IOException {
            release(this.cacheKey, false);
        }

        @Override
        public void closeAsync() {
            release(this.cacheKey, true);
        }

        @Override
        public void awaitClosed() throws InterruptedException {
            this.underlyingConnection.awaitClosed();
        }

        @Override
        public void awaitClosedUninterruptibly() {
            this.underlyingConnection.awaitClosedUninterruptibly();
        }

        @Override
        public Key addCloseHandler(CloseHandler<? super Connection> closeHandler) {
            return this.underlyingConnection.addCloseHandler(closeHandler);
        }

        @Override
        public Collection<Principal> getPrincipals() {
            return this.underlyingConnection.getPrincipals();
        }

        @Override
        public UserInfo getUserInfo() {
            return this.underlyingConnection.getUserInfo();
        }

        @Override
        public IoFuture<Channel> openChannel(String s, OptionMap options) {
            return this.underlyingConnection.openChannel(s, options);
        }

        @Override
        public String getRemoteEndpointName() {
            return this.underlyingConnection.getRemoteEndpointName();
        }

        @Override
        public Endpoint getEndpoint() {
            return this.underlyingConnection.getEndpoint();
        }

        @Override
        public Attachments getAttachments() {
            return this.underlyingConnection.getAttachments();
        }
    }

    /**
     * A {@link Runtime#addShutdownHook(Thread) shutdown task} which {@link org.jboss.ejb.client.remoting.ConnectionPool#shutdown() shuts down}
     * the connection pool
     */
    private static final class ShutdownTask implements Runnable {

        private final ConnectionPool pool;

        ShutdownTask(final ConnectionPool pool) {
            this.pool = pool;
        }

        @Override
        public void run() {
            // close all pooled connections
            pool.shutdown();
        }
    }

    /**
     * A {@link CloseHandler} which removes a entry from the {@link ConnectionPool}.
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
