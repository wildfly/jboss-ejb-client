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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A {@link EJBClientInterceptor} which is responsible for intercepting the returning value of "create"/"finder" methods
 * on a {@link javax.ejb.EJBHome} proxy. This interceptor just lets the invocation proceed in its {@link #handleInvocation(EJBClientInvocationContext)}
 * method. However, in its {@link #handleInvocationResult(EJBClientInvocationContext)} method it does the following:
 * <ol>
 * <li>Check to see if the invocation is happening on a EJBHome proxy. It does this by checking the {@link EJBLocator}
 * type of the {@link EJBClientInvocationContext invocation context}. If it finds that the invocation is not
 * on a EJBHome proxy, then the {@link #handleInvocationResult(EJBClientInvocationContext)} just returns back the
 * original result.
 * </li>
 * <li>
 * Check to see if the invoked method is a "create"/"finder" method. The EJB spec states that each EJBHome interface
 * can have any number of "create"/"finder" methods, but the method name should begin with "create"/"find". This is what the
 * interceptor checks for. If the invocation is not for a "create"/"find" method, then the {@link #handleInvocationResult(EJBClientInvocationContext)}
 * just returns back the original result.
 * </li>
 * <li>
 * Check to see if the original returned instance is a {@link EJBClient#isEJBProxy(Object) EJB proxy} or a {@link Collection} of {@link EJBClient#isEJBProxy(Object) EJB proxies}.
 * If it isn't, then the {@link #handleInvocationResult(EJBClientInvocationContext)} method just returns back the
 * original result. If it finds that it's an EJB proxy or a Collection of EJB proxies, then the {@link #handleInvocationResult(EJBClientInvocationContext)}
 * recreates the proxy/proxies by using the {@link org.jboss.ejb.client.EJBInvocationHandler#getEjbClientContextIdentifier() EJB client context identifier}
 * that's applicable to the EJBHome proxy on which this invocation was done. This way, the EJB proxies returned
 * by calls to "create"/"finder" methods on the EJBHome proxy will always be associated with the EJB client context identifier
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
 * @see https://issues.jboss.org/browse/EJBCLIENT-51
 */
final class EJBHomeInterceptor implements EJBClientInterceptor {

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
        final boolean isCreateMethod = isCreateMethodInvocation(invocationContext);
        final boolean isFinderMethod = isFinderMethod(invocationContext);
        boolean finderReturnTypeCollection = false;
        // if it's not a "createXXX" method invocation nor an entity home "finder" method invocation, then just return back the original result
        if (!isCreateMethod && !isFinderMethod) {
            return originalResult;
        }
        // if it's a create method then the return type of the method isn't expected to be a Collection, unlike ceratin finder methods (on entity beans)
        if (isCreateMethod) {
            // if the original result isn't a EJB proxy, then we just return back the original result
            if (!EJBClient.isEJBProxy(originalResult)) {
                return originalResult;
            }
        } else if (isFinderMethod) {
            // a finder method can return a single result or a collection, so check the type and if the return type is neither a Collection
            // nor a (single) EJB proxy, then just return back the original result
            if (!(originalResult instanceof Collection) && !EJBClient.isEJBProxy(originalResult)) {
                return originalResult;
            }
            if (originalResult instanceof Collection) {
                // we have identified this to be a finder method returning a Collection. Now ensure that the Collection contains
                // all EJB proxies. If it doesn't, then just return back the original result
                for (Object finderResult : (Collection) originalResult) {
                    if (finderResult != null && !EJBClient.isEJBProxy(finderResult)) {
                        return originalResult;
                    }
                }
                // make a note that this is a finder method returning a collection of EJB proxies
                finderReturnTypeCollection = true;
            }
        }
        // at this point we have identified the invocation to be a "createXXX" or a "findXXX" method invocation on a EJB
        // home view and the return object being a EJB proxy or a Collection of EJB proxies. So we now update that proxy/proxies to use
        // the EJB client context identifier that's applicable for the EJB home proxy on which this invocation was done
        if (finderReturnTypeCollection) {
            final Collection ejbProxies = (Collection) originalResult;
            final List recreatedEJBProxies = new ArrayList();
            // recreate each proxy in the Collection, to use the context identifier
            for (Object ejbProxy : ejbProxies) {
                // we *don't* change the locator of the original proxy
                final EJBLocator ejbLocator = EJBClient.getLocatorFor(ejbProxy);
                // get the EJB client context identifier (if any) that's applicable for the home view on which this
                // invocation happened
                final EJBClientContextIdentifier ejbClientContextIdentifier = invocationContext.getInvocationHandler().getEjbClientContextIdentifier();
                // now recreate the proxy with the EJB client context identifier
                final Object recreatedProxy = EJBClient.createProxy(ejbLocator, ejbClientContextIdentifier);
                // add it to the collection to be returned
                recreatedEJBProxies.add(recreatedProxy);
            }
            // return the Collection containing the recreated proxies
            return recreatedEJBProxies;
        } else {
            final Object ejbProxy = originalResult;
            // we *don't* change the locator of the original proxy
            final EJBLocator ejbLocator = EJBClient.getLocatorFor(ejbProxy);
            // get the EJB client context identifier (if any) that's applicable for the home view on which this
            // invocation happened
            final EJBClientContextIdentifier ejbClientContextIdentifier = invocationContext.getInvocationHandler().getEjbClientContextIdentifier();
            // now recreate the proxy with the EJB client context identifier
            return EJBClient.createProxy(ejbLocator, ejbClientContextIdentifier);
        }
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
     * @param invocationContext The invocation context
     * @return
     */
    private boolean isCreateMethodInvocation(final EJBClientInvocationContext invocationContext) {
        final Method invokedMethod = invocationContext.getInvokedMethod();
        return invokedMethod.getName().startsWith("create");
    }

    /**
     * Returns true if the invocation is for a finder method of an entity bean home. Else returns false.
     * Finder methods on entity bean remote home interface are expected to start with the name <code>"find"</code>
     * and one such method <code>findByPrimaryKey</code> is mandatory.
     *
     * @param invocationContext The invocation context
     * @return
     */
    private boolean isFinderMethod(final EJBClientInvocationContext invocationContext) {
        final Method invokedMethod = invocationContext.getInvokedMethod();
        return invokedMethod.getName().equals("findByPrimaryKey") || invokedMethod.getName().startsWith("find");
    }
}
