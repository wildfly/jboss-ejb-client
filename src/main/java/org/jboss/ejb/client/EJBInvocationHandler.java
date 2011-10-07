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

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Future;

/**
 * @param <T> the proxy view type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings({"SerializableClassWithUnconstructableAncestor"})
final class EJBInvocationHandler<T> extends Attachable implements InvocationHandler, Serializable {

    private static final long serialVersionUID = 946555285095057230L;

    private final transient boolean async;
    private final Locator<T> locator;

    EJBInvocationHandler(final Locator<T> locator) {
        if (locator == null) {
            throw new NullPointerException("locator is null");
        }
        this.locator = locator;
        async = false;
    }

    EJBInvocationHandler(final EJBInvocationHandler<T> other) {
        super(other);
        locator = other.locator;
        async = true;
    }

    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        return doInvoke(locator.getInterfaceType().cast(proxy), method, args);
    }

    public Object doInvoke(final T proxy, final Method method, final Object[] args) throws Throwable {
        if (method.getName().equals("toString") && method.getParameterTypes().length == 0) {
            return handleToString();
        } else if (method.getName().equals("equals") && method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == Object.class) {
            return handleEquals(args[0]);
        } else if (method.getName().equals("hashCode") && method.getParameterTypes().length == 0) {
            return handleHashCode();
        }

        final EJBClientContext context = EJBClientContext.requireCurrent();
        final EJBReceiver<?> receiver = context.requireEJBReceiver(locator.getAppName(), locator.getModuleName(), locator.getDistinctName());
        return doInvoke(this, async, proxy, method, args, receiver, context);
    }

    @SuppressWarnings("unchecked")
    static <T> EJBInvocationHandler<? extends T> forProxy(T proxy) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (handler instanceof EJBInvocationHandler) {
            return (EJBInvocationHandler<? extends T>) handler;
        }
        throw Logs.MAIN.proxyNotOurs(proxy, EJBClient.class.getName());
    }

    private static <T, A> Object doInvoke(final EJBInvocationHandler<T> ejbInvocationHandler, final boolean async, final T proxy, final Method method, final Object[] args, final EJBReceiver<A> receiver, EJBClientContext clientContext) throws Throwable {
        // todo - concatenate receiver chain too
        final EJBReceiverContext ejbReceiverContext = clientContext.requireEJBReceiverContext(receiver);
        final EJBClientInvocationContext<A> invocationContext = new EJBClientInvocationContext<A>(ejbInvocationHandler, clientContext, receiver.createReceiverSpecific(), receiver, ejbReceiverContext, proxy, method, args, EJBClientContext.GENERAL_INTERCEPTORS);

        invocationContext.sendRequest();

        if (!async) {
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
    }

    private String handleToString() {
        return String.format("Proxy for remote EJB %s", locator);
    }

    private Integer handleHashCode() {
        return Integer.valueOf(locator.hashCode());
    }

    private Boolean handleEquals(final Object other) {
        if (other instanceof Proxy) {
            final InvocationHandler handler = Proxy.getInvocationHandler(other);
            if (handler instanceof EJBInvocationHandler) {
                final EJBInvocationHandler<?> otherHandler = (EJBInvocationHandler<?>) handler;
                return locator.equals(otherHandler.locator);
            }
        }
        return false;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if (async) throw new NotSerializableException("Async proxies are not serializable");
        oos.defaultWriteObject();
    }

    EJBInvocationHandler<T> getAsyncHandler() {
        return async ? this : new EJBInvocationHandler<T>(this);
    }

    Locator<T> getLocator() {
        return locator;
    }
}
