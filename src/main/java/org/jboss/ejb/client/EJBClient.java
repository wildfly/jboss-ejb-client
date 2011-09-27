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
import java.util.concurrent.Future;

/**
 * The main EJB client API class.  This class contains helper methods which may be used to create proxies, open sessions,
 * and associate the current invocation context.
 *
 * @author jpai
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClient {

    private EJBClient() {
    }

    /**
     * Open a new session for an EJB.
     *
     * @param proxy the stateful session bean remote/business interface proxy
     */
    public static void createSession(final Object proxy) {
        final EJBInvocationHandler handler = (EJBInvocationHandler) Proxy.getInvocationHandler(proxy);
        final EJBReceiver<?> ejbReceiver = EJBClientContext.requireCurrent().getEJBReceiver(handler.getAppName(), handler.getModuleName(), handler.getDistinctName());
        try {
            final byte[] sessionId = ejbReceiver.openSession(handler.getAppName(), handler.getModuleName(), handler.getDistinctName(), handler.getBeanName());
            handler.putAttachment(RemotingSessionInterceptor.SESSION_KEY, sessionId);
        } catch (Exception e) {
            // xxx
        }
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
     * @param <T> the proxy type
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
            throw new IllegalArgumentException("Not a valid remote EJB proxy");
        }
    }

    /**
     * Get the future result of an operation.  Should be called in conjunction with {@link #asynchronous(Object)}.
     *
     * @param operation the operation
     * @param <T> the result type
     * @return the future result
     * @throws IllegalStateException if the operation is not appropriately given
     */
    @SuppressWarnings("unchecked")
    public static <T> Future<T> getFutureResult(final T operation) throws IllegalStateException {
        if (operation != null) {
            // todo: maybe we should return a completed future here
            throw new IllegalStateException("Operation wasn't asynchronous");
        }
        final ThreadLocal<Future<?>> futureResult = FUTURE_RESULT;
        try {
            final Future<?> future = futureResult.get();
            if (future == null) throw new IllegalStateException("No asynchronous operation in progress");
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
            if (future == null) throw new IllegalStateException("No asynchronous operation in progress");
            return future;
        } finally {
            futureResult.remove();
        }
    }

    static void setFutureResult(final Future<?> future) {
        FUTURE_RESULT.set(future);
    }

    /**
     * Creates and returns a proxy for a EJB identified by the <code>appName</code>, <code>moduleName</code>, <code>beanName</code>
     * and the <code>beanInterfaceType</code>
     *
     *
     *
     *
     * @param appName           The application name of the deployment in which the EJB is deployed. This typically is the name of the enterprise
     *                          archive (without the .ear suffix) in which the EJB is deployed on the server. The application name of the deployment
     *                          is sometimes overridden through the use of application.xml. In such cases, the passed <code>appName</code> should match
     *                          that name.
     *                          <p/>
     *                          The <code>appName</code> passed can be null if the EJB is <i>not</i> deployed an enterprise archive (.ear)
     * @param moduleName        The module name of the deployment in which the EJB is deployed. This typically is the name of the jar file (without the
     *                          .jar suffix) or the name of the war file (without the .war suffix). The module name is allowed to be overridden through
     *                          the use of deployment descriptors, in which case the passed <code>moduleName</code> should match that name.
     *                          <p/>
     *                          <code>moduleName</code> cannot be null or an empty value
     * @param extraName         The extra name of the deployment, used to disambiguate one deployment from another
     * @param viewType          The interface type exposed by the bean, for which we are creating the proxy. For example, if the bean exposes
     *                          remote business interface view, then the client can pass that as the <code>beanInterfaceType</code>. Same applies
     *                          for remote home interface.
     *                          <p/>
     *                          The <code>viewType</code> cannot be null
     * @param beanName          The name of the EJB for which the proxy is being created  @return
     * @throws IllegalArgumentException ff the moduleName is {@code null} or empty, if the beanName is {@code null} or empty, or if {@code viewType} is {@code null}
     * @return the new proxy
     */
    public static <T> T getProxy(final String appName, final String moduleName, final String extraName, final Class<T> viewType, final String beanName) throws IllegalArgumentException {
        if (moduleName == null || moduleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name cannot be null or empty");
        }
        if (beanName == null || beanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bean name cannot be null or empty");
        }
        if (viewType == null) {
            throw new IllegalArgumentException("Bean interface type cannot be null");
        }
        return viewType.cast(Proxy.newProxyInstance(viewType.getClassLoader(), new Class<?>[]{viewType}, getInvocationHandler(viewType, appName, moduleName, extraName, beanName)));
    }

    static InvocationHandler getInvocationHandler(final Class<?> viewClass, final String appName, final String moduleName, final String extraName, final String beanName) {
        return new EJBInvocationHandler(viewClass, appName, moduleName, extraName, beanName);
    }
}
