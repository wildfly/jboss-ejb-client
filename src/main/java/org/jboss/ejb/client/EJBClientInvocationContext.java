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

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * An interceptor context for EJB client interceptors.
 *
 * @param <A> the receiver attachment type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientInvocationContext<A> extends Attachable {
    private final EJBInvocationHandler invocationHandler;
    private final EJBClientContext ejbClientContext;
    private final A receiverSpecific;
    private final EJBReceiver<A> receiver;

    private final Object invokedProxy;
    private final Method invokedMethod;
    private Object[] parameters;
    private Callable<?> resultProducer;

    EJBClientInvocationContext(final EJBInvocationHandler invocationHandler, final EJBClientContext ejbClientContext, final A receiverSpecific, final EJBReceiver<A> receiver, final Object invokedProxy, final Method invokedMethod, final Object[] parameters) {
        this.invocationHandler = invocationHandler;
        this.ejbClientContext = ejbClientContext;
        this.receiverSpecific = receiverSpecific;
        this.receiver = receiver;
        this.invokedProxy = invokedProxy;
        this.invokedMethod = invokedMethod;
        this.parameters = parameters;
    }

    /**
     * Get a value attached to the proxy.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the value, or {@code null} if there is none
     */
    public <T> T getProxyAttachment(AttachmentKey<T> key) {
        return invocationHandler.getAttachment(key);
    }

    /**
     * Remove a value attached to the proxy.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the value, or {@code null} if there is none
     */
    public <T> T removeProxyAttachment(final AttachmentKey<T> key) {
        return invocationHandler.removeAttachment(key);
    }

    /**
     * Get a value attached to the EJB client context.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the value, or {@code null} if there is none
     */
    public <T> T getClientContextAttachment(AttachmentKey<T> key) {
        return ejbClientContext.getAttachment(key);
    }

    /**
     * Get a value attached to the receiver.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the value, or {@code null} if there is none
     */
    public <T> T getReceiverAttachment(AttachmentKey<T> key) {
        return receiver.getAttachment(key);
    }

    /**
     * Set a value to be attached to the receiver.
     *
     * @param key the attachment key
     * @param value the new value
     * @param <T> the value type
     * @return the previous value or {@code null} for none
     */
    public <T> T putReceiverAttachment(final AttachmentKey<T> key, final T value) {
        return receiver.putAttachment(key, value);
    }

    /**
     * Get a value attached to this context.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the value, or {@code null} if there is none
     */
    public <T> T getAttachment(AttachmentKey<T> key) {
        return super.getAttachment(key);
    }

    /**
     * Set a value to be attached to this context.
     *
     * @param key the attachment key
     * @param value the new value
     * @param <T> the value type
     * @return the previous value or {@code null} for none
     */
    public <T> T putAttachment(final AttachmentKey<T> key, final T value) {
        return super.putAttachment(key, value);
    }

    /**
     * Remove a value attached to this context.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the old value, or {@code null} if there was none
     */
    public <T> T removeAttachment(final AttachmentKey<T> key) {
        return super.removeAttachment(key);
    }

    /**
     * Get the invocation application name.
     *
     * @return the app name
     */
    public String getAppName() {
        return invocationHandler.getAppName();
    }

    /**
     * Get the invocation module name.
     *
     * @return the module name
     */
    public String getModuleName() {
        return invocationHandler.getModuleName();
    }

    /**
     * Get the invocation distinct name.
     *
     * @return the distinct name
     */
    public String getDistinctName() {
        return invocationHandler.getDistinctName();
    }

    /**
     * Get the invocation bean name.
     *
     * @return the bean name
     */
    public String getBeanName() {
        return invocationHandler.getBeanName();
    }

    /**
     * Get the receiver-specific attachment for protocol-specific interceptors.
     *
     * @return the receiver attachment
     */
    public A getReceiverSpecific() {
        return receiverSpecific;
    }

    /**
     * Get the invocation result from {@link #handleInvocationResult(R)}.
     *
     * @return the invocation result
     * @throws Exception if the invocation did not succeed
     */
    public Object getResult() throws Exception {
        // Step 1.  See if any interceptors are left in the chain; if so call the next one and return the result.

        // Step 2.  Actually return the result from the underlying transport.
        return resultProducer.call();
    }

    void resultReady(Callable<?> resultProducer) {
        this.resultProducer = resultProducer;
        try {
            // Now, call the first interceptor chain member.
            final Object result = getResult();
            // invocation success.
        } catch (Exception e) {
            // invocation failed.
        }
    }

    protected EJBReceiver<A> getReceiver() {
        return receiver;
    }

    public Object getInvokedProxy() {
        return invokedProxy;
    }

    public Method getInvokedMethod() {
        return invokedMethod;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public Class<?> getViewClass() {
        return invocationHandler.getViewClass();
    }
}
