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

import java.net.InetSocketAddress;
import java.net.URI;

import org.wildfly.common.Assert;

/**
 * A receiver for EJB invocations.  Receivers can be associated with one or more client contexts.  This interface is
 * implemented by providers for EJB invocation services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class EJBReceiver extends Attachable {

    /**
     * Construct a new instance.
     */
    protected EJBReceiver() {
    }

    /**
     * Process the invocation.  Implementations of this method should always execute the operation asynchronously.  The
     * operation result should be passed in to the receiver invocation context.  To ensure ideal GC behavior, the
     * receiver should discard any reference to the invocation context(s) once the result producer has been set.
     *
     * @param receiverContext         The EJB receiver invocation context
     * @throws Exception if the operation throws an exception
     */
    protected abstract void processInvocation(EJBReceiverInvocationContext receiverContext) throws Exception;

    /**
     * Attempt to cancel an invocation.  Implementations should make a reasonable effort to determine whether
     * the operation was actually cancelled; however it is permissible to fall back to returning {@code false} if
     * it cannot be discovered.
     *
     * @param receiverContext         the EJB receiver invocation context
     * @param cancelIfRunning {@code true} to request that the cancellation proceed even if the method is running
     * @return {@code true} if the operation was definitely cancelled immediately, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    protected boolean cancelInvocation(EJBReceiverInvocationContext receiverContext, boolean cancelIfRunning) {
        return false;
    }

    /**
     * Creates a session for a stateful session bean represented by the passed app name, module name, distinct name
     * and bean name combination. Returns a {@link StatefulEJBLocator} representing the newly created session.  The
     * returned locator should have the same view type as the requested locator.
     *
     * @param receiverContext the EJB receiver session creation context
     * @return the session ID for the newly opened session
     * @throws IllegalArgumentException if the session creation request is made for a bean which is <i>not</i> a
     * stateful session bean
     */
    protected SessionID createSession(final EJBReceiverSessionCreationContext receiverContext) throws Exception {
        return createSession$$bridge(receiverContext).getSessionId();
    }

    /**
     * @deprecated Compatibility bridge, remove at Final.
     */
    protected StatefulEJBLocator<?> createSession$$bridge(final EJBReceiverSessionCreationContext receiverContext) throws Exception {
        throw Assert.unsupported();
    }

    /**
     * Query the expected or actual source IP address configured for the given target URI.
     *
     * @param destination the supported URI of the peer (not {@code null})
     * @return the socket address, or {@code null} if none is known
     */
    protected InetSocketAddress getSourceAddress(final InetSocketAddress destination) {
        return null;
    }

    /**
     * Determine if the given target URI is "connected".  Connectionless or connect-per-request protocols can inherit
     * the default, which always returns {@code true}.
     *
     * @param uri the supported URI of the peer (not {@code null})
     * @return {@code true} if the peer is readily available, {@code false} otherwise
     */
    protected boolean isConnected(final URI uri) {
        return true;
    }
}
