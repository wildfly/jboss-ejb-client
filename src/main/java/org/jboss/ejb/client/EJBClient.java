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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.concurrent.Future;

import javax.ejb.CreateException;
import javax.transaction.UserTransaction;

/**
 * The main EJB client API class.  This class contains helper methods which may be used to create proxies, open sessions,
 * and associate the current invocation context.
 *
 * @author jpai
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClient {

    private static final Logs log = Logs.MAIN;

    static {
        log.greeting(Version.getVersionString());
    }

    private EJBClient() {
    }

    private static final ThreadLocal<Future<?>> FUTURE_RESULT = new ThreadLocal<Future<?>>();

    /**
     * Get an asynchronous view of a proxy.  Any {@code void} method on the proxy will be invoked fully asynchronously
     * without a server round-trip delay.  Any method which returns a {@link java.util.concurrent.Future Future} will
     * continue to be asynchronous.  Any other method invoked on the returned proxy will return {@code null} (the future
     * result can be acquired by wrapping the remote call with {@link #getFutureResult(Object)} or by using {@link #getFutureResult()}).
     * If an asynchronous view is passed in, the same view is returned.
     *
     * @param proxy the proxy interface instance
     * @param <T>   the proxy type
     * @return the asynchronous view
     * @throws IllegalArgumentException if the given object is not a valid proxy
     */
    @SuppressWarnings("unchecked")
    public static <T> T asynchronous(final T proxy) throws IllegalArgumentException {
        final InvocationHandler invocationHandler = Proxy.getInvocationHandler(proxy);
        if (invocationHandler instanceof EJBInvocationHandler) {
            final EJBInvocationHandler<?> remoteInvocationHandler = (EJBInvocationHandler<?>) invocationHandler;
            // determine proxy "type", return existing instance if it's already async
            if (remoteInvocationHandler.isAsyncHandler()) {
                return proxy;
            } else {
                return (T) Proxy.newProxyInstance(proxy.getClass().getClassLoader(), proxy.getClass().getInterfaces(), remoteInvocationHandler.getAsyncHandler());
            }
        } else {
            throw log.unknownProxy(proxy);
        }
    }

    /**
     * Get the future result of an operation.  Should be called in conjunction with {@link #asynchronous(Object)}.
     *
     * @param operation the operation
     * @param <T>       the result type
     * @return the future result
     * @throws IllegalStateException if the operation is not appropriately given
     */
    @SuppressWarnings("unchecked")
    public static <T> Future<T> getFutureResult(final T operation) throws IllegalStateException {
        if (operation != null) {
            return new FinishedFuture<T>(operation);
        }
        final ThreadLocal<Future<?>> futureResult = FUTURE_RESULT;
        try {
            final Future<?> future = futureResult.get();
            if (future == null) throw log.noAsyncInProgress();
            return (Future<T>) future;
        } finally {
            futureResult.remove();
        }
    }

    /**
     * Get the future result of an operation.  Should be called in conjunction with {@link #asynchronous(Object)}.
     *
     * @return the future result
     * @throws IllegalStateException if the operation is not appropriately given
     */
    public static Future<?> getFutureResult() throws IllegalStateException {
        final ThreadLocal<Future<?>> futureResult = FUTURE_RESULT;
        try {
            final Future<?> future = futureResult.get();
            if (future == null) throw log.noAsyncInProgress();
            return future;
        } finally {
            futureResult.remove();
        }
    }

    static void setFutureResult(final Future<?> future) {
        FUTURE_RESULT.set(future);
    }

    /**
     * Create a new proxy for the remote object identified by the given locator.
     *
     * @param locator the locator
     * @param <T>     the proxy type
     * @return the new proxy
     * @throws IllegalArgumentException if the locator parameter is {@code null} or is invalid
     */
    public static <T> T createProxy(final EJBLocator<T> locator) throws IllegalArgumentException {
        if (locator == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB locator");
        }
        return locator.createProxyInstance(new EJBInvocationHandler<T>(locator));
    }

    /**
     * Determine whether an object is indeed a valid EJB proxy object created by this API.
     *
     * @param object the object to test
     * @return {@code true} if it is an EJB proxy, {@code false} otherwise
     */
    public static boolean isEJBProxy(final Object object) {
        return object != null && Proxy.isProxyClass(object.getClass()) && Proxy.getInvocationHandler(object) instanceof EJBInvocationHandler;
    }

    /**
     * Create a new EJB session.
     *
     * @param viewType     the view type class
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the EJB name
     * @param distinctName the module distinct name
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    public static <T> StatefulEJBLocator<T> createSession(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) throws CreateException {
        return createSession(new StatelessEJBLocator<T>(viewType, appName, moduleName, beanName, distinctName, Affinity.NONE));
    }

    /**
     * Create a new EJB session.
     *
     * @param affinity     the affinity specification for the session
     * @param viewType     the view type class
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the EJB name
     * @param distinctName the module distinct name
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    public static <T> StatefulEJBLocator<T> createSession(final Affinity affinity, final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) throws CreateException {
        return createSession(new StatelessEJBLocator<T>(viewType, appName, moduleName, beanName, distinctName, affinity == null ? Affinity.NONE : affinity));
    }

    /**
     * Create a new EJB session.
     *
     * @param uri          a URI at which EJBs may be obtained
     * @param viewType     the view type class
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the EJB name
     * @param distinctName the module distinct name
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    public static <T> StatefulEJBLocator<T> createSession(final URI uri, final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) throws CreateException {
        final Affinity affinity = uri == null ? Affinity.NONE : Affinity.forUri(uri);
        return createSession(new StatelessEJBLocator<T>(viewType, appName, moduleName, beanName, distinctName, affinity));
    }

    /**
     * Create a new EJB session.
     *
     * @param viewType     the view type class
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the EJB name
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    public static <T> StatefulEJBLocator<T> createSession(final Class<T> viewType, final String appName, final String moduleName, final String beanName) throws CreateException {
        return createSession(new StatelessEJBLocator<T>(viewType, appName, moduleName, beanName, Affinity.NONE));
    }

    /**
     * Create a new EJB session.
     *
     * @param affinity     the affinity specification for the session
     * @param viewType     the view type class
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the EJB name
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    public static <T> StatefulEJBLocator<T> createSession(final Affinity affinity, final Class<T> viewType, final String appName, final String moduleName, final String beanName) throws CreateException {
        return createSession(new StatelessEJBLocator<T>(viewType, appName, moduleName, beanName, affinity == null ? Affinity.NONE : affinity));
    }

    /**
     * Create a new EJB session.
     *
     * @param uri          a URI at which EJBs may be obtained
     * @param viewType     the view type class
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the EJB name
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    public static <T> StatefulEJBLocator<T> createSession(final URI uri, final Class<T> viewType, final String appName, final String moduleName, final String beanName) throws CreateException {
        final Affinity affinity = uri == null ? Affinity.NONE : Affinity.forUri(uri);
        return createSession(new StatelessEJBLocator<T>(viewType, appName, moduleName, beanName, affinity));
    }

    /**
     * Create a new EJB session.
     *
     * @param statelessLocator the stateless locator identifying the stateful EJB
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    public static <T> StatefulEJBLocator<T> createSession(StatelessEJBLocator<T> statelessLocator) throws CreateException {
        final EJBReceiver ejbReceiver = EJBClientContext.getCurrent().getEJBReceiver(statelessLocator);
        return ejbReceiver.openSession(statelessLocator);
    }

    /**
     * Get the locator for a proxy, if it has one.
     *
     * @param proxy the proxy
     * @return the locator
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance
     */
    public static <T> EJBLocator<? extends T> getLocatorFor(T proxy) throws IllegalArgumentException {
        return EJBInvocationHandler.forProxy(proxy).getLocator();
    }

    /**
     * Get a {@code UserTransaction} object instance which can be used to control transactions on a specific node.
     *
     * @param targetNodeName the node name
     * @return the {@code UserTransaction} instance
     * @throws IllegalStateException if the transaction context isn't set or cannot provide a {@code UserTransaction} instance
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static UserTransaction getUserTransaction(String targetNodeName) {
        // TODO: use tx API with a specific URI
        throw new UnsupportedOperationException("Not re-implemented until txn client API is settled");
    }
}
