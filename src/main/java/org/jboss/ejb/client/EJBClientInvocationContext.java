/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.ejb.client;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Math.max;
import static java.lang.Thread.holdsLock;

import javax.net.ssl.SSLContext;
import javax.transaction.Transaction;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientTransactionPolicy;
import org.wildfly.security.auth.client.AuthenticationConfiguration;

/**
 * An invocation context for EJB invocations from an EJB client
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Jaikiran Pai
 */
public final class EJBClientInvocationContext extends AbstractInvocationContext {

    private static final Logs log = Logs.MAIN;

    public static final String PRIVATE_ATTACHMENTS_KEY = "org.jboss.ejb.client.invocation.attachments";

    private static final int MAX_RETRIES = 5;

    // Contextual stuff
    private final EJBInvocationHandler<?> invocationHandler;

    // Invocation data
    private final Object invokedProxy;
    private final Object[] parameters;
    private final EJBProxyInformation.ProxyMethodInfo methodInfo;
    private final EJBReceiverInvocationContext receiverInvocationContext = new EJBReceiverInvocationContext(this);

    // Invocation state
    private final Object lock = new Object();
    private EJBReceiverInvocationContext.ResultProducer resultProducer;

    private State state = State.WAITING;
    private Object cachedResult;

    private int interceptorChainIndex;
    private boolean resultDone;
    private boolean blockingCaller;
    private Transaction transaction;
    private AuthenticationConfiguration authenticationConfiguration;
    private SSLContext sslContext;

    EJBClientInvocationContext(final EJBInvocationHandler<?> invocationHandler, final EJBClientContext ejbClientContext, final Object invokedProxy, final Object[] parameters, final EJBProxyInformation.ProxyMethodInfo methodInfo) {
        super(invocationHandler.getLocator(), ejbClientContext);
        this.invocationHandler = invocationHandler;
        this.invokedProxy = invokedProxy;
        this.parameters = parameters;
        this.methodInfo = methodInfo;
    }

    enum State {
        WAITING(true),
        CANCEL_REQ(true),
        CANCELLED(false),
        READY(false),
        CONSUMING(false),
        FAILED(false),
        DONE(false),
        DISCARDED(false),
        ;

        private final boolean waiting;

        State(final boolean waiting) {
            this.waiting = waiting;
        }

        boolean isWaiting() {
            return waiting;
        }
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
     * Determine whether the method is marked client-asynchronous, meaning that invocation should be asynchronous regardless
     * of whether the server-side method is asynchronous.
     *
     * @return {@code true} if the method is marked client-asynchronous, {@code false} otherwise
     */
    public boolean isClientAsync() {
        return invocationHandler.isAsyncHandler() || methodInfo.isClientAsync();
    }

    /**
     * Determine whether the method is definitely synchronous, that is, it is not marked client-async, and the return
     * value of the method is not {@code void} or {@code Future<?>}.
     *
     * @return {@code true} if the method is definitely synchronous, {@code false} if the method may be asynchronous
     */
    public boolean isSynchronous() {
        return ! isClientAsync() && methodInfo.isSynchronous();
    }

    /**
     * Determine whether the method is marked idempotent, meaning that the method may be invoked more than one time with
     * no additional effect.
     *
     * @return {@code true} if the method is marked idempotent, {@code false} otherwise
     */
    public boolean isIdempotent() {
        return methodInfo.isIdempotent();
    }

    /**
     * Determine whether the method has an explicit transaction policy set.
     *
     * @return the transaction policy, if any, or {@code null} if none was explicitly set
     */
    public ClientTransactionPolicy getTransactionPolicy() {
        return methodInfo.getTransactionPolicy();
    }

    /**
     * Determine whether the request is expected to be compressed.
     *
     * @return {@code true} if the request is expected to be compressed, {@code false} otherwise
     */
    public boolean isCompressRequest() {
        return methodInfo.isCompressRequest();
    }

    /**
     * Determine whether the response is expected to be compressed.
     *
     * @return {@code true} if the response is expected to be compressed, {@code false} otherwise
     */
    public boolean isCompressResponse() {
        return methodInfo.isCompressResponse();
    }

    /**
     * Get the compression hint level.  If no compression hint is given, -1 is returned.
     *
     * @return the compression hint level, or -1 for no compression hint
     */
    public int getCompressionLevel() {
        return methodInfo.getCompressionLevel();
    }

    /**
     * Get the method type signature string, used to identify the method.
     *
     * @return the method signature string
     */
    public String getMethodSignatureString() {
        return methodInfo.getSignature();
    }

    /**
     * Get the EJB method locator.
     *
     * @return the EJB method locator
     */
    public EJBMethodLocator getMethodLocator() {
        return methodInfo.getMethodLocator();
    }

    /**
     * Determine whether this invocation is currently blocking the calling thread.
     *
     * @return {@code true} if the calling thread is being blocked; {@code false} otherwise
     */
    public boolean isBlockingCaller() {
        synchronized (lock) {
            return blockingCaller;
        }
    }

