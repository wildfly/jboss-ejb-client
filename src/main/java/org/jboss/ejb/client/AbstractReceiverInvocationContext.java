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

import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * The base class of receiver invocation contexts.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractReceiverInvocationContext {
    AbstractReceiverInvocationContext() {
    }

    /**
     * Get the invocation context associated with this receiver invocation context.
     *
     * @return the invocation context
     */
    public abstract AbstractInvocationContext getClientInvocationContext();

    /**
     * Get the authentication context of the request.  The configuration may be associated with the proxy,
     * or it may have been inherited from the environment.
     *
     * @return the authentication configuration of the request (not {@code null})
     */
    public abstract AuthenticationContext getAuthenticationContext();
}
