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

import org.jboss.ejb.client.annotation.ClientInterceptorPriority;

/**
 * An EJB client interceptor, possibly protocol-specific.  Client interceptors should not store any state locally since
 * they are shared between all threads.
 * <p>
 * Interceptors are generally applied in priority order.  Priority is determined by configuration or by the presence of
 * an {@link ClientInterceptorPriority} annotation on the interceptor class.  If priorities are equal, then the
 * following configuration order applies:
 * <ul>
 *     <li>Annotation-declared method-level interceptors</li>
 *     <li>Annotation-declared class-level interceptors</li>
 *     <li>Method-level configuration-declared interceptors</li>
 *     <li>Class-level configuration-declared interceptors</li>
 *     <li>Global configuration-declared interceptors</li>
 *     <li>Interceptors found from class path</li>
 *     <li>Globally installed default interceptors</li>
 * </ul>
 * Interceptors in the same configuration source are called in the order they were declared.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface EJBClientInterceptor {

    /**
     * Handle the invocation.  Implementations may short-circuit the invocation by throwing an exception.  This method
     * should process any per-interceptor state and call {@link EJBClientInvocationContext#sendRequest()}.
     *
     * @param context the invocation context
     * @throws Exception if an invocation error occurs
     */
    void handleInvocation(EJBClientInvocationContext context) throws Exception;

    /**
     * Handle the invocation result.  The implementation should generally call {@link EJBClientInvocationContext#getResult()}
     * immediately and perform any post-invocation cleanup task in a finally block.
     *
     * @param context the invocation context
     * @return the invocation result, if any
     * @throws Exception if an invocation error occurred
     */
    Object handleInvocationResult(EJBClientInvocationContext context) throws Exception;

    /**
     * Optionally handle a session creation invocation.  Explicit session creation is always a blocking operation.  The
     * default operation forwards to the next interceptor in the chain.
     *
     * @param context the session creation invocation context (not {@code null})
     * @return the stateful EJB locator (must not be {@code null})
     * @throws Exception if an invocation error occurred
     */
    default SessionID handleSessionCreation(EJBSessionCreationInvocationContext context) throws Exception {
        return context.proceed();
    }

    /**
     * An interceptor registration handle.
     *
     * @deprecated Please use EJBClientContext.Builder to manipulate the EJBClientInterceptors.
     */
    @Deprecated
    class Registration implements Comparable<Registration> {
        private final EJBClientContext clientContext;
        private final EJBClientInterceptor interceptor;
        private final int priority;

        Registration(final EJBClientContext clientContext, final EJBClientInterceptor interceptor, final int priority) {
            this.clientContext = clientContext;
            this.interceptor = interceptor;
            this.priority = priority;
        }

        /**
         * Remove this registration.
         */
        public void remove() {
            clientContext.removeInterceptor(this);
        }

        EJBClientInterceptor getInterceptor() {
            return interceptor;
        }

        public int compareTo(final Registration o) {
            return Integer.signum(priority - o.priority);
        }
    }
}
