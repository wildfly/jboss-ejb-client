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

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.ejb.EJBException;
import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

import org.jboss.ejb._private.Logs;
import org.wildfly.common.Assert;

/**
 * @param <T> the proxy view type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class EJBInvocationHandler<T> extends Attachable implements InvocationHandler, Serializable {

    private static final long serialVersionUID = 946555285095057230L;

    private final transient boolean async;

    private transient String toString;
    private transient String toStringProxy;

    private final AtomicReference<EJBLocator<T>> locatorRef;

    private volatile Affinity weakAffinity = Affinity.NONE;

    // -1 = use global value
    private volatile long invocationTimeout = -1L;

    /**
     * Construct a new instance.
     *
     * @param locator the EJB locator (not {@code null})
     */
    EJBInvocationHandler(final EJBLocator<T> locator) {
        Assert.checkNotNullParam("locator", locator);
        this.locatorRef = new AtomicReference<>(locator);
        async = false;
        if (locator instanceof StatefulEJBLocator) {
            // set the weak affinity to the node on which the session was created
            setWeakAffinity(locator.getAffinity());
        }
    }

    /**
     * Construct a new asynchronous instance.
     *
     * @param other the synchronous invocation handler
     */
    EJBInvocationHandler(final EJBInvocationHandler<T> other) {
        super(other);
        final EJBLocator<T> locator = other.locatorRef.get();
        locatorRef = new AtomicReference<>(locator);
        async = true;
        if (locator instanceof StatefulEJBLocator) {
            // set the weak affinity to the node on which the session was created
            setWeakAffinity(locator.getAffinity());
        }
    }

    public Object invoke(final Object rawProxy, final Method method, final Object... args) throws Exception {
        final T proxy = locatorRef.get().getViewType().cast(rawProxy);
        final EJBProxyInformation.ProxyMethodInfo methodInfo = locatorRef.get().getProxyInformation().getProxyMethodInfo(method);
        return invoke(proxy, methodInfo, args);
    }

    EJBProxyInformation.ProxyMethodInfo getProxyMethodInfo(EJBMethodLocator methodLocator) {
        return locatorRef.get().getProxyInformation().getProxyMethodInfo(methodLocator);
    }

    Object invoke(final Object proxy, final EJBProxyInformation.ProxyMethodInfo methodInfo, final Object... args) throws Exception {
        final Method method = methodInfo.getMethod();
        switch (methodInfo.getMethodType()) {
            case EJBProxyInformation.MT_EQUALS:
            case EJBProxyInformation.MT_IS_IDENTICAL: {
                assert args.length == 1; // checked by EJBProxyInformation
                if (args[0] instanceof Proxy) {
                    final InvocationHandler handler = Proxy.getInvocationHandler(args[0]);
                    if (handler instanceof EJBInvocationHandler) {
                        return Boolean.valueOf(equals(handler));
                    }
                }
                return Boolean.FALSE;
            }
            case EJBProxyInformation.MT_HASH_CODE: {
                // TODO: cache instance?
                return Integer.valueOf(locatorRef.get().hashCode());
            }
            case EJBProxyInformation.MT_TO_STRING: {
                final String s = toStringProxy;
                return s != null ? s : (toStringProxy = String.format("Proxy for remote EJB %s", locatorRef.get()));
            }
            case EJBProxyInformation.MT_GET_PRIMARY_KEY: {
                if (locatorRef.get().isEntity()) {
                    return locatorRef.get().narrowAsEntity(EJBObject.class).getPrimaryKey();
                }
                throw new RemoteException("Cannot invoke getPrimaryKey() on " + proxy);
            }
            case EJBProxyInformation.MT_GET_HANDLE: {
                // TODO: cache instance
                return EJBHandle.handleFor(locatorRef.get().narrowTo(EJBObject.class));
            }
            case EJBProxyInformation.MT_GET_HOME_HANDLE: {
                if (locatorRef.get() instanceof EJBHomeLocator) {
                    // TODO: cache instance
                    return EJBHomeHandle.handleFor(locatorRef.get().narrowAsHome(EJBHome.class));
                }
                throw new RemoteException("Cannot invoke getHomeHandle() on " + proxy);
            }
        }
        // otherwise it's a business method
        assert methodInfo.getMethodType() == EJBProxyInformation.MT_BUSINESS;
        final EJBClientContext clientContext = EJBClientContext.getCurrent();
        return clientContext.performLocatedAction(locatorRef.get(), (receiver, originalLocator, newAffinity) -> {
            final EJBClientInvocationContext invocationContext = new EJBClientInvocationContext(this, clientContext, proxy, args, methodInfo);
            invocationContext.setReceiver(receiver);
            invocationContext.setLocator(locatorRef.get().withNewAffinity(newAffinity));
            invocationContext.setBlockingCaller(true);

            try {
                // send the request
                invocationContext.sendRequest();

                if (! async && ! methodInfo.isClientAsync()) {
                    // wait for invocation to complete
                    final Object value = invocationContext.awaitResponse(this);
                    if (value != EJBClientInvocationContext.PROCEED_ASYNC) {
                        return value;
                    }
                    // proceed asynchronously
                }
                invocationContext.setBlockingCaller(false);
                // force async...
                if (method.getReturnType() == Future.class) {
                    return invocationContext.getFutureResponse();
                } else if (method.getReturnType() == void.class) {
                    invocationContext.setDiscardResult();
                    // Void return
                    return null;
                } else {
                    // wrap return always
                    EJBClient.setFutureResult(invocationContext.getFutureResponse());
                    return null;
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                boolean remoteException = false;
                for (Class<?> exception : method.getExceptionTypes()) {
                    if (exception.isAssignableFrom(e.getClass())) {
                        throw e;
                    } else if (RemoteException.class.equals(exception)) {
                        remoteException = true;
                    }
                }
                if (remoteException) {
                    throw new RemoteException("Error", e);
                }
                throw new EJBException(e);
            }
        });
    }

    void setWeakAffinity(Affinity newWeakAffinity) {
        weakAffinity = newWeakAffinity;
    }

    Affinity getWeakAffinity() {
        return weakAffinity;
    }

    @SuppressWarnings("unchecked")
    static <T> EJBInvocationHandler<? extends T> forProxy(T proxy) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (handler instanceof EJBInvocationHandler) {
            return (EJBInvocationHandler<? extends T>) handler;
        }
        throw Logs.MAIN.proxyNotOurs(proxy, EJBClient.class.getName());
    }

    @SuppressWarnings("unused")
    protected Object writeReplace() {
        return new SerializedEJBInvocationHandler(locatorRef.get(), async);
    }

    EJBInvocationHandler<T> getAsyncHandler() {
        return async ? this : new EJBInvocationHandler<T>(this);
    }

    boolean isAsyncHandler() {
        return this.async;
    }

    EJBLocator<T> getLocator() {
        return locatorRef.get();
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof EJBInvocationHandler && equals((EJBInvocationHandler<?>)other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(InvocationHandler other) {
        return other instanceof EJBInvocationHandler && equals((EJBInvocationHandler<?>)other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(EJBInvocationHandler<?> other) {
        return this == other || other != null && locatorRef.get().equals(other.locatorRef.get()) && async == other.async;
    }

    /**
     * Get the hash code of this handler.
     *
     * @return the hash code of this handler
     */
    public int hashCode() {
        int hc = locatorRef.get().hashCode();
        if (async) hc ++;
        return hc;
    }

    public String toString() {
        final String s = toString;
        return s != null ? s : (toString = String.format("Proxy invocation handler for %s", locatorRef.get()));
    }

    @SuppressWarnings("unchecked")
    <R> EJBInvocationHandler<R> forClass(final Class<R> viewType) {
        if (viewType.isAssignableFrom(viewType)) {
            return (EJBInvocationHandler<R>) this;
        } else {
            throw new ClassCastException(viewType.getName());
        }
    }

    void setStrongAffinity(final Affinity newAffinity) {
        final AtomicReference<EJBLocator<T>> locatorRef = this.locatorRef;
        EJBLocator<T> oldVal, newVal;
        do {
            oldVal = locatorRef.get();
            if (oldVal.getAffinity().equals(newAffinity)) {
                return;
            }
            newVal = oldVal.withNewAffinity(newAffinity);
        } while (! locatorRef.compareAndSet(oldVal, newVal));
    }

    void setSessionID(final SessionID sessionID) {
        final AtomicReference<EJBLocator<T>> locatorRef = this.locatorRef;
        EJBLocator<T> oldVal, newVal;
        do {
            oldVal = locatorRef.get();
            if (oldVal.isStateful()) {
                if (oldVal.asStateful().getSessionId().equals(sessionID)) {
                    // harmless/idempotent
                    return;
                }
                throw Logs.MAIN.ejbIsAlreadyStateful();
            }
            newVal = new StatefulEJBLocator<T>(oldVal, sessionID);
        } while (! locatorRef.compareAndSet(oldVal, newVal));
    }

    long getInvocationTimeout() {
        return invocationTimeout;
    }

    void setInvocationTimeout(final long invocationTimeout) {
        this.invocationTimeout = invocationTimeout;
    }

    boolean compareAndSetStrongAffinity(final Affinity expectedAffinity, final Affinity newAffinity) {
        Assert.checkNotNullParam("expectedAffinity", expectedAffinity);
        Assert.checkNotNullParam("newAffinity", newAffinity);
        final AtomicReference<EJBLocator<T>> locatorRef = this.locatorRef;
        EJBLocator<T> oldVal = locatorRef.get();
        if (! oldVal.getAffinity().equals(expectedAffinity)) {
            return false;
        }
        EJBLocator<T> newVal = oldVal.withNewAffinity(newAffinity);
        return locatorRef.compareAndSet(oldVal, newVal);
    }
}
