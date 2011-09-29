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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final EJBClientInterceptor<? super A>[] interceptorChain;
    private int idx;
    private boolean requestDone;
    private boolean resultDone;
    private Object[] parameters;
    private EJBReceiverInvocationContext.ResultProducer resultProducer;
    private final EJBReceiverInvocationContext receiverInvocationContext;
    private final FutureResponse futureResponse = new FutureResponse();

    EJBClientInvocationContext(final EJBInvocationHandler invocationHandler, final EJBClientContext ejbClientContext, final A receiverSpecific, final EJBReceiver<A> receiver, final EJBReceiverContext ejbReceiverContext, final Object invokedProxy, final Method invokedMethod, final Object[] parameters, final EJBClientInterceptor<? super A>[] chain) {
        this.invocationHandler = invocationHandler;
        this.ejbClientContext = ejbClientContext;
        this.receiverSpecific = receiverSpecific;
        this.receiver = receiver;
        this.receiverInvocationContext =  new EJBReceiverInvocationContext(this, ejbReceiverContext);
        this.invokedProxy = invokedProxy;
        this.invokedMethod = invokedMethod;
        this.parameters = parameters;
        interceptorChain = chain;
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
     * Proceed with sending the request normally.
     *
     * @throws Exception if the request was not successfully sent
     */
    public void sendRequest() throws Exception {
        if (requestDone) {
            throw new IllegalStateException("sendRequest() called during wrong phase");
        }
        final int idx = this.idx++;
        final EJBClientInterceptor<? super A>[] chain = interceptorChain;
        try {
            if (chain.length == idx) {
                receiver.processInvocation(this, receiverInvocationContext);
            } else {
                chain[idx].handleInvocation(this);
            }
        } finally {
            requestDone = true;
        }
    }

    /**
     * Get the invocation result from this request.  The result is not actually acquired unless all interceptors
     * call this method.  Should only be called from {@link EJBClientInterceptor#handleInvocationResult(EJBClientInvocationContext)}.
     *
     * @return the invocation result
     * @throws Exception if the invocation did not succeed
     */
    public Object getResult() throws Exception {
        final EJBReceiverInvocationContext.ResultProducer resultProducer = this.resultProducer;

        if (resultDone || resultProducer == null) {
            throw new IllegalStateException("getResult() called during wrong phase");
        }

        final int idx = this.idx++;
        final EJBClientInterceptor<? super A>[] chain = interceptorChain;
        try {
            if (chain.length == idx) {
                return resultProducer.getResult();
            } else {
                return chain[idx].handleInvocationResult(this);
            }
        } finally {
            resultDone = true;
        }
    }

    /**
     * Discard the result from this request.  Should only be called from {@link EJBClientInterceptor#handleInvocationResult(EJBClientInvocationContext)}.
     *
     * @throws IllegalStateException if there is no result to discard
     */
    public void discardResult() throws IllegalStateException {
        final EJBReceiverInvocationContext.ResultProducer resultProducer = this.resultProducer;

        if (resultProducer == null) {
            throw new IllegalStateException("discardResult() called during request phase");
        }

        resultProducer.discardResult();
    }

    void resultReady(EJBReceiverInvocationContext.ResultProducer resultProducer) {
        synchronized (futureResponse) {
            idx = 0;
            this.resultProducer = resultProducer;
            futureResponse.notifyAll();
        }
    }

    void requestCancelled() {
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

    Future<?> getFutureResponse() {
        return futureResponse;
    }

    Object awaitResponse() throws Exception {
        boolean intr = false;
        try {
            synchronized (futureResponse) {
                while (resultProducer == null) try {
                    futureResponse.wait();
                } catch (InterruptedException e) {
                    intr = true;
                }
            }
            return getResult();
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    final class FutureResponse implements Future<Object> {
        private boolean cancelled;
        private boolean done;
        private Object result;
        private Throwable exception;

        synchronized void cancelled() {
            cancelled = true;
            notifyAll();
        }

        synchronized void failed(Throwable exception) {
            done = true;
            this.exception = exception;
            notifyAll();
        }

        public synchronized boolean cancel(final boolean mayInterruptIfRunning) {
            // todo - deliver to receiver
            return false;
        }

        public synchronized boolean isCancelled() {
            return cancelled;
        }

        public synchronized boolean isDone() {
            return done;
        }

        public synchronized Object get() throws InterruptedException, ExecutionException {
            while (! done) {
                wait();
            }
            if (cancelled) {
                throw new CancellationException("Request was cancelled");
            }
            if (exception != null) {
                throw new ExecutionException("Remote execution failed", exception);
            }
            return result;
        }

        public synchronized Object get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (! done) {
                long now = System.nanoTime();
                final long end = Math.max(now, now + unit.toNanos(timeout));
                do {
                    if (resultProducer != null) try {
                        idx = 0;
                        return result = getResult();
                    } catch (Throwable e) {
                        exception = e;
                        break; // fall thru to exception
                    } finally {
                        done = true;
                    }
                    final long remaining = end - now;
                    if (remaining <= 0L) {
                        throw new TimeoutException("Timed out");
                    }
                    // wait at least 1ms
                    long millis = (remaining + 999999L) / 1000000L;
                    wait(millis);
                    now = System.nanoTime();
                } while (! done);
            }
            if (cancelled) {
                throw new CancellationException("Request was cancelled");
            }
            if (exception != null) {
                throw new ExecutionException("Remote execution failed", exception);
            }
            return result;
        }
    }
}
