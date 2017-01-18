/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
