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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Thread.holdsLock;

/**
 * An invocation context for EJB invocations from an EJB client
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Jaikiran Pai
 */
public final class EJBClientInvocationContext extends Attachable {

    private static final Logs log = Logs.MAIN;

    public static final String PRIVATE_ATTACHMENTS_KEY = "org.jboss.ejb.client.invocation.attachments";

    // Contextual stuff
    private final EJBInvocationHandler<?> invocationHandler;
    private final EJBClientContext ejbClientContext;

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
    private Map<String, Object> contextData;
    private EJBReceiverInvocationContext receiverInvocationContext;
    // nodes to which invocation shouldn't be forwarded to. This is typically for cases
    // where a invocation failed on a specific node, for this invocation context, due to the deployment
    // being undeployed after the node was selected for handling this invocation but before the client
    // could get a undeploy notification for it
    private Set<String> excludedNodes = new HashSet<String>();

    // Interceptor state
    private final EJBClientInterceptor[] interceptorChain;
    private int interceptorChainIndex;
    private boolean resultDone;

    EJBClientInvocationContext(final EJBInvocationHandler<?> invocationHandler, final EJBClientContext ejbClientContext, final Object invokedProxy, final Method invokedMethod, final Object[] parameters) {
        this.invocationHandler = invocationHandler;
        this.ejbClientContext = ejbClientContext;
        this.invokedProxy = invokedProxy;
        this.invokedMethod = invokedMethod;
        this.parameters = parameters;
        interceptorChain = (EJBClientInterceptor[]) ejbClientContext.getInterceptorChain();
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

    EJBInvocationHandler<?> getInvocationHandler() {
        return invocationHandler;
    }

    /**
     * Get the EJB client context associated with this invocation.
     *
     * @return the EJB client context
     */
    public EJBClientContext getClientContext() {
        return ejbClientContext;
    }

    /**
     * Get the context data.  This same data will be made available verbatim to
     * server-side interceptors via the {@code InvocationContext.getContextData()} method, and thus
     * can be used to pass data from the client to the server (as long as all map values are
     * {@link Serializable}).
     *
     * @return the context data
     */
    public Map<String, Object> getContextData() {
        if (contextData == null) {
            return contextData = new LinkedHashMap<String, Object>();
        } else {
            return contextData;
        }
    }

    /**
     * Get the locator for the invocation target.
     *
     * @return the locator
     */
    public EJBLocator<?> getLocator() {
        return invocationHandler.getLocator();
    }

    /**
     * Proceed with sending the request normally.
     *
     * @throws Exception if the request was not successfully sent
     */
    public void sendRequest() throws Exception {
        final int idx = this.interceptorChainIndex++;
        final EJBClientInterceptor[] chain = interceptorChain;
        if (idx > chain.length) {
            throw Logs.MAIN.sendRequestCalledDuringWrongPhase();
        }
        if (chain.length == idx) {
            final EJBReceiverInvocationContext context = receiverInvocationContext;
            if (context == null) {
                throw Logs.MAIN.noReceiverAssociatedWithInvocation();
            }
            context.getEjbReceiverContext().getReceiver().processInvocation(this, context);
        } else {
            chain[idx].handleInvocation(this);
        }
    }

    /**
     * Retry a request which was previously sent but probably failed with a error response.
     * Note that this method will throw an {@link IllegalStateException} if a previous request wasn't completed
     * for this invocation context
     *
     * @throws Exception if the retry request was not successfully sent
     */
    void retryRequest() throws Exception {
        // if a previous request wasn't yet done, then this isn't really a "retry",
        // so we error out
        final int idx = this.interceptorChainIndex;
        final EJBClientInterceptor[] chain = interceptorChain;
        if (idx <= chain.length) {
            throw Logs.MAIN.cannotRetryRequest();
        }
        // reset the interceptor index to the beginning of the chain
        this.interceptorChainIndex = 0;
        // reset the previously set receiver invocation context, since a possibly new receiver
        // will be selected during this retry
        this.receiverInvocationContext = null;
        // send request
        this.sendRequest();
    }

    /**
     * Returns a set of nodes which shouldn't be used for handling the invocation represented by this
     * invocation context. If there are no such nodes, then this method returns an empty set.
     * <p/>
     * Typically a node is marked as excluded for a {@link EJBClientInvocationContext invocation context}
     * if it failed to handle that specific {@link EJBClientInvocationContext invocation}. This can happen,
     * for example, if a node was selected for invocation handling and was later incapable of handling it
     * due to the deployment being un-deployed. The invocation in such cases will typically be retried by
     * marking that node as excluded and selecting a different node (if any) for the retried invocation.
     *
     * @return
     */
    Set<String> getExcludedNodes() {
        return Collections.unmodifiableSet(this.excludedNodes);
    }

    /**
     * Marks the passed <code>nodeName</code> as excluded so that the invocation represented by
     * this {@link EJBClientInvocationContext invocation context} will leave it out while selecting a suitable node for
     * handling the invocation
     *
     * @param nodeName The name of the node to be excluded
     */
    void markNodeAsExcluded(final String nodeName) {
        if (nodeName == null || nodeName.trim().isEmpty()) {
            return;
        }
        this.excludedNodes.add(nodeName);
    }

    void setReceiverInvocationContext(EJBReceiverInvocationContext context) {
        receiverInvocationContext = context;
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
        final EJBClientInterceptor[] chain = interceptorChain;
        if (idx == 0) try {
            return chain[idx].handleInvocationResult(this);
        } finally {
            resultDone = true;
            final Affinity weakAffinity = getAttachment(AttachmentKeys.WEAK_AFFINITY);
            if (weakAffinity != null) {
                invocationHandler.setWeakAffinity(weakAffinity);
            }
        }
        else try {
            if (chain.length == idx) {
                if(System.getSecurityManager() == null) {
                    return resultProducer.getResult();
                } else {
                    try {
                        return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                            @Override
                            public Object run() throws Exception {
                                return resultProducer.getResult();
                            }
                        });
                    } catch (PrivilegedActionException e) {
                        throw e.getException();
                    }
                }
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
            throw Logs.MAIN.discardResultCalledDuringWrongPhase();
        }

        resultProducer.discardResult();
    }

