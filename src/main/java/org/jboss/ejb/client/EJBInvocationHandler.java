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
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings({"SerializableClassWithUnconstructableAncestor"})
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
        if (method.getName().equals("toString") && method.getParameterTypes().length == 0) {
            return handleToString();
        } else if (method.getName().equals("equals") && method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == Object.class) {
            return handleEquals(args[0]);
        } else if (method.getName().equals("hashCode") && method.getParameterTypes().length == 0) {
            return handleHashCode();
        }

        final EJBClientContext context = EJBClientContext.requireCurrent();
        final EJBReceiver<?> receiver = context.requireEJBReceiver(appName, moduleName, distinctName);
        return doInvoke(proxy, method, args, receiver, context);
    }


    private <A> Object doInvoke(final Object proxy, final Method method, final Object[] args, final EJBReceiver<A> receiver, EJBClientContext clientContext) throws Throwable {
        // todo - concatenate receiver chain too
        final EJBReceiverContext ejbReceiverContext = clientContext.requireEJBReceiverContext(receiver);
        final EJBClientInvocationContext<A> invocationContext = new EJBClientInvocationContext<A>(this, clientContext, receiver.createReceiverSpecific(), receiver, ejbReceiverContext, proxy, method, args, EJBClientContext.GENERAL_INTERCEPTORS);

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
        final StringBuilder builder = new StringBuilder("Proxy For Remote EJB ");
        builder.append(appName);
        builder.append('/');
        builder.append(moduleName);
        builder.append('/');
        builder.append(distinctName);
        builder.append('/');
        builder.append(beanName);
        final SessionID sessionID = getAttachment(SessionID.SESSION_ID_KEY);
        if (sessionID != null) {
            builder.append(" SessionId(");
            builder.append(sessionID.toString());
            builder.append(')');
        }
        return builder.toString();
    }

    private Integer handleHashCode() {
        final SessionID sessionID = getAttachment(SessionID.SESSION_ID_KEY);
        if (sessionID != null) {
            return sessionID.hashCode();
        } else {
            int result = appName != null ? appName.hashCode() : 0;
            result = 31 * result + moduleName.hashCode();
            result = 31 * result + distinctName.hashCode();
            result = 31 * result + beanName.hashCode();
            result = 31 * result + viewClass.hashCode();
            return result;
        }
    }

    private Boolean handleEquals(final Object other) {
        if (other instanceof Proxy) {
            final InvocationHandler handler = Proxy.getInvocationHandler(other);
            if (handler instanceof EJBInvocationHandler) {
                final EJBInvocationHandler otherHandler = (EJBInvocationHandler) handler;
                final SessionID thisSession = getAttachment(SessionID.SESSION_ID_KEY);
                final SessionID otherSession = otherHandler.getAttachment(SessionID.SESSION_ID_KEY);
                //if there is a session if that is the primary means of determining equality
                if (thisSession != null) {
                    return otherSession != null && thisSession.equals(otherSession);
                } else if (otherSession != null) {
                    return false;
                }

                if (!appName.equals(otherHandler.appName))
                    return false;
                if (!beanName.equals(otherHandler.beanName))
                    return false;
                if (!distinctName.equals(otherHandler.distinctName))
                    return false;
                if (!moduleName.equals(otherHandler.moduleName))
                    return false;
                if (!viewClass.equals(otherHandler.viewClass))
                    return false;
                return true;
            }
        }
        return false;
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
