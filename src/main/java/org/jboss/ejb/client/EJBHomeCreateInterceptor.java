/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.lang.reflect.Method;

/**
 * A {@link EJBClientInterceptor} which is responsible for intercepting the returning value of "create" methods
 * on a {@link javax.ejb.EJBHome} proxy. This interceptor just lets the invocation proceed in its {@link #handleInvocation(EJBClientInvocationContext)}
 * method. However, in its {@link #handleInvocationResult(EJBClientInvocationContext)} method it does the following:
 * <ol>
 * <li>Check to see if the invocation is happening on a EJBHome proxy. It does this by checking the {@link EJBLocator}
 * type of the {@link EJBClientInvocationContext invocation context}. If it finds that the invocation is not
 * on a EJBHome proxy, then the {@link #handleInvocationResult(EJBClientInvocationContext)} just returns back the
 * original result.
 * </li>
 * <li>
 * Check to see if the invoked method is a "create" method. The EJB spec states that each EJBHome interface
 * can have any number of "create" methods, but the method name should begin with "create". This is what the
 * interceptor checks for. If the invocation is not for a "create" method, then the {@link #handleInvocationResult(EJBClientInvocationContext)}
 * just returns back the original result.
 * </li>
 * <li>
 * Check to see if the original returned instance is a {@link EJBClient#isEJBProxy(Object) EJB proxy}. If it isn't
 * a EJB proxy then the {@link #handleInvocationResult(EJBClientInvocationContext)} method just returns back the
 * original result. If it finds that it's an EJB proxy, then the {@link #handleInvocationResult(EJBClientInvocationContext)}
 * recreates the proxy by using the {@link org.jboss.ejb.client.EJBInvocationHandler#getEjbClientContextIdentifier() EJB client context identifier}
 * that's applicable to the EJBHome proxy on which this invocation was done. This way, the EJB proxies returned
 * by calls to "create" methods on the EJBHome proxy will always be associated with the EJB client context identifier
 * that's applicable to the EJBHome proxy
 * </li>
 * </ol>
 * An example of where this interceptor plays a role is as follows:
 * <code>
 * final Properties jndiProps = new Properties();
 * // create a scoped EJB client context
 * jndiProps.put("org.jboss.ejb.client.scoped.context",true);
 * // other jndi props
 * ...
 * final Context ctx = new InitialContext(jndiProps);
 * final SomeEJBRemoteHome remoteHome = (SomeEJBRemoteHome) ctx.lookup("ejb:/foo/bar/dist/bean!remotehomeinterface");
 * // now create the EJB remote object.
 * // this returned "SomeEJBRemote" proxy MUST have the same EJB client context identifier that was
 * // applicable for the "remoteHome" proxy that we created a few lines above. That way any subsequent
 * // invocation on this "remoteBean" will always use the correct EJB client context
 * final SomeEJBRemote remoteBean = remoteHome.create();
 * // now invoke on the bean
 * remoteBean.doSomething();
 * <p/>
 * </code>
 *
 * @author Jaikiran Pai
 */
final class EJBHomeCreateInterceptor implements EJBClientInterceptor {

    @Override
    public void handleInvocation(final EJBClientInvocationContext invocationContext) throws Exception {
        // we just pass along the request
        invocationContext.sendRequest();
    }

    @Override
    public Object handleInvocationResult(final EJBClientInvocationContext invocationContext) throws Exception {
        final Object originalResult = invocationContext.getResult();
        if (originalResult == null) {
            return originalResult;
        }
        // if it's not an invocation on a EJB home view, then just return back the original result
        if (!isEJBHomeInvocation(invocationContext)) {
            return originalResult;
        }
        // if it's not a "createXXX" method invocation, then just return back the original result
        if (!isCreateMethodInvocation(invocationContext)) {
            return originalResult;
        }
        // if the original result isn't a EJB proxy, then we just return back the original result
        if (!EJBClient.isEJBProxy(originalResult)) {
            return originalResult;
        }
        // at this point we have identified the invocation to be a "createXXX" method invocation on a EJB
        // home view and the return object being a EJB proxy. So we now update that proxy to use the EJB client
        // context identifier that's applicable for the EJB home proxy on which this invocation was done
        final Object ejbProxy = originalResult;
        // we *don't* change the locator of the original proxy
        final EJBLocator ejbLocator = EJBClient.getLocatorFor(ejbProxy);
        // get the EJB client context identifier (if any) that's applicable for the home view on which this
        // invocation happened
        final EJBClientContextIdentifier ejbClientContextIdentifier = invocationContext.getInvocationHandler().getEjbClientContextIdentifier();
        // now recreate the proxy with the EJB client context identifier
        return EJBClient.createProxy(ejbLocator, ejbClientContextIdentifier);
    }

    /**
     * Returns true if the invocation happened on a EJB home view. Else returns false.
     *
     * @param invocationContext
     * @return
     */
    private boolean isEJBHomeInvocation(final EJBClientInvocationContext invocationContext) {
        final EJBLocator locator = invocationContext.getLocator();
        return locator instanceof EJBHomeLocator;
    }

    /**
     * Returns true if the invocation is for a "create<...>" method. Else returns false.
     *
     * @param invocationContext
     * @return
     */
    private boolean isCreateMethodInvocation(final EJBClientInvocationContext invocationContext) {
        final Method invokedMethod = invocationContext.getInvokedMethod();
        return invokedMethod.getName().startsWith("create");
    }
}
