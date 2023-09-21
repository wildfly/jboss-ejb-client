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
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static java.lang.Math.max;
import static java.lang.Thread.holdsLock;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientTransactionPolicy;
import org.wildfly.common.Assert;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.discovery.Discovery;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * An invocation context for EJB invocations from an EJB client
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Jaikiran Pai
 */
public final class EJBClientInvocationContext extends AbstractInvocationContext {

    private static final Logs log = Logs.MAIN;

    public static final String PRIVATE_ATTACHMENTS_KEY = "org.jboss.ejb.client.invocation.attachments";
    /**
     * A context data key that may contain a Set of Strings. Any context data on the server side invocation context
     * stored under these keys will be returned to the client.
     */
    public static final String RETURNED_CONTEXT_DATA_KEY = "jboss.returned.keys";

    // Contextual stuff
    private final EJBInvocationHandler<?> invocationHandler;
    private final Discovery discoveryContext;

    // Invocation data
    private final Object invokedProxy;
    private final Object[] parameters;
    private final EJBProxyInformation.ProxyMethodInfo methodInfo;
    private final EJBReceiverInvocationContext receiverInvocationContext = new EJBReceiverInvocationContext(this);
    private final EJBClientContext.InterceptorList interceptorList;
    private final long startTime = System.nanoTime();
    private long timeout;

    // Invocation state
    private final Object lock = new Object();
    private EJBReceiverInvocationContext.ResultProducer resultProducer;

    private volatile boolean cancelRequested;
    private boolean retryRequested;
    private State state = State.SENDING;
    private int remainingRetries;
    private Supplier<? extends Throwable> pendingFailure;
    private List<Supplier<? extends Throwable>> suppressedExceptions;
    private Object cachedResult;

    private int interceptorChainIndex;
    private boolean blockingCaller;
    private int waiters = 0;

    EJBClientInvocationContext(final EJBInvocationHandler<?> invocationHandler, final EJBClientContext ejbClientContext, final Object invokedProxy, final Object[] parameters, final EJBProxyInformation.ProxyMethodInfo methodInfo, final int allowedRetries, final Supplier<AuthenticationContext> authenticationContextSupplier, final Discovery discoveryContext) {
        super(invocationHandler.getLocator(), ejbClientContext, authenticationContextSupplier != null ? authenticationContextSupplier.get() : AuthenticationContext.captureCurrent());
        this.invocationHandler = invocationHandler;
        this.discoveryContext = discoveryContext;
        this.invokedProxy = invokedProxy;
        this.parameters = parameters;
        this.methodInfo = methodInfo;
        long timeout = invocationHandler.getInvocationTimeout();
        if (timeout == -1) {
            timeout = ejbClientContext.getInvocationTimeout();
        }
        this.timeout = timeout;
        remainingRetries = allowedRetries;
        interceptorList = getClientContext().getInterceptors(getViewClass(), getInvokedMethod());
    }

    enum State {
        // waiting states

        SENDING(true),
        SENT(true),
        WAITING(true),

        // completion states

