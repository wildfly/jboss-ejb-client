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

/**
 * A context which is provided to EJB receiver implementations in order to perform operations on the client context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBReceiverContext {
    private final EJBClientContext clientContext;

    EJBReceiverContext(final EJBClientContext clientContext) {
        this.clientContext = clientContext;
    }

    /**
     * Get the client context that corresponds to this receiver context.
     *
     * @return the client context
     */
    public EJBClientContext getClientContext() {
        return clientContext;
    }
}
