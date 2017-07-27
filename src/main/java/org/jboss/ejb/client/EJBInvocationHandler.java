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

import java.io.NotSerializableException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.ejb.EJBException;
import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

import org.jboss.ejb.client.annotation.CompressionHint;

/**
 * @param <T> the proxy view type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings({"SerializableClassWithUnconstructableAncestor"})
final class EJBInvocationHandler<T> extends Attachable implements InvocationHandler, Serializable {

    private static final long serialVersionUID = 946555285095057230L;

    private static final String ENABLE_ANNOTATION_SCANNING_SYSTEM_PROPERTY = "org.jboss.ejb.client.view.annotation.scan.enabled";

    private final transient boolean async;
    /**
     * @serial the associated EJB locator
     */
    private final EJBLocator<T> locator;
    /**
     * @serial the weak affinity
     */
    private volatile Affinity weakAffinity = Affinity.NONE;

    /**
     * An optional association to a EJB client context
     */
    // we don't serialize EJB client context identifier
    private final transient EJBClientContextIdentifier ejbClientContextIdentifier;

    /**
     * The {@link AnnotationScanner}s which will be used for scanning annotations on the view class which corresponds to the proxy represented by this invocation handler
     */
    // The whole annotation scanning business is for now contained within this invocation handler class and isn't exposed or isn't allowed to be "pluggable" to avoid unnecesary complexity
    // in the EJB client API. The only control user applications have over annotation scanning of view class(es), for now, is via setting a system property to enable or disable the scanning.
    private final transient AnnotationScanner[] annotationScanners = new AnnotationScanner[]{new CompressionHintAnnotationScanner()};

    /**
     * map of methods that can be handled on the client side
     */
    private static final Map<MethodKey, MethodHandler> clientSideMethods;

    static {
        Map<MethodKey, MethodHandler> methods = new HashMap<MethodKey, MethodHandler>();
        methods.put(new MethodKey("equals", Object.class), new EqualsMethodHandler());
        methods.put(new MethodKey("hashCode"), new HashCodeMethodHandler());
        methods.put(new MethodKey("toString"), new ToStringMethodHandler());
        methods.put(new MethodKey("getPrimaryKey"), new GetPrimaryKeyHandler());
        methods.put(new MethodKey("getHandle"), new GetHandleHandler());
        methods.put(new MethodKey("isIdentical", EJBObject.class), new IsIdenticalHandler());
        methods.put(new MethodKey("getHomeHandle"), new GetHomeHandleHandler());
        clientSideMethods = Collections.unmodifiableMap(methods);
    }

    EJBInvocationHandler(final EJBLocator<T> locator) {
        this(null, locator);
    }

    /**
     * Creates an {@link EJBInvocationHandler} for the passed <code>locator</code> and associates this
     * invocation handler with the passed <code>ejbClientContextIdentifier</code>
     *
     * @param ejbClientContextIdentifier (Optional) EJB client context identifier. Can be null.
     * @param locator                    The {@link EJBLocator} cannot be null.
     */
    EJBInvocationHandler(final EJBClientContextIdentifier ejbClientContextIdentifier, final EJBLocator<T> locator) {
        if (locator == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB locator");
        }
        this.ejbClientContextIdentifier = ejbClientContextIdentifier;
        this.locator = locator;
        async = false;
        if (locator instanceof StatefulEJBLocator) {
            // set the weak affinity to the node on which the session was created
            final String sessionOwnerNode = ((StatefulEJBLocator<?>) locator).getSessionOwnerNode();
            if (sessionOwnerNode != null) {
                this.setWeakAffinity(new NodeAffinity(sessionOwnerNode));
            }
        }
        // scan for annotations on the view class, if necessary
        final String annotationScanEnabledSysPropVal = SecurityActions.getSystemProperty(ENABLE_ANNOTATION_SCANNING_SYSTEM_PROPERTY);
        if (annotationScanEnabledSysPropVal != null && Boolean.valueOf(annotationScanEnabledSysPropVal.trim())) {
            scanAnnotationsOnViewClass();
        } else {
            // let's for the sake of potential performance optimization, add an attachment which lets the EJBReceiver(s) and any other decision making
            // code to decide whether it can entirely skip any logic related to "hints" (like @org.jboss.ejb.client.annotation.CompressionHint)
            putAttachment(AttachmentKeys.HINTS_DISABLED, true);
        }
    }

    EJBInvocationHandler(final EJBInvocationHandler<T> other) {
        super(other);
        locator = other.locator;
        async = true;
        ejbClientContextIdentifier = other.ejbClientContextIdentifier;
        if (locator instanceof StatefulEJBLocator) {
            // set the weak affinity to the node on which the session was created
            final String sessionOwnerNode = ((StatefulEJBLocator<?>) locator).getSessionOwnerNode();
            if (sessionOwnerNode != null) {
                this.setWeakAffinity(new NodeAffinity(sessionOwnerNode));
            }
        }
    }

    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        return doInvoke(locator.getViewType().cast(proxy), method, args);
    }

    void setWeakAffinity(Affinity newWeakAffinity) {
        weakAffinity = newWeakAffinity;
    }

    Affinity getWeakAffinity() {
        return weakAffinity;
    }

    /**
     * Returns the {@link EJBClientContextIdentifier} associated with this invocation handler. If this
     * invocation handler isn't associated with any {@link EJBClientContextIdentifier} then this method
     * returns null
     *
     * @return
     */
    EJBClientContextIdentifier getEjbClientContextIdentifier() {
        return this.ejbClientContextIdentifier;
    }

    Object doInvoke(final T proxy, final Method method, final Object[] args) throws Throwable {
        final MethodHandler handler = clientSideMethods.get(new MethodKey(method));
        if (handler != null && handler.canHandleInvocation(this, proxy, method, args)) {
            return handler.invoke(this, proxy, method, args);
        }
        final EJBClientContext context;
        // check if we are associated with a EJB client context identifier
        if (ejbClientContextIdentifier == null) {
            // we aren't associated with a EJB client context identifier, so select the "current"
            // EJB client context
            context = EJBClientContext.requireCurrent();
        } else {
            // we are associated with a specific EJB client context, so fetch it
            context = EJBClientContext.require(this.ejbClientContextIdentifier);
        }
        return doInvoke(this, async, proxy, method, args, context);
    }

    @SuppressWarnings("unchecked")
    static <T> EJBInvocationHandler<? extends T> forProxy(T proxy) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (handler instanceof EJBInvocationHandler) {
            return (EJBInvocationHandler<? extends T>) handler;
        }
        throw Logs.MAIN.proxyNotOurs(proxy, EJBClient.class.getName());
    }

    private static <T> Object doInvoke(final EJBInvocationHandler<T> ejbInvocationHandler, final boolean async, final T proxy, final Method method, final Object[] args, EJBClientContext clientContext) throws Throwable {
        final EJBClientInvocationContext invocationContext = new EJBClientInvocationContext(ejbInvocationHandler, clientContext, proxy, method, args);

        try {
            // send the request
            sendRequestWithPossibleRetries(invocationContext, true, new ArrayList<String>());

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
        } catch (Exception e) {
            //AS7-5937 prevent UndeclaredThrowableException
            if (e instanceof RuntimeException) {
                throw e;
            }
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

    /**
     * Sends a method invocation request to an eligible EJB receiver. If the request sending fails with a {@link RequestSendFailedException} then this method attempts to
     * retry sending that request to some other eligible node (if any). It does this till either the request was successfully sent or till there are no more eligible EJB receivers
     * which can handle this request
     *
     * @param clientInvocationContext The EJB client invocation context
     * @param firstAttempt            True if this is a first attempt at sending the request, false if this is a retry
     * @throws Exception
     */
    private static void sendRequestWithPossibleRetries(final EJBClientInvocationContext clientInvocationContext, final boolean firstAttempt, final List<String> failedNodes) throws Exception {
        try {
            // this is the first attempt so use the sendRequest API
            if (firstAttempt) {
                clientInvocationContext.sendRequest();
            } else {
                // retry
                clientInvocationContext.retryRequest();
            }
        } catch (RequestSendFailedException rsfe) {
            // check whether the first attempt fails during serialization
            if (firstAttempt) {
                // EJBCLIENT-106 supress retry and throw the root cause
                if (rsfe.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) rsfe.getCause();
                }else if(rsfe.getCause() instanceof NotSerializableException) {
                    throw (NotSerializableException)rsfe.getCause();
                }else if (rsfe.getCause() instanceof UnmarshalException) {
                    throw (UnmarshalException) rsfe.getCause();
                }
            }
            // check to see if the request has already been retried by response-triggered retry mechanism
            // despite the fact that the send raised an exception (see WFLY-6417)
            if (clientInvocationContext.isDone()) {
                Logs.MAIN.debugf(rsfe, "Aborting client-side retry of invocation %s (the request has already returned), due to:", clientInvocationContext);
                return;
            }
            // retry the request
            final String failedNodeName = rsfe.getFailedNodeName();
            if (failedNodeName != null && !failedNodes.contains(failedNodeName)) {
                failedNodes.add(failedNodeName);
                Logs.MAIN.debugf(rsfe, "Retrying invocation %s which failed on node: %s due to:", clientInvocationContext, failedNodeName);
                // retry
                sendRequestWithPossibleRetries(clientInvocationContext, false, failedNodes);
            } else {
                throw rsfe;
            }
        }
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

    /**
     * Scans for annotations on the view class and its methods using pre-configured {@link AnnotationScanner}s (if any)
     */
    private void scanAnnotationsOnViewClass() {
        // nothing to do
        if (annotationScanners == null || annotationScanners.length == 0) {
            return;
        }
        final Class<?> viewClass = this.locator.getViewType();
        final Method[] viewMethods = viewClass.getMethods();
        for (final AnnotationScanner annotationScanner : annotationScanners) {
            // scan class level annotations
            annotationScanner.scanClass(viewClass);
            // now method level annotations
            for (final Method viewMethod : viewMethods) {
                annotationScanner.scanMethod(viewMethod);
            }
        }
    }

    private static final class MethodKey {

        private final String name;
        private final Class<?>[] parameters;


        public MethodKey(final String name, final Class<?>... parameters) {
            this.name = name;
            this.parameters = parameters;
        }

        public MethodKey(final Method method) {
            this.name = method.getName();
            this.parameters = method.getParameterTypes();
        }

        @Override
        public boolean equals(final Object o) {
            //we don't care about the return type
            //we want covariant methods to still be handled
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final MethodKey methodKey = (MethodKey) o;

            if (!name.equals(methodKey.name)) return false;
            if (!Arrays.equals(parameters, methodKey.parameters)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(parameters);
            return result;
        }
    }

    private interface MethodHandler {

        boolean canHandleInvocation(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception;

        Object invoke(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception;
    }

    private static final class EqualsMethodHandler implements MethodHandler {

        @Override
        public boolean canHandleInvocation(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) {
            return true;
        }

        @Override
        public Object invoke(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) {
            final Object other = args[0];
            if (other instanceof Proxy) {
                final InvocationHandler handler = Proxy.getInvocationHandler(other);
                if (handler instanceof EJBInvocationHandler) {
                    return thisHandler.locator.equals(((EJBInvocationHandler<?>) handler).locator);
                }
            }
            return Boolean.FALSE;
        }
    }


    private static final class HashCodeMethodHandler implements MethodHandler {

        @Override
        public boolean canHandleInvocation(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) {
            return true;
        }

        @Override
        public Object invoke(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) {
            return thisHandler.locator.hashCode();
        }
    }

    private static final class ToStringMethodHandler implements MethodHandler {

        @Override
        public boolean canHandleInvocation(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) {
            return true;
        }

        @Override
        public Object invoke(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) {
            return String.format("Proxy for remote EJB %s", thisHandler.locator);
        }
    }

    private static final class GetPrimaryKeyHandler implements MethodHandler {

        @Override
        public boolean canHandleInvocation(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception {
            return proxy instanceof EJBObject;
        }

        @Override
        public Object invoke(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception {
            final EJBLocator<?> locator = thisHandler.locator;
            if (locator instanceof EntityEJBLocator) {
                return ((EntityEJBLocator) locator).getPrimaryKey();
            }
            throw new RemoteException("Cannot invoke getPrimaryKey() om " + proxy);
        }
    }

    private static final class GetHandleHandler implements MethodHandler {

        @Override
        public boolean canHandleInvocation(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception {
            return proxy instanceof EJBObject && thisHandler.locator instanceof EJBLocator;
        }

        @Override
        public Object invoke(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception {
            final EJBLocator locator = (EJBLocator) thisHandler.getLocator();
            return new EJBHandle(locator);
        }
    }

    private static final class IsIdenticalHandler implements MethodHandler {

        @Override
        public boolean canHandleInvocation(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception {
            return proxy instanceof EJBObject && thisHandler.locator instanceof EJBLocator;
        }

        @Override
        public Object invoke(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception {
            final EJBLocator<?> locator = thisHandler.locator;
            final Object other = args[0];
            if (Proxy.isProxyClass(other.getClass())) {
                final InvocationHandler handler = Proxy.getInvocationHandler(other);
                if (handler instanceof EJBInvocationHandler) {
                    return locator.equals(((EJBInvocationHandler<?>) handler).getLocator());
                }
            }
            return false;
        }
    }

    private static final class GetHomeHandleHandler implements MethodHandler {

        @Override
        public boolean canHandleInvocation(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception {
            return proxy instanceof EJBHome;
        }

        @Override
        public Object invoke(final EJBInvocationHandler<?> thisHandler, final Object proxy, final Method method, final Object[] args) throws Exception {
            final EJBLocator locator = (EJBLocator) thisHandler.getLocator();
            if (locator instanceof EJBHomeLocator) {
                return new EJBHomeHandle((EJBHomeLocator) locator);
            }
            throw new RemoteException("Cannot invoke getHomeHandle() on " + proxy);
        }
    }

    /**
     * An {@link AnnotationScanner} is responsible for scanning for (some known) annotations on the view class and its methods. It's up to the implementations of this
     * interface to decide what to do with the annotation it finds on the view class or the view method. Typical implementations attach the found annotation(s) as "attachments" to the
     * {@link EJBInvocationHandler} so that the annotation information is available for invocations, happening on this invocation handler, through the use of {@link EJBClientInvocationContext#getProxyAttachment(AttachmentKey)}
     */
    private interface AnnotationScanner {
        void scanClass(final Class<?> view);

        void scanMethod(final Method method);
    }

    /**
     * An {@link AnnotationScanner} responsible for scanning the {@link org.jboss.ejb.client.annotation.CompressionHint} annotation on the view class and its methods. This
     * {@link org.jboss.ejb.client.EJBInvocationHandler.CompressionHintAnnotationScanner} attaches the found annotations (if any) to the invocation handler through the use of {@link EJBInvocationHandler#putAttachment(AttachmentKey, Object)}
     * method. The class level {@link org.jboss.ejb.client.annotation.CompressionHint} annotation (if any) is attached with {@link AttachmentKeys#VIEW_CLASS_DATA_COMPRESSION_HINT_ATTACHMENT_KEY} as the key and the method level
     * {@link org.jboss.ejb.client.annotation.CompressionHint} annotations (if any) is attached with {@link AttachmentKeys#VIEW_METHOD_DATA_COMPRESSION_HINT_ATTACHMENT_KEY} as the key
     */
    private final class CompressionHintAnnotationScanner implements AnnotationScanner {

        @Override
        public void scanClass(Class<?> view) {
            final CompressionHint compressionHint = (CompressionHint) view.getAnnotation(CompressionHint.class);
            // nothing to do
            if (compressionHint == null) {
                return;
            }
            // add it as an attachment to the invocation handler so that it's available during an invocation via the EJBClientInvocationContext#getProxyAttachment()
            EJBInvocationHandler.this.putAttachment(AttachmentKeys.VIEW_CLASS_DATA_COMPRESSION_HINT_ATTACHMENT_KEY, compressionHint);
        }

        @Override
        public void scanMethod(Method method) {
            final CompressionHint compressionHint = (CompressionHint) method.getAnnotation(CompressionHint.class);
            // nothing to do
            if (compressionHint == null) {
                return;
            }
            Map<Method, CompressionHint> annotatedMethods = EJBInvocationHandler.this.getAttachment(AttachmentKeys.VIEW_METHOD_DATA_COMPRESSION_HINT_ATTACHMENT_KEY);
            if (annotatedMethods == null) {
                // Intentionally avoiding IdentityHashMap since it doesn't work out because the methods being scanned here and the methods being invoked upon later, aren't the "same" (i.e. this method != that method)
                annotatedMethods = new HashMap<Method, CompressionHint>();
                EJBInvocationHandler.this.putAttachment(AttachmentKeys.VIEW_METHOD_DATA_COMPRESSION_HINT_ATTACHMENT_KEY, annotatedMethods);
            }
            annotatedMethods.put(method, compressionHint);
        }
    }
}