        READY(false),
        CONSUMING(false),
        DONE(false),
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
     * Indicates to the server that a client side interceptor is interested in the context data that is stored
     * under the given key. Any object on the context data map (i.e. {@code InvocationContext.getContextData()} that is
     * present at the end of the server invocation will be serialised and returned to the client.
     *
     * If an object is present under this key and is not serializable then the request will fail.
     *
     * @param key The context data key the client interceptor is interested in
     */
    public void addReturnedContextDataKey(String key) {
        Object returnedData = getContextData().get(RETURNED_CONTEXT_DATA_KEY);
        if(returnedData == null) {
            getContextData().put(RETURNED_CONTEXT_DATA_KEY, returnedData = new HashSet<>());
        } else if(!(returnedData instanceof Set)) {
            throw Logs.INVOCATION.returnedContextDataKeyOfWrongType();
        }
        Set<String> keys = (Set<String>) returnedData;
        keys.add(key);
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
        if (methodInfo.getCompressRequestHint() == EJBProxyInformation.ProxyMethodInfo.CompressionHint.NONE) {
            return getClientContext().getDefaultCompression() != -1;
        }
        return methodInfo.getCompressRequestHint() == EJBProxyInformation.ProxyMethodInfo.CompressionHint.TRUE;
    }

    /**
     * Determine whether the response is expected to be compressed.
     *
     * @return {@code true} if the response is expected to be compressed, {@code false} otherwise
     */
    public boolean isCompressResponse() {
        if (methodInfo.getCompressResponseHint() == EJBProxyInformation.ProxyMethodInfo.CompressionHint.NONE) {
            return getClientContext().getDefaultCompression() != -1;
        }
        return methodInfo.getCompressResponseHint() == EJBProxyInformation.ProxyMethodInfo.CompressionHint.TRUE;
    }

    /**
     * Get the compression hint level.  If no compression hint is given, -1 is returned.
     *
     * @return the compression hint level, or -1 for no compression hint
     */
    public int getCompressionLevel() {
        if(methodInfo.getCompressionLevel() != -1){
            return methodInfo.getCompressionLevel();
        }
        return getClientContext().getDefaultCompression();
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
     * Add a suppressed exception to the request.
     *
     * @param cause the suppressed exception (must not be {@code null})
     */
    public void addSuppressed(Throwable cause) {
        Assert.checkNotNullParam("cause", cause);
        synchronized (lock) {
            if (log.isTraceEnabled()) {
               String message = cause.getMessage() != null ? cause.getMessage() : "null";
               log.tracef("Adding suppressed exception %s to request! (state = %s)", message, state);
            }
            if (state == State.DONE) {
                return;
            }
            if (suppressedExceptions == null) {
                suppressedExceptions = new ArrayList<>();
            }
            suppressedExceptions.add(() -> cause);
            checkStateInvariants();
        }
    }

    /**
     * Add a suppressed exception to the request.
     *
     * @param cause the suppressed exception (must not be {@code null})
     */
    public void addSuppressed(Supplier<? extends Throwable> cause) {
        Assert.checkNotNullParam("cause", cause);
        synchronized (lock) {
            if (log.isTraceEnabled()) {
               String message = (cause.get() != null && cause.get().getMessage() != null) ? cause.get().getMessage() : "null";
               log.tracef("Adding suppressed exception %s to request! (state = %s)", message, state);
            }
            if (state == State.DONE) {
                return;
            }
            if (suppressedExceptions == null) {
                suppressedExceptions = new ArrayList<>();
            }
            suppressedExceptions.add(cause);
            checkStateInvariants();
        }
    }

    public void requestRetry() {
        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("Requesting retry of invocation! (state = %s)", checkState());
        }
        synchronized (lock) {
            retryRequested = true;
        }
    }

    void sendRequestInitial() {
        assert checkState() == State.SENDING;

        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("Calling sendRequestInitial() to run sendRequest() loop (state = %s)", state);
        }
        for (;;) {
            assert interceptorChainIndex == 0;
            try {
                getAuthenticationContext().runExConsumer(EJBClientInvocationContext::sendRequest, this);
                // back to the start of the chain; decide what to do next.
                synchronized (lock) {
                    try {
                        assert state == State.SENT || state == State.READY;
                        // from here we can go to: READY, or WAITING, or retry SENDING.
                        Supplier<? extends Throwable> pendingFailure = this.pendingFailure;
                        EJBReceiverInvocationContext.ResultProducer resultProducer = this.resultProducer;
                        // now see if we're retrying or returning.
                        if (pendingFailure != null) {
                            if (resultProducer == null) {
                                this.resultProducer = new ThrowableResult(pendingFailure);
                            } else {
                                addSuppressed(pendingFailure);
                            }

                            if (log.isTraceEnabled()) {
                                String message = (pendingFailure.get() != null && pendingFailure.get().getMessage() != null) ? pendingFailure.get().getMessage() : "null";
                                log.tracef("Call to sendRequest() has pendingFailure %s (state = %s)", message, state);
                            }

                            // Run the result interceptor chain to see if a retry is necessary
                            this.pendingFailure = null;
                            transition(State.CONSUMING);
                            try {
                                getResult(true);
                            } catch (Throwable t) {
                                if (state == State.SENDING) {
                                    if (log.isTraceEnabled()) {
                                        log.tracef("Call to sendRequest() has triggered retry (state = %s)", state);
                                    }
                                    // Retry has been requested
                                    continue;
                                }
                            }

                            // Return with getResult's set state
                            return;
                        }

                        if (resultProducer != null) {
                            if (log.isTraceEnabled()) {
                                log.tracef("Call to sendRequest() has produced result with producer %s (state = %s)", resultProducer, state);
                            }
                            transition(State.READY);
                            return;
                        }

                        if (log.isTraceEnabled()) {
                            log.tracef("Call to sendRequest() has not yet produced result, waiting (state = %s)", state);
                        }
                        transition(State.WAITING);
                        return;
                    } finally {
                        checkStateInvariants();
                    }
                }
                // not reachable
            } catch (Throwable t) {
                if (log.isTraceEnabled()) {
                    String message = (t != null && t.getMessage() != null) ? t.getMessage() : "null";
                    log.tracef("Encountered exception when calling sendRequest(): exception = %s (state = %s)", message, state);
                }
                // back to the start of the chain; decide what to do next.
                synchronized (lock) {
                    if (state == State.SENDING) {
                        // didn't make it to the end of the chain even... but we won't suppress the thrown exception
                        transition(State.SENT);
                    }
                    assert state == State.SENT || state == State.READY;
                    try {
                        // from here we can go to: FAILED, READY, or retry SENDING.
                        Supplier<? extends Throwable> pendingFailure = this.pendingFailure;
                        EJBReceiverInvocationContext.ResultProducer resultProducer = this.resultProducer;
                        if (resultProducer != null) {
                            // READY, even if we have a pending failure.
                            if (log.isTraceEnabled()) {
                                log.tracef("not retrying the invocation!: result producer = %d (state = %s)", resultProducer, state);
                            }
                            if (pendingFailure != null) {
                                addSuppressed(t);
                                addSuppressed(pendingFailure);
                                this.pendingFailure = null;
                            }
                            transition(State.READY);
                            return;
                        }
                        // FAILED, or retry SENDING.
                        if (! retryRequested || remainingRetries == 0) {
                            // nobody wants retry, or there are none left; go to FAILED
                            if (pendingFailure != null) {
                                addSuppressed(pendingFailure);
                            }
                            if (log.isTraceEnabled()) {
                                String message = (t != null && t.getMessage() != null) ? t.getMessage() : "null";
                                log.tracef("not retrying the invocation!: failure exception = %d (state = %s)", message, state);
                            }
                            if (t instanceof Exception) {
                                this.resultProducer = new EJBReceiverInvocationContext.ResultProducer.Failed((Exception) t);
                            } else {
                                this.resultProducer = new EJBReceiverInvocationContext.ResultProducer.Failed(new UndeclaredThrowableException(t));
                            }
                            this.pendingFailure = null;
                            transition(State.READY);
                            return;
                        }
                        if (log.isTraceEnabled()) {
                            log.tracef("retrying the invocation!: remaining retries = %d (state = %s)", remainingRetries, state);
                        }
                        // retry SENDING
                        if (pendingFailure != null) {
                            addSuppressed(pendingFailure);
                        }
                        setReceiver(null);
                        this.pendingFailure = null;
                        transition(State.SENDING);
                        retryRequested = false;
                        remainingRetries --;
                    } finally {
                        checkStateInvariants();
                    }
                }
                // record for later
                addSuppressed(t);
                // redo the loop
                //noinspection UnnecessaryContinue
                continue;
            }
        }
    }

