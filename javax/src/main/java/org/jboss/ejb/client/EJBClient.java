/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.ejb.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import javax.ejb.CreateException;
import javax.transaction.UserTransaction;

import org.jboss.ejb._private.Logs;
import org.wildfly.common.Assert;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.transaction.client.RemoteTransactionContext;

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
     * An invocation context key which is set to the source socket address of the invocation request, if any.  The
     * value will be of type {@link SocketAddress}.
     */
    public static final String SOURCE_ADDRESS_KEY = "jboss.source-address";

    /**
     * A JNDI context key which, if defined, identifies that the proxy to be created should have affinity to the cluster.
     */
    public static final String CLUSTER_AFFINITY = "jboss.cluster-affinity";

    /**
     * A JNDI context key which, if defined, disables learning in the case of an unspecified {@link EJBClient#CLUSTER_AFFINITY}
     */
    public static final String DISABLE_AFFINITY_LEARNING = "jboss.disable-affinity-learning";

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
        return createProxy(locator, null);
    }

    static <T> T createProxy(final EJBLocator<T> locator, final Supplier<AuthenticationContext> authenticationContextSupplier) {
        Assert.checkNotNullParam("locator", locator);
        return locator.createProxyInstance(new EJBInvocationHandler<T>(locator, authenticationContextSupplier));
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
     * Create a new EJB session proxy.  The returned proxy will be cluster-aware if a cluster affinity is used in the locator.
     *
     * @param statelessLocator the stateless locator identifying the stateful EJB
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    public static <T> T createSessionProxy(final StatelessEJBLocator<T> statelessLocator) throws Exception {
        return createSessionProxy(statelessLocator, null, null);
    }

    // Special hook method for naming; let's replace this sometime soon.
    static <T> T createSessionProxy(final StatelessEJBLocator<T> statelessLocator, Supplier<AuthenticationContext> authenticationContextSupplier, NamingProvider namingProvider) throws Exception {
        final EJBClientContext clientContext = EJBClientContext.getCurrent();
        // this is the auth context to use just for the invocation
        final AuthenticationContext authenticationContext;
        if (authenticationContextSupplier != null) {
            authenticationContext = authenticationContextSupplier.get();
        } else {
            authenticationContext = AuthenticationContext.captureCurrent();
        }

        final EJBSessionCreationInvocationContext context = clientContext.createSessionCreationInvocationContext(statelessLocator, authenticationContext);
        final StatefulEJBLocator<T> statefulLocator = clientContext.createSession(context, statelessLocator, namingProvider);
        final T proxy = createProxy(statefulLocator, authenticationContextSupplier);
        final Affinity weakAffinity = context.getWeakAffinity();

        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("createSessionProxy: strong affinity = %s, weak affinity = %s", statefulLocator.getAffinity(), context.getWeakAffinity());
        }

        if (weakAffinity != null && Affinity.NONE != weakAffinity) {
            setWeakAffinity(proxy, weakAffinity);
        }

        return proxy;
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
    public static <T> StatefulEJBLocator<T> createSession(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) throws Exception {
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
    public static <T> StatefulEJBLocator<T> createSession(final Affinity affinity, final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) throws Exception {
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
    public static <T> StatefulEJBLocator<T> createSession(final URI uri, final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) throws Exception {
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
    public static <T> StatefulEJBLocator<T> createSession(final Class<T> viewType, final String appName, final String moduleName, final String beanName) throws Exception {
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
    public static <T> StatefulEJBLocator<T> createSession(final Affinity affinity, final Class<T> viewType, final String appName, final String moduleName, final String beanName) throws Exception {
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
    public static <T> StatefulEJBLocator<T> createSession(final URI uri, final Class<T> viewType, final String appName, final String moduleName, final String beanName) throws Exception {
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
    public static <T> StatefulEJBLocator<T> createSession(StatelessEJBLocator<T> statelessLocator) throws Exception {
        return createSession(statelessLocator, null);
    }

    /**
     * Create a new EJB session.
     *
     * @param statelessLocator the stateless locator identifying the stateful EJB
     * @param authenticationContext the authentication context to use for the request and the resultant proxy
     * @param <T> the view type
     * @return the new EJB locator
     * @throws CreateException if an error occurs
     */
    static <T> StatefulEJBLocator<T> createSession(StatelessEJBLocator<T> statelessLocator, AuthenticationContext authenticationContext) throws Exception {
        final EJBClientContext clientContext = EJBClientContext.getCurrent();
        return clientContext.createSession(statelessLocator, authenticationContext, null);
    }

    /**
     * Perform a one-way asynchronous invocation by method locator on a proxy.  Any return value is ignored.
     *
     * @param proxy the EJB proxy
     * @param methodLocator the method locator
     * @param args the invocation arguments
     * @param <T> the view type
     * @throws Exception if the invocation failed for some reason
     */
    public static <T> void invokeOneWay(T proxy, EJBMethodLocator methodLocator, Object... args) throws Exception {
        final EJBInvocationHandler<? extends T> invocationHandler = EJBInvocationHandler.forProxy(proxy);
        final EJBProxyInformation.ProxyMethodInfo proxyMethodInfo = invocationHandler.getProxyMethodInfo(methodLocator);
        invocationHandler.invoke(proxy, proxyMethodInfo, args);
    }

    /**
     * Perform an asynchronous invocation by method locator on a proxy, returning the future result.
     *
     * @param proxy the EJB proxy
     * @param methodLocator the method locator
     * @param args the invocation arguments
     * @param <T> the view type
     * @throws Exception if the invocation failed for some reason
     */
    public static <T> Future<?> invokeAsync(T proxy, EJBMethodLocator methodLocator, Object... args) throws Exception {
        final EJBInvocationHandler<? extends T> invocationHandler = EJBInvocationHandler.forProxy(proxy);
        final EJBProxyInformation.ProxyMethodInfo proxyMethodInfo = invocationHandler.getProxyMethodInfo(methodLocator);
        return (Future<?>) invocationHandler.invoke(proxy, proxyMethodInfo, args);
    }

    /**
     * Perform an invocation by method locator on a proxy, returning the result.
     *
     * @param proxy the EJB proxy
     * @param methodLocator the method locator
     * @param args the invocation arguments
     * @param <T> the view type
     * @throws Exception if the invocation failed for some reason
     */
    public static <T> Object invoke(T proxy, EJBMethodLocator methodLocator, Object... args) throws Exception {
        final EJBInvocationHandler<? extends T> invocationHandler = EJBInvocationHandler.forProxy(proxy);
        final EJBProxyInformation.ProxyMethodInfo proxyMethodInfo = invocationHandler.getProxyMethodInfo(methodLocator);
        return invocationHandler.invoke(proxy, proxyMethodInfo, args);
    }

    /**
     * Get the locator for a proxy, if it has one.
     *
     * @param proxy the proxy (may not be {@code null})
     * @param <T> the proxy type
     * @return the locator
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance
     */
    public static <T> EJBLocator<? extends T> getLocatorFor(T proxy) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        return EJBInvocationHandler.forProxy(proxy).getLocator();
    }

    /**
     * Set a per-proxy invocation timeout.  This overrides the globally configured timeout.
     *
     * @param proxy the proxy to change (must not be {@code null}, must be a valid EJB proxy)
     * @param timeout the amount of time (must be greater than zero)
     * @param timeUnit the time unit (must not be {@code null})
     * @throws IllegalArgumentException if the timeout is less than or equal to zero or a required parameter is
     * {@code null} or invalid
     */
    public static void setInvocationTimeout(Object proxy, long timeout, TimeUnit timeUnit) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkMinimumParameter("timeout", 1L, timeout);
        Assert.checkNotNullParam("timeUnit", timeUnit);
        EJBInvocationHandler.forProxy(proxy).setInvocationTimeout(Math.max(1L, timeUnit.toMillis(timeout)));
    }

    /**
     * Clear the per-proxy invocation timeout, causing it to use the globally configured timeout.
     *
     * @param proxy the proxy to change (must not be {@code null}, must be a valid EJB proxy)
     * @throws IllegalArgumentException if the proxy is {@code null} or is not valid
     */
    public static void clearInvocationTimeout(Object proxy) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        EJBInvocationHandler.forProxy(proxy).setInvocationTimeout(-1L);
    }

    /**
     * Change the strong affinity of a proxy.  All subsequent invocations against the proxy will use the new affinity.
     * Subsequent calls to {@link #getLocatorFor(Object)} for the given proxy will return the updated locator.
     *
     * @param proxy the proxy (may not be {@code null})
     * @param newAffinity the new affinity (may not be {@code null})
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance
     * @throws SecurityException if a security manager is present and the caller does not have the {@code changeStrongAffinity} {@link EJBClientPermission}
     */
    public static void setStrongAffinity(Object proxy, Affinity newAffinity) throws IllegalArgumentException, SecurityException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("newAffinity", newAffinity);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(EJBClientPermission.CHANGE_STRONG_AFFINITY);
        }
        EJBInvocationHandler.forProxy(proxy).setStrongAffinity(newAffinity);
    }

    /**
     * Get the strong affinity of a proxy.  This is a shortcut for {@code getLocatorFor(proxy).getAffinity()}.
     *
     * @param proxy the proxy (may not be {@code null})
     * @return the proxy strong affinity
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance
     */
    public static Affinity getStrongAffinity(Object proxy) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        return getLocatorFor(proxy).getAffinity();
    }

    /**
     * Compare and change the strong affinity of a proxy.  All subsequent invocations against the proxy will use the new affinity.
     * Subsequent calls to {@link #getLocatorFor(Object)} for the given proxy will return the updated locator.  If the
     * affinity is not equal to the expected value, {@code false} is returned and no change is made.
     *
     * @param proxy the proxy (may not be {@code null})
     * @param newAffinity the new affinity (may not be {@code null})
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance
     * @throws SecurityException if a security manager is present and the caller does not have the {@code changeStrongAffinity} {@link EJBClientPermission}
     */
    public static boolean compareAndSetStrongAffinity(Object proxy, Affinity expectedAffinity, Affinity newAffinity) throws IllegalArgumentException, SecurityException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("expectedAffinity", expectedAffinity);
        Assert.checkNotNullParam("newAffinity", newAffinity);
        final EJBInvocationHandler<?> invocationHandler = EJBInvocationHandler.forProxy(proxy);
        final Affinity existing = invocationHandler.getLocator().getAffinity();
        if (! expectedAffinity.equals(existing)) {
            return false;
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(EJBClientPermission.CHANGE_STRONG_AFFINITY);
        }
        return invocationHandler.compareAndSetStrongAffinity(expectedAffinity, newAffinity);
    }

    /**
     * Transform the strong affinity of a proxy.  All subsequent invocations against the proxy will use the new affinity.
     * Subsequent calls to {@link #getLocatorFor(Object)} for the given proxy will return the updated locator.
     *
     * @param proxy the proxy (may not be {@code null})
     * @param transformOperator the operator to apply to acquire the new affinity from the old one (may not be {@code null})
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance
     * @throws SecurityException if a security manager is present and the caller does not have the {@code changeStrongAffinity} {@link EJBClientPermission}
     */
    public static void transformStrongAffinity(Object proxy, UnaryOperator<Affinity> transformOperator) throws IllegalArgumentException, SecurityException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("transformOperator", transformOperator);
        final EJBInvocationHandler<?> invocationHandler = EJBInvocationHandler.forProxy(proxy);
        Affinity oldAffinity = invocationHandler.getLocator().getAffinity();
        Affinity newAffinity = transformOperator.apply(oldAffinity);
        Assert.assertNotNull(newAffinity);
        if (oldAffinity.equals(newAffinity)) {
            return;
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(EJBClientPermission.CHANGE_STRONG_AFFINITY);
        }
        while (! invocationHandler.compareAndSetStrongAffinity(oldAffinity, newAffinity)) {
            oldAffinity = invocationHandler.getLocator().getAffinity();
            newAffinity = transformOperator.apply(oldAffinity);
            Assert.assertNotNull(newAffinity);
            if (oldAffinity.equals(newAffinity)) {
                return;
            }
        }
    }

    /**
     * Change the weak affinity of a proxy.  All subsequent invocations against the proxy will use the new affinity.
     *
     * @param proxy the proxy (may not be {@code null})
     * @param newAffinity the new affinity (may not be {@code null})
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance
     * @throws SecurityException if a security manager is present and the caller does not have the {@code changeWeakAffinity} {@link EJBClientPermission}
     */
    public static void setWeakAffinity(Object proxy, Affinity newAffinity) throws IllegalArgumentException, SecurityException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("newAffinity", newAffinity);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(EJBClientPermission.CHANGE_WEAK_AFFINITY);
        }
        EJBInvocationHandler.forProxy(proxy).setWeakAffinity(newAffinity);
    }

    /**
     * Get the current weak affinity of a proxy.
     *
     * @param proxy the proxy (must not be {@code null})
     * @return the affinity (not {@code null})
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance
     */
    public static Affinity getWeakAffinity(Object proxy) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        return EJBInvocationHandler.forProxy(proxy).getWeakAffinity();
    }

    /**
     * Convert a non-stateful proxy to be stateful.  If the proxy was already stateful and the session ID matches, the
     * proxy is unchanged.  If the proxy was otherwise already stateful, an exception is thrown.  Subsequent calls to
     * {@link #getLocatorFor(Object)} for the given proxy will return the updated locator.
     *
     * @param proxy the proxy to convert (must not be {@code null})
     * @param sessionID the session ID to use for the stateful locator (must not be {@code null})
     * @throws IllegalArgumentException if the given proxy is not a valid client proxy instance, or the proxy is already
     * stateful with a different session ID
     */
    public static void convertToStateful(Object proxy, SessionID sessionID) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("sessionID", sessionID);
        EJBInvocationHandler.forProxy(proxy).setSessionID(sessionID);
    }

    /**
     * Get a {@code UserTransaction} object instance which can be used to control transactions on a specific node.
     *
     * @param targetNodeName the node name (ignored)
     * @return the {@code UserTransaction} instance
     * @throws IllegalStateException if the transaction context isn't set or cannot provide a {@code UserTransaction} instance
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static UserTransaction getUserTransaction(String targetNodeName) {
        return RemoteTransactionContext.getInstance().getUserTransaction();
    }

    /**
     * Get a proxy attachment.
     *
     * @param proxy the proxy (must not be {@code null})
     * @param attachmentKey the attachment key to use (must not be {@code null})
     * @param <T> the value type
     * @return the attachment value or {@code null} if the attachment is not present
     * @throws IllegalArgumentException if a required parameter is {@code null} or if the object is not a valid EJB client proxy
     */
    public static <T> T getProxyAttachment(Object proxy, AttachmentKey<T> attachmentKey) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("attachmentKey", attachmentKey);
        return EJBInvocationHandler.forProxy(proxy).getAttachment(attachmentKey);
    }

    /**
     * Set a proxy attachment.
     *
     * @param proxy the proxy (must not be {@code null})
     * @param attachmentKey the attachment key to use (must not be {@code null})
     * @param newValue the new value to set (must not be {@code null})
     * @param <T> the value type
     * @return the previous attachment value or {@code null} if the attachment previously did not exist
     * @throws IllegalArgumentException if a required parameter is {@code null} or if the object is not a valid EJB client proxy
     */
    public static <T> T putProxyAttachment(Object proxy, AttachmentKey<T> attachmentKey, T newValue) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("attachmentKey", attachmentKey);
        Assert.checkNotNullParam("newValue", newValue);
        return EJBInvocationHandler.forProxy(proxy).putAttachment(attachmentKey, newValue);
    }

    /**
     * Set a proxy attachment if it is not already set.
     *
     * @param proxy the proxy (must not be {@code null})
     * @param attachmentKey the attachment key to use (must not be {@code null})
     * @param newValue the new value to set (must not be {@code null})
     * @param <T> the value type
     * @return the previous attachment value or {@code null} if the attachment previously did not exist
     * @throws IllegalArgumentException if a required parameter is {@code null} or if the object is not a valid EJB client proxy
     */
    public static <T> T putProxyAttachmentIfAbsent(Object proxy, AttachmentKey<T> attachmentKey, T newValue) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("attachmentKey", attachmentKey);
        Assert.checkNotNullParam("newValue", newValue);
        return EJBInvocationHandler.forProxy(proxy).putAttachmentIfAbsent(attachmentKey, newValue);
    }

    /**
     * Remove a proxy attachment.
     *
     * @param proxy the proxy (must not be {@code null})
     * @param attachmentKey the attachment key to use (must not be {@code null})
     * @param <T> the value type
     * @return the previous attachment value or {@code null} if the attachment previously did not exist
     * @throws IllegalArgumentException if a required parameter is {@code null} or if the object is not a valid EJB client proxy
     */
    public static <T> T removeProxyAttachment(Object proxy, AttachmentKey<T> attachmentKey) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("attachmentKey", attachmentKey);
        return EJBInvocationHandler.forProxy(proxy).removeAttachment(attachmentKey);
    }

    /**
     * Remove a proxy attachment with a particular value.
     *
     * @param proxy the proxy (must not be {@code null})
     * @param attachmentKey the attachment key to use (must not be {@code null})
     * @param oldValue the new value to set (must not be {@code null})
     * @param <T> the value type
     * @return {@code true} if the attachment was removed, or {@code false} if the value did not match or was not present
     * @throws IllegalArgumentException if a required parameter is {@code null} or if the object is not a valid EJB client proxy
     */
    public static <T> boolean removeProxyAttachment(Object proxy, AttachmentKey<T> attachmentKey, T oldValue) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("attachmentKey", attachmentKey);
        Assert.checkNotNullParam("oldValue", oldValue);
        return EJBInvocationHandler.forProxy(proxy).removeAttachment(attachmentKey, oldValue);
    }

    /**
     * Replace a proxy attachment if it is already present.
     *
     * @param proxy the proxy (must not be {@code null})
     * @param attachmentKey the attachment key to use (must not be {@code null})
     * @param newValue the new value to set (must not be {@code null})
     * @param <T> the value type
     * @return the previous attachment value or {@code null} if the attachment previously did not exist
     * @throws IllegalArgumentException if a required parameter is {@code null} or if the object is not a valid EJB client proxy
     */
    public static <T> T replaceProxyAttachment(Object proxy, AttachmentKey<T> attachmentKey, T newValue) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("attachmentKey", attachmentKey);
        Assert.checkNotNullParam("newValue", newValue);
        return EJBInvocationHandler.forProxy(proxy).replaceAttachment(attachmentKey, newValue);
    }

    /**
     * Replace a proxy attachment if it is already present.
     *
     * @param proxy the proxy (must not be {@code null})
     * @param attachmentKey the attachment key to use (must not be {@code null})
     * @param newValue the old value to replace (must not be {@code null})
     * @param <T> the value type
     * @return {@code true} if the attachment value was replaced, {@code false} otherwise
     * @throws IllegalArgumentException if a required parameter is {@code null} or if the object is not a valid EJB client proxy
     */
    public static <T> boolean replaceProxyAttachment(Object proxy, AttachmentKey<T> attachmentKey, T oldValue, T newValue) throws IllegalArgumentException {
        Assert.checkNotNullParam("proxy", proxy);
        Assert.checkNotNullParam("attachmentKey", attachmentKey);
        Assert.checkNotNullParam("oldValue", oldValue);
        Assert.checkNotNullParam("newValue", newValue);
        return EJBInvocationHandler.forProxy(proxy).replaceAttachment(attachmentKey, oldValue, newValue);
    }
}