    void resultReady(EJBReceiverInvocationContext.ResultProducer resultProducer) {
        synchronized (lock) {
            switch (state) {
                case WAITING:
                case CANCEL_REQ: {
                    this.resultProducer = resultProducer;
                    interceptorChainIndex = 0;
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
    protected EJBReceiver getReceiver() {
        final EJBReceiverInvocationContext context = receiverInvocationContext;
        if (context == null) {
            throw Logs.MAIN.noReceiverAssociatedWithInvocation();
        }
        return context.getEjbReceiverContext().getReceiver();
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
        return invocationHandler.getLocator().getViewType();
    }

    Future<?> getFutureResponse() {
        return new FutureResponse();
    }

    static final Object PROCEED_ASYNC = new Object();

    void proceedAsynchronously() {
        assert !holdsLock(lock);
        synchronized (lock) {
            if (asyncState == AsyncState.SYNCHRONOUS) {
                asyncState = AsyncState.ASYNCHRONOUS;
                lock.notifyAll();
            }
        }
    }

    Object awaitResponse() throws Exception {
        assert !holdsLock(lock);
        boolean intr = false;
        final EJBClientConfiguration ejbClientConfiguration = this.ejbClientContext.getEJBClientConfiguration();
        final long invocationTimeout = ejbClientConfiguration == null ? 0 : ejbClientConfiguration.getInvocationTimeout();
        try {
            synchronized (lock) {
                if (asyncState == AsyncState.ASYNCHRONOUS) {
                    return PROCEED_ASYNC;
                } else if (asyncState == AsyncState.ONE_WAY) {
                    throw log.oneWayInvocation();
                }
                long remainingWaitTimeout = invocationTimeout;
                long waitStartTime = System.currentTimeMillis();
                while (state == State.WAITING) {
                    try {
                        // if no invocation timeout is configured, then we wait indefinitely
                        if (invocationTimeout <= 0) {
                            lock.wait();
                        } else {
                            waitStartTime = System.currentTimeMillis();
                            // we wait for a specific amount of time
                            lock.wait(remainingWaitTimeout);
                        }
                    } catch (InterruptedException e) {
                        intr = true;
                        // if there was a invocation timeout configured and the thread was interrupted
                        // then figure out how long we waited and what remaining time we should wait for
                        // if the result hasn't yet arrived
                        if (invocationTimeout > 0) {
                            final long timeWaitedFor = System.currentTimeMillis() - waitStartTime;
                            // we already waited enough, so setup a result producer which will
                            // let the client know that the invocation timed out
                            if (timeWaitedFor >= remainingWaitTimeout) {
                                // setup a invocation timeout result producer
                                this.resultReady(new InvocationTimeoutResultProducer(invocationTimeout));
                                break;
                            } else {
                                remainingWaitTimeout = remainingWaitTimeout - timeWaitedFor;
                            }
                        }
                        continue;
                    }
                    if (asyncState == AsyncState.ASYNCHRONOUS) {
                        // It's an asynchronous invocation; proceed asynchronously.
                        return PROCEED_ASYNC;
                    } else if (asyncState == AsyncState.ONE_WAY) {
                        throw log.oneWayInvocation();
                    }
                    // If the state is still waiting and the invocation timeout was specified,
                    // then it indicates that the Object.wait(timeout) returned due to a timeout.
                    if (state == State.WAITING && invocationTimeout > 0) {
                        // setup a invocation timeout result producer
                        this.resultReady(new InvocationTimeoutResultProducer(invocationTimeout));
                        break;
                    }
                }
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
            if (asyncState != AsyncState.ONE_WAY) {
                asyncState = AsyncState.ONE_WAY;
                lock.notifyAll();
            }
            if (state == State.DONE) {
                return;
            }
            // result is waiting, discard it
            state = State.DISCARDED;
            resultProducer = this.resultProducer;
            lock.notifyAll();
            // fall out of the lock to discard the result
        }
        // If task thread invoked #resultReady() method before current thread invokes this methods,
        //  then #state in #resultReady() was WAITING. Therefore resultProducer.discardResult() in
        //  #resultReady() was not invoked.
        // In the reverse of the pattern, resultProducer is null.
        if(resultProducer != null) resultProducer.discardResult();
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

    void failed(Throwable exception) {
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

        public boolean cancel(final boolean mayInterruptIfRunning) {
            assert !holdsLock(lock);
            synchronized (lock) {
                if (state != State.WAITING) {
                    return false;
                }
                // if we aren't allowed to interrupt a running task, then skip the cancellation
                if (!mayInterruptIfRunning) {
                    return false;
                }
                // at this point the task is running and we are allowed to interrupt it. So issue
                // a cancel request and change the current state
                state = State.CANCEL_REQ;
            }
            return getReceiver().cancelInvocation(EJBClientInvocationContext.this, receiverInvocationContext);
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
                        lock.wait(millis);
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
    }

    protected void finalize() throws Throwable {
        assert !holdsLock(lock);
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
