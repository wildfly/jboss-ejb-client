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
final class EJBInvocationHandler extends Attachable implements InvocationHandler, Serializable {

    private static final long serialVersionUID = 946555285095057230L;

    private final String beanName;
    private final ModuleID moduleID;
    private final Class<?> viewClass;
    private final transient boolean async;

    EJBInvocationHandler(final Class<?> viewClass, final String appName, final String moduleName, final String distinctName, final String beanName) {
        this.viewClass = viewClass;
        this.moduleID = new ModuleID(appName, moduleName, distinctName);
        this.beanName = beanName;
        async = false;
    }

    private EJBInvocationHandler(final EJBInvocationHandler twin) {
        super(twin);
        viewClass = twin.viewClass;
        this.moduleID = twin.moduleID;
        beanName = twin.beanName;
        async = true;
    }

    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        final EJBClientContext context = EJBClientContext.requireCurrent();
        final EJBReceiver<?> receiver = context.requireEJBReceiver(moduleID.getAppName(), moduleID.getModuleName(), moduleID.getDistinctName());
        return doInvoke(proxy, method, args, receiver, context);
    }

    private <A> Object doInvoke(final Object proxy, final Method method, final Object[] args, final EJBReceiver<A> receiver, EJBClientContext clientContext) throws Throwable {
        final EJBClientInvocationContext<A> context = new EJBClientInvocationContext<A>(this, clientContext, receiver.createReceiverSpecific(), receiver, proxy, method, args);
        for (EJBClientInterceptor<Object> interceptor : EJBClientContext.GENERAL_INTERCEPTORS) {
            interceptor.handleInvocation(context);
        }
        if (async) {
            // force async...
            if (method.getReturnType() == Future.class) {
                // use the existing future, assuming that the result is properly set
                return receiver.processInvocation(context);
            } else if (method.getReturnType() == void.class) {
                // no return type necessary
                receiver.processInvocation(context);
                return null;
            } else {
                // wrap return always
                EJBClient.setFutureResult(receiver.processInvocation(context));
                return null;
            }
        } else {
            // wait for invocation to complete
            return receiver.processInvocation(context).get();
        }
    }

    public String getAppName() {
        return this.moduleID.getAppName();
    }

    public String getModuleName() {
        return this.moduleID.getModuleName();
    }

    public String getDistinctName() {
        return this.moduleID.getDistinctName();
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