    State checkState() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * Proceed with sending the request normally.
     *
     * @throws Exception if the request was not successfully sent
     */
    public void sendRequest() throws Exception {
        final Object lock = this.lock;
        Assert.assertNotHoldsLock(lock);
        final EJBClientInterceptorInformation[] chain = interceptorList.getInformation();
        synchronized (lock) {
            if (state != State.SENDING) {
                throw Logs.MAIN.sendRequestCalledDuringWrongPhase();
            }
        }
        final int idx = interceptorChainIndex ++;
        try {
            if (cancelRequested) {
                synchronized (lock) {
                    if (log.isTraceEnabled()) {
                        log.tracef("sendRequest: cancelling the invocation! (state = %s)", state);
                    }
                    transition(State.SENT);
                    resultReady(CANCELLED);
                    checkStateInvariants();
                }
            } else if (chain.length == idx) {
                // End of the chain processing; deliver to receiver or throw an exception.
                final URI destination = getDestination();
                final EJBReceiver receiver;
                try {
                    if (Logs.INVOCATION.isDebugEnabled()) {
                        Logs.INVOCATION.debugf("sendRequest: setting receiver, strong affinity = %s, weak affinity = %s, remote destination is: %s",
                                getLocator().getAffinity(), invocationHandler.getWeakAffinity(), destination);
                    }
                    receiver = getClientContext().resolveReceiver(destination, getLocator());
                } catch (Throwable t) {
                    synchronized (lock) {
                        if (log.isTraceEnabled()) {
                            String message = (t != null && t.getMessage() != null) ? t.getMessage() : "null";
                            String scheme = (destination != null && destination.getScheme() != null) ? destination.getScheme() : "no scheme";
                            log.tracef("sendRequest: resolveReceiver() with scheme %s got exception %s! (state = %s)", scheme, message, state);
                        }
                        if (state != State.SENT) {
                            transition(State.SENT);
                        }
                        checkStateInvariants();
                    }
                    throw t;
                }
                setReceiver(receiver);
                synchronized (lock) {
                    transition(State.SENT);
                    checkStateInvariants();
                }
                try {
                    if (Logs.INVOCATION.isDebugEnabled()) {
                        Logs.INVOCATION.debugf("sendRequest: calling processInvocation, receiver context = %s (state = %s)", receiverInvocationContext, state);
                    }
                    receiver.processInvocation(receiverInvocationContext);
                } catch (Throwable t) {
                    synchronized (lock) {
                        if (log.isTraceEnabled()) {
                            String message = (t != null && t.getMessage() != null) ? t.getMessage() : "null";
                            log.tracef("sendRequest: processInvocation got exception %s! (state = %s)", message, state);
                        }
                        if (state != State.SENT) {
                            transition(State.SENT);
                        }
                        checkStateInvariants();
                    }
                    throw t;
                }
            } else {
                try {
                    if (Logs.INVOCATION.isDebugEnabled()) {
                        Logs.INVOCATION.debugf("sendRequest: calling handleInvocation in interceptor[%s]: %s", idx, chain[idx].getInterceptorInstance());
                    }
                    chain[idx].getInterceptorInstance().handleInvocation(this);
                } catch (Throwable t) {
                    synchronized (lock) {
                        if (log.isTraceEnabled()) {
                            String message = (t != null && t.getMessage() != null) ? t.getMessage() : "null";
                            log.tracef("sendRequest: handleInvocation() got exception %s! (state = %s)", message, state);
                        }
                        if (state != State.SENT) {
                            transition(State.SENT);
                        }
                        checkStateInvariants();
                    }
                    throw t;
                }
                synchronized (lock) {
                    try {
                        if (state != State.SENT && state != State.READY) {
                            assert state == State.SENDING;
                            transition(State.SENT);
                            throw Logs.INVOCATION.requestNotSent();
                        }
                    } finally {
                        checkStateInvariants();
                    }
                }
            }
        } finally {
            interceptorChainIndex--;
        }
        // return to enclosing interceptor
        return;
    }