    /**
     * Establish whether this invocation is currently blocking the calling thread.
     *
     * @param blockingCaller {@code true} if the calling thread is being blocked; {@code false} otherwise
     */
    public void setBlockingCaller(final boolean blockingCaller) {
        synchronized (lock) {
            this.blockingCaller = blockingCaller;
        }
    }

    /**
     * Proceed with sending the request normally.
     *
     * @throws Exception if the request was not successfully sent
     */
    public void sendRequest() throws Exception {
        for (int i = 0; i < MAX_RETRIES; i ++) try {
            final int idx = interceptorChainIndex++;
            try {
                final EJBClientContext.InterceptorList list = this.ejbClientContext.getInterceptors(getViewClass(), getInvokedMethod());
                final EJBClientInterceptorInformation[] chain = list.getInformation();
                if (idx > chain.length) {
                    throw Logs.MAIN.sendRequestCalledDuringWrongPhase();
                }
                if (chain.length == idx) {
                    final URI destination = getDestination();
                    final EJBReceiver receiver = getClientContext().resolveReceiver(destination, locator);
                    receiver.processInvocation(receiverInvocationContext);
                } else {
                    chain[idx].getInterceptorInstance().handleInvocation(this);
                }
            } finally {
                interceptorChainIndex --;
            }
            return;
        } catch (RequestSendFailedException e) {
            if (! e.canBeRetried()) {
                throw e;
            }
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
            throw Logs.MAIN.getResultCalledDuringWrongPhase();
        }

        final int idx = this.interceptorChainIndex++;
        try {
            final EJBClientContext.InterceptorList list = this.ejbClientContext.getInterceptors(getViewClass(), getInvokedMethod());
            final EJBClientInterceptorInformation[] chain = list.getInformation();
            if (chain.length == idx) {
                return resultProducer.getResult();
            }
            if (idx == 0) try {
                return chain[idx].getInterceptorInstance().handleInvocationResult(this);
            } finally {
                resultDone = true;
                final Affinity weakAffinity = getAttachment(AttachmentKeys.WEAK_AFFINITY);
                if (weakAffinity != null) {
                    invocationHandler.setWeakAffinity(weakAffinity);
                }
            } else try {
                return chain[idx].getInterceptorInstance().handleInvocationResult(this);
            } finally {
                resultDone = true;
            }
        } finally {
            interceptorChainIndex--;
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
            // no result to discard
            return;
        }

        resultProducer.discardResult();
    }

