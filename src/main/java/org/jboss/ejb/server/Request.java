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

package org.jboss.ejb.server;

import java.net.SocketAddress;
import java.util.concurrent.Executor;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.SessionID;
import org.wildfly.common.Assert;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * The base type of any EJB server request.  This type is implemented by protocol implementations and consumed by
 * EJB invocation servers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Request {
    /**
     * Get the request executor.  This is an executor which is associated with the transport provider which may be
     * used to execute requests.
     *
     * @return the request executor
     */
    Executor getRequestExecutor();

    /**
     * Get the peer address that the request was received from, if known.
     *
     * @return the peer address, or {@code null} if it is not known
     */
    default SocketAddress getPeerAddress() {
        return null;
    }

    /**
     * Get the local address that the request was received to, if known.
     *
     * @return the local address, or {@code null} if it is not known
     */
    default SocketAddress getLocalAddress() {
        return null;
    }

    /**
     * Get the protocol of this request.
     *
     * @return the protocol of this request (must not be {@code null})
     */
    String getProtocol();

    /**
     * Determine if this request is blocking a local thread.
     *
     * @return {@code true} if the request is blocking the caller thread, {@code false} otherwise
     */
    boolean isBlockingCaller();

    /**
     * Get the identifier of the target EJB.
     *
     * @return the identifier of the target EJB (must not be {@code null})
     */
    @NotNull
    EJBIdentifier getEJBIdentifier();

    /**
     * Get the security identity that is associated with this invocation.
     *
     * @return the security identity, or {@code null} if the connection is not bound to a security domain
     */
    SecurityIdentity getSecurityIdentity();

    /**
     * Write a message indicating that an exception was thrown by the operation.
     *
     * @param exception the exception that was thrown (must not be {@code null})
     */
    void writeException(@NotNull Exception exception);

    /**
     * Write a message indicating that the EJB is not found on this server.  The request should be abandoned after
     * invoking this method.
     */
    void writeNoSuchEJB();

    /**
     * Write a message indicating that the EJB exists but the locator does not refer to a remote view.  The request
     * should be abandoned after invoking this method.
     */
    void writeWrongViewType();

    /**
     * Write a response indicating that the request was successfully cancelled.
     */
    void writeCancelResponse();

    /**
     * Write a message indicating that given EJB is not actually stateful.  The request should be abandoned after
     * invoking this method.
     */
    void writeNotStateful();

    /**
     * Attempt to convert the current invocation into a stateful invocation.  For session creation requests, this method
     * <em>must</em> be called.  For regular method invocations, this method <em>may</em> be called if the invoked EJB
     * is stateful but the locator is stateless, in order to auto-create the session.
     *
     * @param sessionId the new session ID (must not be {@code null})
     * @throws IllegalArgumentException if the current invocation cannot be converted to a stateful invocation because
     *  it is already stateful or the target EJB is not a stateful EJB
     * @throws IllegalStateException if the invocation was already converted to be stateful with a different session ID
     */
    void convertToStateful(@NotNull SessionID sessionId) throws IllegalArgumentException, IllegalStateException;

    /**
     * Hint to the implementation that the strong affinity of the client proxy should be updated if possible.  Not
     * all transports support all affinity types; as this is only a hint, the transport is free to ignore these calls.
     *
     * @param affinity the affinity to set (must not be {@code null})
     */
    default void updateStrongAffinity(@NotNull Affinity affinity) {
        Assert.checkNotNullParam("affinity", affinity);
    }

    /**
     * Hint to the implementation that the weak affinity of the client proxy should be updated if possible.  Not
     * all transports support all affinity types; as this is only a hint, the transport is free to ignore these calls.
     *
     * @param affinity the affinity to set (must not be {@code null})
     */
    default void updateWeakAffinity(@NotNull Affinity affinity) {
        Assert.checkNotNullParam("affinity", affinity);
    }

    /**
     * Get the provider interface associated with the request.
     *
     * @param providerInterfaceType the provider interface type class (must not be {@code null})
     * @param <C> the provider interface type
     * @return the provider interface, or {@code null} if it is not known
     */
    default <C> C getProviderInterface(Class<C> providerInterfaceType) {
        return null;
    }
}
