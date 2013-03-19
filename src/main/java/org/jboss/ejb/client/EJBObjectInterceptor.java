/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import javax.ejb.EJBObject;
import java.lang.reflect.Method;

/**
 * A {@link EJBClientInterceptor} which is responsible for intercepting the returning value of <code>getEJBHome</code> method
 * on a {@link javax.ejb.EJBObject} proxy. This interceptor just lets the invocation proceed in its {@link #handleInvocation(EJBClientInvocationContext)}
 * method. However, in its {@link #handleInvocationResult(EJBClientInvocationContext)} method it does the following:
 * <ol>
 * <li>Check to see if the invocation is happening on a EJBObject proxy. If it finds that the invocation is not
 * on a EJBObject proxy, then the {@link #handleInvocationResult(EJBClientInvocationContext)} just returns back the
 * original result.
 * </li>
 * <li>
 * Check to see if the invoked method is a "getEJBHome" method. If the invocation is not for a "getEJBHome" method, then
 * the {@link #handleInvocationResult(EJBClientInvocationContext)} just returns back the original result.
 * </li>
 * <li>
 * Check to see if the original returned instance is a {@link EJBClient#isEJBProxy(Object) EJB proxy}.
 * If it isn't, then the {@link #handleInvocationResult(EJBClientInvocationContext)} method just returns back the
 * original result. If it finds that it's an EJB proxy then the {@link #handleInvocationResult(EJBClientInvocationContext)}
 * recreates the proxy/proxies by using the {@link org.jboss.ejb.client.EJBInvocationHandler#getEjbClientContextIdentifier() EJB client context identifier}
 * that's applicable to the EJBObject proxy on which this invocation was done. This way, the EJB home proxies returned
 * by calls to "getEJBHome" methods on the EJBObject proxy will always be associated with the EJB client context identifier
 * that's applicable to the EJBObject proxy
 * </li>
 * </ol>
 *
 * @author Jaikiran Pai
 */
final class EJBObjectInterceptor implements EJBClientInterceptor {
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
        // if the original result isn't a EJB proxy, then we just return back the original result
        if (!EJBClient.isEJBProxy(originalResult)) {
            return originalResult;
        }
        // if it's not an invocation on a EJBObject view, then just return back the original result
        if (!isEJBObjectInvocation(invocationContext)) {
            return originalResult;
        }
        // if it's not a "getEJBHome()" method invocation then just return back the original result
        if (!isGetEJBHomeMethodInvocation(invocationContext)) {
            return originalResult;
        }
        // at this point we have identified the invocation to be "getEJBHome()" method invocation on the EJBObject view.
        // So we now update that returned EJB home proxy to use the EJB client context identifier that's applicable for the
        // EJBObject proxy on which this invocation was done
        final Object ejbProxy = originalResult;
        // we *don't* change the locator of the original proxy
        final EJBLocator ejbLocator = EJBClient.getLocatorFor(ejbProxy);
        // get the EJB client context identifier (if any) that's applicable for the EJBObject view on which this
        // invocation happened
        final EJBClientContextIdentifier ejbClientContextIdentifier = invocationContext.getInvocationHandler().getEjbClientContextIdentifier();
        // now recreate the returned EJB home proxy with the EJB client context identifier
        return EJBClient.createProxy(ejbLocator, ejbClientContextIdentifier);
    }

    /**
     * Returns true if the invocation happened on a EJB home view. Else returns false.
     *
     * @param invocationContext
     * @return
     */
    private boolean isEJBObjectInvocation(final EJBClientInvocationContext invocationContext) {
        return invocationContext.getInvokedProxy() instanceof EJBObject;
    }

    /**
     * Returns true if the invocation is for a <code>getEJBHome</code> method. Else returns false.
     *
     * @param invocationContext The invocation context
     * @return
     */
    private boolean isGetEJBHomeMethodInvocation(final EJBClientInvocationContext invocationContext) {
        final Method invokedMethod = invocationContext.getInvokedMethod();
        return invokedMethod.getName().equals("getEJBHome");
    }

}
