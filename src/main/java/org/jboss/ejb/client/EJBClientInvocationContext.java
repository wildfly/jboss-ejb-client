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

import static java.lang.Thread.holdsLock;

/**
 * An interceptor context for EJB client interceptors.
 *
 * @param <A> the receiver attachment type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientInvocationContext<A> extends Attachable {

    private static final Logs log = Logs.MAIN;

    // Contextual stuff
    private final EJBReceiverInvocationContext receiverInvocationContext;
    private final EJBInvocationHandler invocationHandler;
    private final EJBClientContext ejbClientContext;
    private final EJBReceiver<A> receiver;
    private final A receiverSpecific;

    // Invocation data
    private final Object invokedProxy;
    private final Method invokedMethod;
    private final Object[] parameters;

    // Invocation state
    private final Object lock = new Object();
    private EJBReceiverInvocationContext.ResultProducer resultProducer;
    private State state = State.WAITING;
    private AsyncState asyncState = AsyncState.SYNCHRONOUS;
    private Object cachedResult;

    // Interceptor state
    private final EJBClientInterceptor<? super A>[] interceptorChain;
    private int idx;
    private boolean requestDone;
    private boolean resultDone;

    EJBClientInvocationContext(final EJBInvocationHandler invocationHandler, final EJBClientContext ejbClientContext, final A receiverSpecific, final EJBReceiver<A> receiver, final EJBReceiverContext ejbReceiverContext, final Object invokedProxy, final Method invokedMethod, final Object[] parameters, final EJBClientInterceptor<? super A>[] interceptorChain) {
        this.invocationHandler = invocationHandler;
        this.ejbClientContext = ejbClientContext;
        this.receiverSpecific = receiverSpecific;
        this.receiver = receiver;
        this.invokedProxy = invokedProxy;
        this.invokedMethod = invokedMethod;
        this.parameters = parameters;
        this.interceptorChain = interceptorChain;
        //noinspection ThisEscapedInObjectConstruction
        receiverInvocationContext = new EJBReceiverInvocationContext(this, ejbReceiverContext);
    }

    enum AsyncState {
        SYNCHRONOUS,
        ASYNCHRONOUS,
        ONE_WAY;
    }

    enum State {
        WAITING,
        CANCEL_REQ,
        CANCELLED,
        READY,
        CONSUMING,
        FAILED,
        DONE,
        DISCARDED;
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
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case CANCEL_REQ: {
                    this.resultProducer = resultProducer;
                    idx = 0;
                    state = State.READY;
                    lock.notifyAll();
                    return;
                }
            }
        }
        // for whatever reason, we don't care
        resultProducer.discardResult();
        return;
    }

    /**
     * Get the EJB receiver associated with this invocation.
     *
     * @return the EJB receiver
     */
    protected EJBReceiver<A> getReceiver() {
        return receiver;
    }

    /**
     * Get the invoked proxy object.
     *
     * @return the invoked proxy
     */
    public Object getInvokedProxy() {
        return invokedProxy;
    }

    /**
     * Get the invoked proxy method.
     *
     * @return the invoked method
     */
    public Method getInvokedMethod() {
        return invokedMethod;
    }

    /**
     * Get the invocation method parameters.
     *
     * @return the invocation method parameters
     */
    public Object[] getParameters() {
        return parameters;
    }

    /**
     * Get the invoked view class.
     *
     * @return the invoked view class
     */
    public Class<?> getViewClass() {
        return invocationHandler.getViewClass();
    }

    Future<?> getFutureResponse() {
        return new FutureResponse();
    }

    static final Object PROCEED_ASYNC = new Object();

    void proceedAsynchronously() {
        assert ! holdsLock(lock);
        synchronized (lock) {
            if (asyncState == AsyncState.SYNCHRONOUS) {
                asyncState = AsyncState.ASYNCHRONOUS;
                notifyAll();
            }
        }
    }

    Object awaitResponse() throws Exception {
        assert ! holdsLock(lock);
        boolean intr = false;
        try {
            synchronized (lock) {
                if (asyncState == AsyncState.ASYNCHRONOUS) {
                    return PROCEED_ASYNC;
                } else if (asyncState == AsyncState.ONE_WAY) {
                    throw log.oneWayInvocation();
                }
                while (state == State.WAITING) try {
                    lock.wait();
                    if (asyncState == AsyncState.ASYNCHRONOUS) {
                        // It's an asynchronous invocation; proceed asynchronously.
                        return PROCEED_ASYNC;
                    } else if (asyncState == AsyncState.ONE_WAY) {
                        throw log.oneWayInvocation();
                    }
                } catch (InterruptedException e) {
                    intr = true;
                }
            }
            return getResult();
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    void setDiscardResult() {
        assert ! holdsLock(lock);
        final EJBReceiverInvocationContext.ResultProducer resultProducer;
        synchronized (lock) {
            if (asyncState != AsyncState.ONE_WAY) {
                asyncState = AsyncState.ONE_WAY;
                notifyAll();
            }
            if (state != State.DONE) {
                return;
            }
            // result is waiting, discard it
            state = State.DISCARDED;
            resultProducer = this.resultProducer;
            notifyAll();
            // fall out of the lock to discard the result
        }
        resultProducer.discardResult();
    }

    void cancelled() {
        assert ! holdsLock(lock);
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case CANCEL_REQ: {
                    state = State.CANCELLED;
                    notifyAll();
                    break;
                }
            }
        }
    }

    void failed(Throwable exception) {
        assert ! holdsLock(lock);
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case CANCEL_REQ: {
                    state = State.FAILED;
                    cachedResult = exception;
                    notifyAll();
                    break;
                }
            }
        }
    }

    final class FutureResponse implements Future<Object> {

        public boolean cancel(final boolean mayInterruptIfRunning) {
            assert ! holdsLock(lock);
            synchronized (lock) {
                if (state != State.WAITING) {
                    return false;
                }
                state = State.CANCEL_REQ;
            }
            return receiver.cancelInvocation(EJBClientInvocationContext.this, receiverInvocationContext);
        }

        public boolean isCancelled() {
            assert ! holdsLock(lock);
            synchronized (lock) {
                return state == State.CANCELLED;
            }
        }

        public boolean isDone() {
            assert ! holdsLock(lock);
            synchronized (lock) {
                switch (state) {
                    case WAITING:
                    case CANCEL_REQ: {
                        return false;
                    }
                    case READY:
                    case FAILED:
                    case CANCELLED:
                    case DONE:
                    case CONSUMING:
                    case DISCARDED: {
                        return true;
                    }
                    default: throw new IllegalStateException();
                }
            }
        }

        public Object get() throws InterruptedException, ExecutionException {
            assert ! holdsLock(lock);
            final EJBReceiverInvocationContext.ResultProducer resultProducer;
            synchronized (lock) {
                while (state == State.WAITING || state == State.CANCEL_REQ || state == State.CONSUMING) lock.wait();
                switch (state) {
                    case READY: {
                        // Change state to consuming, but don't notify since nobody but us can act on it.
                        // Instead we'll notify after the result is consumed.
                        state = State.CONSUMING;
                        resultProducer = EJBClientInvocationContext.this.resultProducer;
                        // we have to get the result, so break out of here.
                        break;
                    }
                    case FAILED: {
                        throw log.remoteInvFailed((Throwable) cachedResult);
                    }
                    case CANCELLED: {
                        throw log.requestCancelled();
                    }
                    case DONE: {
                        return cachedResult;
                    }
                    case DISCARDED: {
                        throw log.oneWayInvocation();
                    }
                    default: throw new IllegalStateException();
                }
            }
            // extract the result from the producer.
            Object result;
            try {
                result = resultProducer.getResult();
            } catch (Exception e) {
                synchronized (lock) {
                    assert state == State.CONSUMING;
                    state = State.FAILED;
                    cachedResult = e;
                    lock.notifyAll();
                }
                throw log.remoteInvFailed(e);
            }
            synchronized (lock) {
                assert state == State.CONSUMING;
                state = State.DONE;
                cachedResult = result;
                lock.notifyAll();
            }
            return result;
        }

        public Object get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            assert ! holdsLock(lock);
            final EJBReceiverInvocationContext.ResultProducer resultProducer;
            synchronized (lock) {
                if (state == State.WAITING || state == State.CANCEL_REQ || state == State.CONSUMING) {
                    long now = System.nanoTime();
                    final long end = Math.max(now, now + unit.toNanos(timeout));
                    do {
                        final long remaining = end - now;
                        if (remaining <= 0L) {
                            throw log.timedOut();
                        }
                        // wait at least 1ms
                        long millis = (remaining + 999999L) / 1000000L;
                        wait(millis);
                        now = System.nanoTime();
                    } while (state == State.WAITING || state == State.CANCEL_REQ || state == State.CONSUMING);
                }
                switch (state) {
                    case READY: {
                        // Change state to consuming, but don't notify since nobody but us can act on it.
                        // Instead we'll notify after the result is consumed.
                        state = State.CONSUMING;
                        resultProducer = EJBClientInvocationContext.this.resultProducer;
                        // we have to get the result, so break out of here.
                        break;
                    }
                    case FAILED: {
                        throw log.remoteInvFailed((Throwable) cachedResult);
                    }
                    case CANCELLED: {
                        throw log.requestCancelled();
                    }
                    case DONE: {
                        return cachedResult;
                    }
                    case DISCARDED: {
                        throw log.oneWayInvocation();
                    }
                    default: throw new IllegalStateException();
                }
            }
            // extract the result from the producer.
            Object result;
            try {
                result = resultProducer.getResult();
            } catch (Exception e) {
                synchronized (lock) {
                    assert state == State.CONSUMING;
                    state = State.FAILED;
                    cachedResult = e;
                    lock.notifyAll();
                }
                throw log.remoteInvFailed(e);
            }
            synchronized (lock) {
                assert state == State.CONSUMING;
                state = State.DONE;
                cachedResult = result;
                lock.notifyAll();
            }
            return result;
        }
    }

    protected void finalize() throws Throwable {
        assert ! holdsLock(lock);
        final EJBReceiverInvocationContext.ResultProducer resultProducer;
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case CANCEL_REQ: {
                    // the receiver lost its reference to us AND no callers actually seem to care.
                    // There's not a whole lot to do at this point.
                    return;
                }
                case READY: {
                    // there's a result waiting.  We shall simply discard it (outside of the lock though), to make
                    // sure that the transport layer doesn't have resources (like sockets) which are held up.
                    resultProducer = this.resultProducer;
                    break;
                }
                default: {
                    // situation normal, or close enough.
                    return;
                }
            }
        }
        resultProducer.discardResult();
    }
}
