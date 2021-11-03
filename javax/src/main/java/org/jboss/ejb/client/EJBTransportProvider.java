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
 * An EJB transport provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface EJBTransportProvider {

    /**
     * Notify the provider instance that it has been registered with the given client context.
     *
     * @param receiverContext the EJB receiver context (not {@code null})
     */
    default void notifyRegistered(EJBReceiverContext receiverContext) {}

    /**
     * Determine whether this transport provider supports the protocol identified by the given URI scheme.
     *
     * @param uriScheme the URI scheme
     * @return {@code true} if this provider supports the protocol, {@code false} otherwise
     */
    boolean supportsProtocol(String uriScheme);

    /**
     * Get an EJB receiver for the protocol identified by the given URI scheme.
     *
     * @param receiverContext the receiver context
     * @param uriScheme the URI scheme
     * @return the non-{@code null} EJB receiver
     * @throws IllegalArgumentException if the protocol is not supported
     */
    EJBReceiver getReceiver(EJBReceiverContext receiverContext, String uriScheme) throws IllegalArgumentException;

    default void close(EJBReceiverContext receiverContext) throws Exception {}
}
