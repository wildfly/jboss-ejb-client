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

import org.wildfly.common.annotation.NotNull;

/**
 * A server association.  Since server associations yield {@link ClassLoader} instances, it is important that classes
 * implementing this interface are not accessible without a permission check when a security manager is present.  This
 * class is implemented by EJB server environments and consumed by protocol implementations.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Association {

    /**
     * Receive and execute an invocation request.  An invocation may be cancelled; the returned handle may be used
     * by the protocol implementation when a cancellation request is received by the server.
     *
     * @param invocationRequest the invocation request (not {@code null})
     * @param <T> the type of the target EJB
     * @return a handle which may be used to request cancellation of the invocation (must not be {@code null})
     */
    @NotNull
    <T> CancelHandle receiveInvocationRequest(@NotNull InvocationRequest invocationRequest);

    /**
     * Receive and execute a session open request.  An invocation may be cancelled; the returned handle may be used
     * by the protocol implementation when a cancellation request is received by the server.
     *
     * @param sessionOpenRequest the session open request (not {@code null})
     * @return a handle which may be used to request cancellation of the invocation (must not be {@code null})
     */
    @NotNull
    CancelHandle receiveSessionOpenRequest(@NotNull SessionOpenRequest sessionOpenRequest);

    /**
     * Register a cluster topology listener.  This is used by legacy protocols to transmit cluster updates to old clients.
     *
     * @param clusterTopologyListener the cluster topology listener (not {@code null})
     * @return a handle which may be used to cancel the topology listener registration (must not be {@code null})
     */
    @NotNull
    ListenerHandle registerClusterTopologyListener(@NotNull ClusterTopologyListener clusterTopologyListener);

    /**
     * Register a module availability listener.  This is used by legacy clients which use no-affinity EJB locators.
     *
     * @param moduleAvailabilityListener the module availability listener (not {@code null})
     * @return a handle which may be used to cancel the availability listener registration (must not be {@code null})
     */
    @NotNull
    ListenerHandle registerModuleAvailabilityListener(@NotNull ModuleAvailabilityListener moduleAvailabilityListener);
}
