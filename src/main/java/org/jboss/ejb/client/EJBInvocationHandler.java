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
import java.util.concurrent.Future;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings({ "SerializableClassWithUnconstructableAncestor" })
final class EJBInvocationHandler extends Attachable implements InvocationHandler, Serializable {

    private static final long serialVersionUID = 946555285095057230L;

    private final String appName;
    private final String moduleName;
    private final String distinctName;
    private final String beanName;
    private final Class<?> viewClass;
    private final transient boolean async;

    EJBInvocationHandler(final Class<?> viewClass, final String appName, final String moduleName, final String distinctName, final String beanName) {
        this.viewClass = viewClass;
        this.appName = appName;
        this.moduleName = moduleName;
        this.distinctName = distinctName;
        this.beanName = beanName;
        async = false;
    }

    private EJBInvocationHandler(final EJBInvocationHandler twin) {
        super(twin);
        viewClass = twin.viewClass;
        appName = twin.appName;
        moduleName = twin.moduleName;
        distinctName = twin.distinctName;
        beanName = twin.beanName;
        async = true;
    }

    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        final EJBClientContext context = EJBClientContext.requireCurrent();
        final EJBReceiver<?> receiver = context.requireEJBReceiver(appName, moduleName, distinctName);
        return doInvoke(proxy, method, args, receiver, context);
    }

    private <A> Object doInvoke(final Object proxy, final Method method, final Object[] args, final EJBReceiver<A> receiver, EJBClientContext clientContext) throws Throwable {
        // todo - concatenate receiver chain too
        final EJBClientInvocationContext<A> invocationContext = new EJBClientInvocationContext<A>(this, clientContext, receiver.createReceiverSpecific(), receiver, proxy, method, args, EJBClientContext.GENERAL_INTERCEPTORS);
        if (async) {
            // force async...
            if (method.getReturnType() == Future.class) {
                invocationContext.sendRequest();
                return invocationContext.getFutureResponse();
            } else if (method.getReturnType() == void.class) {
                // todo indicate that the result shall be discarded
                invocationContext.sendRequest();
                // Void return
                return null;
            } else {
                invocationContext.sendRequest();
                // wrap return always
                EJBClient.setFutureResult(invocationContext.getFutureResponse());
                return null;
            }
        } else {
            // wait for invocation to complete
            invocationContext.sendRequest();
            return invocationContext.awaitResponse();
        }
    }

    public String getAppName() {
        return appName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getDistinctName() {
        return distinctName;
    }

    public String getBeanName() {
        return beanName;
    }

    public Class<?> getViewClass() {
        return viewClass;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if (async) throw new NotSerializableException("Async proxies are not serializable");
        oos.defaultWriteObject();
    }

    EJBInvocationHandler getAsyncHandler() {
        return async ? this : new EJBInvocationHandler(this);
    }
}