    void resultReady(EJBReceiverInvocationContext.ResultProducer resultProducer) {
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case CANCEL_REQ: {
                    this.resultProducer = resultProducer;
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
        return methodInfo.getMethod();
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
     * Get the transaction associated with the invocation.  If there is no transaction (i.e. transactions should not
     * be propagated), {@code null} is returned.
     *
     * @return the transaction associated with the invocation, or {@code null} if no transaction should be propagated
     */
    public Transaction getTransaction() {
        return transaction;
    }

    /**
     * Set the transaction associated with the invocation.  If there is no transaction (i.e. transactions should not
     * be propagated), {@code null} should be set.
     *
     * @param transaction the transaction associated with the invocation, or {@code null} if no transaction should be
     *  propagated
     */
    public void setTransaction(final Transaction transaction) {
        this.transaction = transaction;
    }

    AuthenticationConfiguration getAuthenticationConfiguration() {
        return authenticationConfiguration;
    }

    void setAuthenticationConfiguration(final AuthenticationConfiguration authenticationConfiguration) {
        this.authenticationConfiguration = authenticationConfiguration;
    }

    SSLContext getSSLContext() {
        return sslContext;
    }

    void setSSLContext(final SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    Future<?> getFutureResponse() {
        return new FutureResponse();
    }

    void proceedAsynchronously() {
        if (getInvokedMethod().getReturnType() == void.class) {
            this.discardResult();
        }
    }

    /**
     * Wait to determine whether this invocation was cancelled.
     *
     * @return {@code true} if the invocation was cancelled; {@code false} if it completed or failed or the thread was
     *  interrupted
     */
    public boolean awaitCancellationResult() {
        assert ! holdsLock(lock);
        synchronized (lock) {
            for (;;) {
                if (state == State.CANCELLED) {
                    return true;
                } else if (! state.isWaiting()) {
                    return false;
                }
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    Object awaitResponse(final EJBInvocationHandler<?> invocationHandler) throws Exception {
        assert !holdsLock(lock);
        boolean intr = false, cancel = false;
        final long handlerInvTimeout = invocationHandler.getInvocationTimeout();
        final long invocationTimeout = handlerInvTimeout != -1 ? handlerInvTimeout : ejbClientContext.getInvocationTimeout();
        try {
            final Object lock = this.lock;
            synchronized (lock) {
                try {
                    if (invocationTimeout <= 0) {
                        // no timeout; lighter code path
                        while (state.isWaiting()) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                intr = true;
                            }
                        }
                    } else {
                        final long start = System.nanoTime();
                        final long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(invocationTimeout);
                        long remaining = timeoutNanos;
                        while (state.isWaiting()) {
                            if (remaining == 0) {
                                // timed out
                                state = State.FAILED;
                                cancel = true;
                                this.cachedResult = new TimeoutException("No invocation response received in " + invocationTimeout + " milliseconds");
                                break;
                            }
                            try {
                                lock.wait(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                            } catch (InterruptedException e) {
                                intr = true;
                            }
                            remaining = max(0L, timeoutNanos - max(0L, System.nanoTime() - start));
                        }
                    }
                } finally {
                    blockingCaller = false;
                }
                if (state == State.FAILED) {
                    throw (Exception) cachedResult;
                }
            }
            if (cancel) {
                final EJBReceiver receiver = getReceiver();
                if (receiver != null) receiver.cancelInvocation(receiverInvocationContext, false);
            }
            return getResult();
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    void setDiscardResult() {
        assert !holdsLock(lock);
        final EJBReceiverInvocationContext.ResultProducer resultProducer;
        synchronized (lock) {
            if (state == State.DONE) {
                return;
            }
            // result is waiting, discard it
            state = State.DISCARDED;
            resultProducer = this.resultProducer;
            lock.notifyAll();
            // fall out of the lock to discard the result
        }
        if (resultProducer != null) resultProducer.discardResult();
    }

    void cancelled() {
        assert !holdsLock(lock);
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case CANCEL_REQ: {
                    state = State.CANCELLED;
                    lock.notifyAll();
                    break;
                }
            }
        }
    }

    void failed(Exception exception) {
        assert !holdsLock(lock);
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case CANCEL_REQ: {
                    state = State.FAILED;
                    cachedResult = exception;
                    lock.notifyAll();
                    break;
                }
            }
        }
    }

    final class FutureResponse implements Future<Object> {

        FutureResponse() {
        }

        public boolean cancel(final boolean mayInterruptIfRunning) {
            assert !holdsLock(lock);
            synchronized (lock) {
                if (state != State.WAITING) {
                    return false;
                }
                // at this point the task is running and we are allowed to interrupt it. So issue
                // a cancel request and change the current state
                state = State.CANCEL_REQ;
            }
            final EJBReceiver receiver = getReceiver();
            return receiver != null && receiver.cancelInvocation(receiverInvocationContext, mayInterruptIfRunning);
        }

        public boolean isCancelled() {
            assert !holdsLock(lock);
            synchronized (lock) {
                return state == State.CANCELLED;
            }
        }

        public boolean isDone() {
            assert !holdsLock(lock);
            synchronized (lock) {
                switch (state) {
                    case WAITING: {
                        return false;
                    }
                    case CANCEL_REQ:
                    case READY:
                    case FAILED:
                    case CANCELLED:
                    case DONE:
                    case CONSUMING:
                    case DISCARDED: {
                        return true;
                    }
                    default:
                        throw new IllegalStateException();
                }
            }
        }

        public Object get() throws InterruptedException, ExecutionException {
            assert !holdsLock(lock);
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
                    default:
                        throw new IllegalStateException();
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
            assert !holdsLock(lock);
            final EJBReceiverInvocationContext.ResultProducer resultProducer;
            synchronized (lock) {
                loop: for (;;) {
                    switch (state) {
                        case READY: {
                            // Change state to consuming, but don't notify since nobody but us can act on it.
                            // Instead we'll notify after the result is consumed.
                            state = State.CONSUMING;
                            resultProducer = EJBClientInvocationContext.this.resultProducer;
                            // we have to get the result, so break out of here.
                            break loop;
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
                        case WAITING:
                        case CANCEL_REQ:
                        case CONSUMING: {
                            long remaining = unit.toNanos(timeout);
                            if (remaining > 0L) {
                                long now = System.nanoTime();
                                do {
                                    lock.wait(remaining / 1000000L, (int) (remaining % 1000000L));
                                    if (state != State.WAITING && state != State.CANCEL_REQ && state != State.CONSUMING) {
                                        continue loop;
                                    }
                                    remaining -= max(1L, System.nanoTime() - now);
                                } while (remaining > 0L);
                            }
                            throw log.timedOut();
                        }
                        default:
                            throw new IllegalStateException();
                    }
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

    /**
     * A {@link org.jboss.ejb.client.EJBReceiverInvocationContext.ResultProducer} which throws a
     * {@link TimeoutException} to indicate that the client invocation has timed out waiting for a response
     */
    private class InvocationTimeoutResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private final long timeout;

        InvocationTimeoutResultProducer(final long timeout) {
            this.timeout = timeout;
        }

        @Override
        public Object getResult() throws Exception {
            throw new TimeoutException("No invocation response received in " + this.timeout + " milliseconds");
        }

        @Override
        public void discardResult() {
        }
    }
}
