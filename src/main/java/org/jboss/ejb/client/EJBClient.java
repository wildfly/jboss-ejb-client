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

import javax.transaction.UserTransaction;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.Future;

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
            final EJBInvocationHandler remoteInvocationHandler = (EJBInvocationHandler) invocationHandler;
            // determine proxy "type", return existing instance if it's already async
            if (true) {
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
        return createProxy(locator, null);
    }

    /**
     * Creates a new proxy for the remote object identified by the given <code>locator</code> and
     * associates that proxy with the passed {@link EJBClientContextIdentifier identifier}
     *
     * @param locator    The locator
     * @param identifier The EJB client context identifier to associate this proxy with. Can be null.
     * @param <T>        The proxy type
     * @return IllegalArgumentException if the locator {@code null}
     */
    public static <T> T createProxy(final EJBLocator<T> locator, final EJBClientContextIdentifier identifier) {
        if (locator == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB locator");
        }
        return locator.createProxyInstance(new EJBInvocationHandler(identifier, locator));
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
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the EJB name
     * @param distinctName the module distinct name
     * @return the new session ID
     * @throws Exception if an error occurs
     */
    // TODO: narrow exception type(s)
    public static <T> StatefulEJBLocator<T> createSession(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) throws Exception {
        return createSession(null, viewType, appName, moduleName, beanName, distinctName);
    }

    /**
     * Create a new EJB session.
     *
     * @param ejbClientContextIdentifier The EJB client context identifier. Can be null in which case the session will
     *                                   be created using the {@link org.jboss.ejb.client.EJBClientContext#requireCurrent() current active EJB client context}
     * @param viewType                   the view type
     * @param appName                    the application name
     * @param moduleName                 the module name
     * @param beanName                   the EJB name
     * @param distinctName               the module distinct name
     * @return the new session ID
     * @throws Exception if an error occurs
     */
    // TODO: narrow exception type(s)
    public static <T> StatefulEJBLocator<T> createSession(final EJBClientContextIdentifier ejbClientContextIdentifier, final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) throws Exception {
        final EJBClientContext clientContext;
        if (ejbClientContextIdentifier != null) {
            // find the appropriate EJB client context
            clientContext = EJBClientContext.require(ejbClientContextIdentifier);
        } else {
            // use the "current" EJB client context
            clientContext = EJBClientContext.requireCurrent();
        }
        final EJBReceiver ejbReceiver = clientContext.requireEJBReceiver(appName, moduleName, distinctName);
        final EJBReceiverContext receiverContext = clientContext.requireEJBReceiverContext(ejbReceiver);
        return ejbReceiver.openSession(receiverContext, viewType, appName, moduleName, distinctName, beanName);
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
     * Get the {@link EJBClientContextIdentifier} associated with the passed EJB proxy. If no {@link EJBClientContextIdentifier}
     * is associated with the proxy then this method returns null.
     *
     * @param proxy The EJB proxy
     * @return
     * @throws IllegalArgumentException If the passed proxy is not a valid EJB proxy
     */
    public static EJBClientContextIdentifier getEJBClientContextIdentifierFor(Object proxy) throws IllegalArgumentException {
        return EJBInvocationHandler.forProxy(proxy).getEjbClientContextIdentifier();
    }

    /**
     * Get a {@code UserTransaction} object instance which can be used to control transactions on a specific node.
     *
     * @param targetNodeName the node name
     * @return the {@code UserTransaction} instance
     * @throws IllegalStateException if the transaction context isn't set or cannot provide a {@code UserTransaction} instance
     */
    public static UserTransaction getUserTransaction(String targetNodeName) {
        return EJBClientTransactionContext.requireCurrent().getUserTransaction(targetNodeName);
    }
}