    /**
     * Get the invocation result from this request.  The result is not actually acquired unless all interceptors
     * call this method.  Should only be called from {@link EJBClientInterceptor#handleInvocationResult(EJBClientInvocationContext)}.
     *
     * @return the invocation result
     * @throws Exception if the invocation did not succeed
     */
    public Object getResult() throws Exception {
        return getResult(false);
    }

    /**
     * Get the invocation result (internal operation).
     *
     * @param retry {@code true} if the caller is the retry process, {@code false} for user call
     * @return the invocation result
     * @throws Exception if the invocation did not succeed
     */
    Object getResult(boolean retry) throws Exception {
        final EJBClientContext.InterceptorList list = getClientContext().getInterceptors(getViewClass(), getInvokedMethod());
        final EJBClientInterceptorInformation[] chain = list.getInformation();
        final EJBReceiverInvocationContext.ResultProducer resultProducer;
        Throwable fail = null;
        final int idx = this.interceptorChainIndex;
        final Object lock = this.lock;
        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("getResult(): calling getResult(%s)", retry);
        }
        synchronized (lock) {
            try {
                if (idx == 0) {
                    if (retry) {
                        assert state == State.CONSUMING;
                    } else {
                        while (state == State.CONSUMING) try {
                            checkStateInvariants();
                            try {
                                waiters++;
                                lock.wait();
                            } finally {
                                waiters--;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw Logs.MAIN.operationInterrupted();
                        }
                        if (state == State.DONE) {
                            Supplier<? extends Throwable> pendingFailure = this.pendingFailure;
                            if (pendingFailure != null) {
                                fail = pendingFailure.get();
                                if (fail == null) {
                                    if (log.isTraceEnabled()) {
                                        log.tracef("getResult(): no pending failure exception, returning cached result (state = %s)", state);
                                    }
                                    return cachedResult;
                                }
                            } else {
                                if (log.isTraceEnabled()) {
                                    log.tracef("getResult(): no pending failure, returning cached result (state = %s)", state);
                                }
                                return cachedResult;
                            }
                        } else if (state != State.READY) {
                            throw Logs.MAIN.getResultCalledDuringWrongPhase();
                        } else {
                            transition(State.CONSUMING);
                        }
                    }
                }
                resultProducer = this.resultProducer;
            } finally {
                checkStateInvariants();
            }
        }
        if (fail != null) try {
            if (log.isTraceEnabled()) {
                String message = fail.getMessage() != null ? fail.getMessage() : "null";
                log.tracef("getResult(): throwing pending failure exception %s (state = %s)", message, state);
            }
            throw fail;
        } catch (Exception | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
        this.interceptorChainIndex = idx + 1;
        try {
            final Object result;
            try {
                if (idx == chain.length) {
                    if (Logs.INVOCATION.isDebugEnabled()) {
                        String producerString = resultProducer != null ? resultProducer.toString() : "null";
                        Logs.INVOCATION.debugf("getResult: interceptor chain has completed, calling getResult() in result producer: %s", producerString);
                    }
                    result = resultProducer.getResult();
                } else {
                    if (Logs.INVOCATION.isDebugEnabled()) {
                        Logs.INVOCATION.debugf("getResult: calling handleInvocationResult in interceptor[%s]: %s", idx, chain[idx].getInterceptorInstance());
                    }
                    // recursive call
                    result = chain[idx].getInterceptorInstance().handleInvocationResult(this);
                }
                if (idx == 0) {
                    synchronized (lock) {
                        transition(State.DONE);
                        pendingFailure = null;
                        suppressedExceptions = null;
                        cachedResult = result;
                        this.resultProducer = null;
                        checkStateInvariants();
                    }
                }
                return result;
            } catch (Throwable t) {
                if (idx == 0) {
                    if (log.isTraceEnabled()) {
                       log.tracef("Encountered exception while calling getResult(): exception = %s (state = %s)", t.toString(), state);
                    }
                    synchronized (lock) {
                        // retry if we can
                        this.resultProducer = null;
                        List<Supplier<? extends Throwable>> suppressedExceptions = this.suppressedExceptions;
                        final int remainingRetries = this.remainingRetries;
                        final boolean retryRequested = this.retryRequested;
                        if (retryRequested && remainingRetries > 0) {
                            if (log.isTraceEnabled()) {
                                log.tracef("Will retry (requested = %s, remaining = %d) (state = %s)", retryRequested, remainingRetries, state);
                            }
                            if (suppressedExceptions == null) {
                                suppressedExceptions = this.suppressedExceptions = new ArrayList<>();
                            }
                            suppressedExceptions.add(() -> t);
                            this.remainingRetries --;
                            this.retryRequested = false;
                            this.cachedResult = null;
                            this.pendingFailure = null;
                            setReceiver(null);
                            transition(State.SENDING);
                            checkStateInvariants();
                        } else {
                            if (log.isTraceEnabled()) {
                                log.tracef("Will not retry (requested = %s, remaining = %d) (state = %s)", retryRequested, remainingRetries, state);
                            }
                            pendingFailure = () -> t;
                            if (suppressedExceptions != null) {
                                this.suppressedExceptions = null;
                                for (Supplier<? extends Throwable> supplier : suppressedExceptions) {
                                    try {
                                        t.addSuppressed(supplier.get());
                                    } catch (Throwable ignored) {}
                                }
                            }
                            transition(State.DONE);
                            checkStateInvariants();
                        }
                    }
                }
                throw t;
            } finally {
                if (idx == 0) {
                    if (Logs.INVOCATION.isDebugEnabled()) {
                        Logs.INVOCATION.debugf("getResult(): invocation returned, relocating EJB: strong affinity = %s, weak affinity = %s", getLocator().getAffinity(), getWeakAffinity());
                    }
                    // relocate the EJB
                    invocationHandler.setWeakAffinity(getWeakAffinity());
                    invocationHandler.setStrongAffinity(getLocator().getAffinity());
                }
            }
        } finally {
            interceptorChainIndex = idx;
        }
    }

    /**
     * Discard the result from this request.  Should only be called from {@link EJBClientInterceptor#handleInvocationResult(EJBClientInvocationContext)}.
     *
     * @throws IllegalStateException if there is no result to discard
     */
    public void discardResult() throws IllegalStateException {
        resultReady(EJBClientInvocationContext.ONE_WAY);
    }

    void resultReady(EJBReceiverInvocationContext.ResultProducer resultProducer) {
        Assert.checkNotNullParam("resultProducer", resultProducer);
        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("Result is ready for %s: result producer: %s", this, resultProducer);
        }
        synchronized (lock) {
            if (state.isWaiting() && this.resultProducer == null) {
                this.resultProducer = resultProducer;
                if (state == State.WAITING || state == State.SENT) {
                    transition(State.READY);
                }
                checkStateInvariants();
                if (log.isTraceEnabled()) {
                    log.tracef("Result is ready for %s: result producer: %s (state = %s)", this, resultProducer, state);
                }
                return;
            }
            checkStateInvariants();
            if (log.isTraceEnabled()) {
                log.tracef("Result discarded for %s: result producer: %s (state = %s)", this, resultProducer, state);
            }
        }
        // for whatever reason, we don't care
        resultProducer.discardResult();
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
     * Get the remaining invocation time in the given unit.  If there is no invocation timeout, {@link Long#MAX_VALUE} is
     * always returned.  If the invocation time has elapsed, 0 is returned.
     *
     * @param timeUnit the time unit (must not be {@code null})
     * @return the invocation's remaining time in the provided unit
     */
    public long getRemainingInvocationTime(TimeUnit timeUnit) {
        Assert.checkNotNullParam("timeUnit", timeUnit);
        final long timeout = this.timeout;
        if (timeout <= 0L) {
            return Long.MAX_VALUE;
        }
        return max(0L, timeUnit.convert(timeout - (System.nanoTime() - startTime) / 1_000_000L, TimeUnit.MILLISECONDS));
    }

    @Override
    public <T> void setLocator(EJBLocator<T> locator) {
        super.setLocator(locator);

        Affinity affinity = locator.getAffinity();
        if (affinity instanceof ClusterAffinity) {
            ClusterAffinityInterest interest = invocationHandler.getAttachment(ClusterAffinityInterest.KEY);
            if (interest != null) {
                interest.notifyAssignment((ClusterAffinity)affinity);
            }
        }
    }

    @NotNull
    Discovery getDiscovery() {
        return discoveryContext;
    }

    Future<?> getFutureResponse() {
        return new FutureResponse();
    }

    void proceedAsynchronously() {
        if (getInvokedMethod().getReturnType() == void.class) {
            resultReady(EJBReceiverInvocationContext.ResultProducer.NULL);
        }
    }

    /**
     * Transition to a new state, guarded by assertions.
     *
     * @param newState the state to transition to (must not be {@code null})
     */
    private void transition(State newState) {
        final Object lock = this.lock;
        Assert.assertHoldsLock(lock);
        final State oldState = this.state;
        if (oldState == newState) {
            return;
        }
        if (log.isTraceEnabled()) {
            StackTraceElement caller = (new Exception()).getStackTrace()[1];
            if (log.isTraceEnabled()) {
                log.tracef("Transitioning %s from %s to %s (%s)", this, oldState, newState, caller);
            }
        }
        switch (oldState) {
            case SENDING: {
                assert newState == State.SENT;
                break;
            }
            case SENT: {
                assert newState == State.READY || newState == State.SENDING || newState == State.DONE || newState == State.WAITING || newState == State.CONSUMING;
                break;
            }
            case WAITING: {
                assert newState == State.DONE || newState == State.READY || newState == State.CONSUMING;
                break;
            }
            case READY: {
                assert newState == State.CONSUMING;
                break;
            }
            case CONSUMING: {
                assert newState == State.SENDING || newState == State.DONE || newState == State.READY;
                break;
            }
            default: {
                assert false;
                break;
            }
        }
        switch (newState) {
            case READY:
            case DONE: {
                this.remainingRetries = 0;
                // fall thru
            }
            case WAITING:{
                if(waiters > 0) {
                    lock.notifyAll();
                }
                break;
            }
        }
        // everything is OK
        this.state = newState;
    }

    /**
     * Check the invariants of the current state with assertions before the caller releases the lock.
     */
    private void checkStateInvariants() {
        final Object lock = this.lock;
        Assert.assertHoldsLock(lock);
        final State state = this.state;
        switch (state) {
            case SENDING: {
                assert resultProducer == null && cachedResult == null && getReceiver() == null;
                break;
            }
            case SENT: {
                assert cachedResult == null;
                break;
            }
            case WAITING: {
                assert resultProducer == null && pendingFailure == null && cachedResult == null;
                break;
            }
            case READY: {
                assert resultProducer != null && pendingFailure == null && cachedResult == null && remainingRetries == 0;
                break;
            }
            case CONSUMING: {
                assert resultProducer != null && pendingFailure == null && cachedResult == null;
                break;
            }
            case DONE: {
                assert resultProducer == null && (pendingFailure == null || pendingFailure != null && cachedResult == null) && remainingRetries == 0;
                break;
            }
            default: {
                assert false;
                break;
            }
        }
    }

    /**
     * Wait to determine whether this invocation was cancelled.
     *
     * @return {@code true} if the invocation was cancelled; {@code false} if it completed or failed or the thread was
     *  interrupted
     */
    public boolean awaitCancellationResult() {
        final Object lock = this.lock;
        Assert.assertNotHoldsLock(lock);
        synchronized (lock) {
            if (Logs.INVOCATION.isDebugEnabled()) {
               Logs.INVOCATION.debugf("Invocation is awaiting cancellation result (state = %s)", state);
            }
            for (;;) {
                if (resultProducer == CANCELLED) {
                    return true;
                } else if (! state.isWaiting()) {
                    return false;
                }
                try {
                    checkStateInvariants();
                    try {
                        waiters++;
                        lock.wait();
                    } finally {
                        waiters--;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    Object awaitResponse() throws Exception {
        Assert.assertNotHoldsLock(lock);
        boolean intr = false, timedOut = false;
        try {
            final Object lock = this.lock;
            final long timeout = this.timeout;
            synchronized (lock) {
                if (Logs.INVOCATION.isDebugEnabled()) {
                   Logs.INVOCATION.debugf("awaitResponse(): invocation is awaiting response (state = %s)", state);
                }
                try {
                    out: for (;;) {
                        switch (state) {
                            case SENDING:
                            case SENT:
                            case CONSUMING:
                            case WAITING: {
                                if (timeout <= 0 || timedOut) {
                                    // no timeout; lighter code path
                                    if (log.isTraceEnabled()) {
                                        log.tracef("awaitResponse(): invocation waiting with no timeout (state = %s)", state);
                                    }
                                    try {
                                        checkStateInvariants();
                                        try {
                                            waiters++;
                                            lock.wait();
                                        } finally {
                                            waiters--;
                                        }
                                    } catch (InterruptedException e) {
                                        intr = true;
                                    }
                                } else {
                                    // timeout in ms, elapsed time in nanosecs
                                    long remaining = max(0L, timeout * 1_000_000L - max(0L, System.nanoTime() - startTime));
                                    if (log.isTraceEnabled()) {
                                        log.tracef("awaitResponse(): invocation waiting with time remaining %s (state = %s)", remaining, state);
                                    }
                                    if (remaining == 0L) {
                                        // timed out
                                        if (log.isTraceEnabled()) {
                                            log.tracef("awaitResponse(): invocation has timed out (state = %s)", state);
                                        }
                                        timedOut = true;
                                        this.timeout = 0;
                                        if (state == State.CONSUMING) {
                                            addSuppressed(new TimeoutException("No invocation response received in " + timeout + " milliseconds"));
                                            transition(State.READY);
                                        } else {
                                            resultReady(new ThrowableResult(() -> new TimeoutException("No invocation response received in " + timeout + " milliseconds")));
                                        }
                                    } else try {
                                        checkStateInvariants();
                                        try {
                                            waiters++;
                                            lock.wait(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                                        } finally {
                                            waiters--;
                                        }
                                    } catch (InterruptedException e) {
                                        intr = true;
                                    }
                                }
                                break;
                            }
                            case READY: {
                                // we have to get the result, so break out of here.
                                if (log.isTraceEnabled()) {
                                    log.tracef("awaitResponse(): response is ready (state = %s)", state);
                                }
                                checkStateInvariants();
                                break out;
                            }
                            case DONE: {
                                checkStateInvariants();
                                if (pendingFailure != null) {
                                    if (log.isTraceEnabled()) {
                                        log.tracef("awaitResponse(): response has pending failure (state = %s)", state);
                                    }
                                    try {
                                        throw pendingFailure.get();
                                    } catch (Error | Exception e) {
                                        throw e;
                                    } catch (Throwable t) {
                                        throw new UndeclaredThrowableException(t);
                                    }
                                }
                                if (log.isTraceEnabled()) {
                                    log.tracef("awaitResponse() returning cached result (state = %s)", state);
                                }
                                return cachedResult;
                            }
                            default: {
                                throw new IllegalStateException();
                            }
                        }
                    }
                } finally {
                    blockingCaller = false;
                }
            }
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("awaitResponse() calling getResult() (state = %s)", state);
            }
            return getResult();
        } finally {
            if (intr) Thread.currentThread().interrupt();
            if (timedOut) {
                if (Logs.INVOCATION.isDebugEnabled()) {
                    Logs.INVOCATION.tracef("Invocation has timed out, cancelling (state = %s)", state);
                }
                final EJBReceiver receiver = getReceiver();
                if (receiver != null) receiver.cancelInvocation(receiverInvocationContext, true);
            }
        }
    }

    void setDiscardResult() {
        final Object lock = this.lock;
        assert !holdsLock(lock);
        final EJBReceiverInvocationContext.ResultProducer resultProducer;
        synchronized (lock) {
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("Invocation is discarding result (state = %s)", state);
            }
            resultProducer = this.resultProducer;
            this.resultProducer = EJBReceiverInvocationContext.ResultProducer.NULL;
            // result is waiting, discard it
            if (state == State.WAITING) {
                transition(State.DONE);
            }
            // fall out of the lock to discard the old result (if any)
            checkStateInvariants();
        }
        if (resultProducer != null) resultProducer.discardResult();
    }

    void cancelled() {
        resultReady(CANCELLED);
    }

    void failed(Exception exception, Executor retryExecutor) {
        final Object lock = this.lock;
        synchronized (lock) {
            if (Logs.INVOCATION.isDebugEnabled()) {
               String message = exception.getMessage() != null ? exception.getMessage() : "null";
               Logs.INVOCATION.debugf("failed(): invocation marked as failed with exception %s (state = %s)", message, state);
            }
            switch (state) {
                case CONSUMING:
                case DONE: {
                    if (log.isTraceEnabled()) {
                       log.tracef("failed(): response is complete, return with no retry (state = %s)", state);
                    }
                    // ignore
                    return;
                }
                case SENDING: {
                    throw new IllegalStateException();
                }
                case SENT: {
                    final Supplier<? extends Throwable> pendingFailure = this.pendingFailure;
                    if (pendingFailure != null) {
                        if (log.isTraceEnabled()) {
                            log.tracef("failed(): response has pending failure %s, return with no retry (state = %s)", pendingFailure.get(), state);
                        }
                        addSuppressed(pendingFailure);
                    }
                    this.pendingFailure = () -> exception;
                    return;
                }
                case READY: {
                    addSuppressed(exception);
                    return;
                }
                case WAITING: {
                    // moving to CONSUMING, which requires a resultProducer.
                    this.resultProducer = new ThrowableResult(() -> exception);
                    this.pendingFailure = null;
                    if (log.isTraceEnabled()) {
                        log.tracef("failed(): response has result producer %s, check for possible retry (state = %s)", state);
                    }
                    // process result immediately, possibly retrying at the end
                    // retry SENDING via CONSUMING
                    transition(State.CONSUMING);
                    // redo the request
                    checkStateInvariants();
                    break;
                }
                default: {
                    throw Assert.impossibleSwitchCase(state);
                }
            }
        }
        retryExecutor.execute(this::retryOperation);
        return;
    }

    void retryOperation() {
        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("retryOperation called (state = %s)", state);
        }
        try {
            getResult(true);
        } catch (Throwable t) {
            final boolean retry;
            synchronized (lock) {
                retry = state == State.SENDING;
            }
            if (log.isTraceEnabled()) {
              String message = (t != null && t.getMessage() != null) ? t.getMessage() : "null";
              log.tracef("retryOperation() got exception %s when calling getResult() (state = %s)", message, state);
            }
            if (retry) sendRequestInitial();
        }
    }

    final class FutureResponse implements Future<Object> {

        FutureResponse() {
        }

        public boolean cancel(final boolean mayInterruptIfRunning) {
            final Object lock = EJBClientInvocationContext.this.lock;
            assert !holdsLock(lock);
            synchronized (lock) {
                if (state == State.DONE) {
                    // cannot cancel now; also resultProducer is gone
                    return pendingFailure == CANCELLED_PRODUCER;
                } else if (! state.isWaiting()) {
                    // cannot cancel now
                    return resultProducer == CANCELLED;
                } else {
                    if (resultProducer == CANCELLED) {
                        return true;
                    }
                    // at this point the task is running and we are allowed to interrupt it. So set
                    // the cancel request flag and a fall out to send the request
                    cancelRequested = true;
                }
            }
            final EJBReceiver receiver = getReceiver();
            final boolean result = receiver != null && receiver.cancelInvocation(receiverInvocationContext, mayInterruptIfRunning);
            if (! result) {
                synchronized (lock) {
                    if (resultProducer == CANCELLED || state == State.DONE && pendingFailure == CANCELLED_PRODUCER) {
                        return true;
                    }
                }
            }
            return result;
        }

        public boolean isCancelled() {
            final Object lock = EJBClientInvocationContext.this.lock;
            assert !holdsLock(lock);
            synchronized (lock) {
                return state == State.DONE ? pendingFailure == CANCELLED_PRODUCER : resultProducer == CANCELLED;
            }
        }

        public boolean isDone() {
            final Object lock = EJBClientInvocationContext.this.lock;
            assert !holdsLock(lock);
            synchronized (lock) {
                if (state == State.CONSUMING) {
                    return retryRequested && remainingRetries > 0 && resultProducer instanceof ThrowableResult;
                } else {
                    // TODO: we should also calculate whether the invocation timed out
                    return ! state.isWaiting();
                }
            }
        }

        public Object get() throws InterruptedException, ExecutionException {
            try {
                return awaitResponse();
            } catch (ExecutionException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw log.remoteInvFailed(e);
            }
        }

        public Object get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            final Object lock = EJBClientInvocationContext.this.lock;
            assert !holdsLock(lock);
            final long handlerInvTimeout = invocationHandler.getInvocationTimeout();
            final long invocationTimeout = handlerInvTimeout != -1 ? handlerInvTimeout : getClientContext().getInvocationTimeout();
            final long ourStart = System.nanoTime();
            // invocationTimeout in ms, elapsed time in nanosecs
            if (unit.convert(max(0L, invocationTimeout * 1_000_000L - max(0L, ourStart - startTime)), TimeUnit.NANOSECONDS) <= timeout) {
                // the invocation will expire before we finish
                return get();
            }
            long remaining = unit.toNanos(timeout);
            synchronized (lock) {
                out: for (;;) {
                    switch (state) {
                        case SENDING:
                        case SENT:
                        case CONSUMING:
                        case WAITING: {
                            checkStateInvariants();
                            if (remaining <= 0L) {
                                throw log.timedOut();
                            }
                            try {
                                waiters++;
                                lock.wait(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                            } finally {
                                waiters--;
                            }
                            remaining = unit.toNanos(timeout) - (System.nanoTime() - ourStart);
                            break;
                        }
                        case READY: {
                            // we have to get the result, so break out of here.
                            checkStateInvariants();
                            break out;
                        }
                        case DONE: {
                            checkStateInvariants();
                            if (pendingFailure != null) {
                                throw log.remoteInvFailed(pendingFailure.get());
                            }
                            return cachedResult;
                        }
                        default:
                            throw new IllegalStateException();
                    }
                }
            }
            // we've gotten the result
            try {
                return getResult();
            } catch (ExecutionException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw log.remoteInvFailed(e);
            }
        }
    }

    static final class ThrowableResult implements EJBReceiverInvocationContext.ResultProducer {
        private final Supplier<? extends Throwable> pendingFailure;

        ThrowableResult(final Supplier<? extends Throwable> pendingFailure) {
            this.pendingFailure = pendingFailure;
        }

        public Object getResult() throws Exception {
            try {
                throw pendingFailure.get();
            } catch (Exception | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t);
            }
        }

        public void discardResult() {
            // ignored
        }
    }

    static final Supplier<Throwable> CANCELLED_PRODUCER = Logs.INVOCATION::requestCancelled;

    static final ThrowableResult CANCELLED = new ThrowableResult(CANCELLED_PRODUCER);

    static final ThrowableResult ONE_WAY = new ThrowableResult(Logs.INVOCATION::oneWayInvocation);
}
