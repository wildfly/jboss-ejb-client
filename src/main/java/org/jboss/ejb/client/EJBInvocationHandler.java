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

import javax.ejb.EJBException;
import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

/**
 * @param <T> the proxy view type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings({"SerializableClassWithUnconstructableAncestor"})
final class EJBInvocationHandler<T> extends Attachable implements InvocationHandler, Serializable {

    private static final long serialVersionUID = 946555285095057230L;

    private final transient boolean async;

    private transient String toString;
    private transient String toStringProxy;

    /**
     * @serial the associated EJB locator
     */
    private final EJBLocator<T> locator;
    /**
     * @serial the weak affinity
     */
    private volatile Affinity weakAffinity = Affinity.NONE;

    /**
     * Construct a new instance.
     *
     * @param locator the EJB locator (not {@code null})
     */
    EJBInvocationHandler(final EJBLocator<T> locator) {
        if (locator == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB locator");
        }
        this.locator = locator;
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
        locator = other.locator;
        async = true;
        if (locator instanceof StatefulEJBLocator) {
            // set the weak affinity to the node on which the session was created
            setWeakAffinity(locator.getAffinity());
        }
    }

    public Object invoke(final Object rawProxy, final Method method, final Object[] args) throws Exception {
        final T proxy = locator.getViewType().cast(rawProxy);
        final EJBProxyInformation.ProxyMethodInfo methodInfo = locator.getProxyInformation().getProxyMethodInfo(method);
        switch (methodInfo.getMethodType()) {
            case EJBProxyInformation.MT_EQUALS:
            case EJBProxyInformation.MT_IS_IDENTICAL: {
                assert args.length == 1; // checked by EJBProxyInformation
                if (args[0] instanceof Proxy) {
                    final InvocationHandler handler = Proxy.getInvocationHandler(args[0]);
                    if (handler instanceof EJBInvocationHandler) {
                        return Boolean.valueOf(locator.equals(((EJBInvocationHandler<?>) handler).getLocator()));
                    }
                }
                return Boolean.FALSE;
            }
            case EJBProxyInformation.MT_HASH_CODE: {
                // TODO: cache instance?
                return Integer.valueOf(locator.hashCode());
            }
            case EJBProxyInformation.MT_TO_STRING: {
                final String s = toStringProxy;
                return s != null ? s : (toStringProxy = String.format("Proxy for remote EJB %s", locator));
            }
            case EJBProxyInformation.MT_GET_PRIMARY_KEY: {
                if (locator instanceof EntityEJBLocator) {
                    return ((EntityEJBLocator) locator).getPrimaryKey();
                }
                throw new RemoteException("Cannot invoke getPrimaryKey() on " + proxy);
            }
            case EJBProxyInformation.MT_GET_HANDLE: {
                // TODO: cache instance
                return EJBHandle.handleFor(locator.narrowTo(EJBObject.class));
            }
            case EJBProxyInformation.MT_GET_HOME_HANDLE: {
                if (locator instanceof EJBHomeLocator) {
                    // TODO: cache instance
                    return EJBHomeHandle.handleFor(locator.narrowAsHome(EJBHome.class));
                }
                throw new RemoteException("Cannot invoke getHomeHandle() on " + proxy);
            }
        }
        // otherwise it's a business method
        assert methodInfo.getMethodType() == EJBProxyInformation.MT_BUSINESS;
        final EJBClientContext clientContext = EJBClientContext.getCurrent();
        final EJBReceiver receiver = clientContext.getEJBReceiver(locator);
        if (receiver == null) {
            throw new RemoteException("Cannot locate destination for EJB identified by " + locator);
        }
        final EJBClientInvocationContext invocationContext = new EJBClientInvocationContext(this, clientContext, proxy, args, receiver, methodInfo);

        try {
            // send the request
            invocationContext.sendRequest();

            if (!async && !methodInfo.isClientAsync()) {
                // wait for invocation to complete
                final Object value = invocationContext.awaitResponse();
                if (value != EJBClientInvocationContext.PROCEED_ASYNC) {
                    return value;
                }
                // proceed asynchronously
            }
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
        return new SerializedEJBInvocationHandler(locator);
    }

    EJBInvocationHandler<T> getAsyncHandler() {
        return async ? this : new EJBInvocationHandler<T>(this);
    }

    boolean isAsyncHandler() {
        return this.async;
    }

    EJBLocator<T> getLocator() {
        return locator;
    }

    public String toString() {
        final String s = toString;
        return s != null ? s : (toString = String.format("Proxy invocation handler for %s", locator));
    }
}
