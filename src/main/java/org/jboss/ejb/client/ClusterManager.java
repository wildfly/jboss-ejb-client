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

import java.util.*;

/**
 * A {@link ClusterManager} keeps track of a specific cluster and the {@link ClusterNode}s
 * in that cluster
 *
 * @author Jaikiran Pai
 */
final class ClusterManager {

    private final String clusterName;
    private final Collection<ClusterNode> clusterNodes = new ArrayList<ClusterNode>();
    /**
     * Map of EJB recevier context per cluster node name
     */
    private final Map<String, EJBReceiverContext> nodeEJBReceiverContexts = Collections.synchronizedMap(new IdentityHashMap<String, EJBReceiverContext>());

    ClusterManager(final String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * Returns a {@link EJBReceiverContext} from among the receiver contexts that are available in this cluster.
     *
     * @return
     * @throws IllegalArgumentException If there's no {@link EJBReceiverContext} available in this cluster
     */
    EJBReceiverContext requireEJBReceiverContext() throws IllegalArgumentException {
        final EJBReceiverContext ejbReceiverContext = this.getEJBReceiverContext();
        if (ejbReceiverContext == null) {
            throw new IllegalStateException("No EJB receiver contexts available in cluster " + clusterName);
        }
        return ejbReceiverContext;
    }

    /**
     * Returns a {@link EJBReceiverContext} from among the receiver contexts that are available in this cluster.
     * Returns null if there is no such receiver context available.
     *
     * @return
     */
    EJBReceiverContext getEJBReceiverContext() {
        if (nodeEJBReceiverContexts.isEmpty()) {
            return null;
        }
        // TODO: We need some kind of load balancing policy here
        return nodeEJBReceiverContexts.values().iterator().next();
    }

    /**
     * Returns true if the cluster managed by this {@link ClusterManager} contains a node named <code>nodeName</code>.
     * Else returns false
     *
     * @param nodeName The node name
     * @return
     */
    boolean isNodeAvailable(final String nodeName) {
        if (nodeName == null) {
            return false;
        }
        for (final ClusterNode clusterNode : clusterNodes) {
            if (nodeName.equals(clusterNode.getNodeName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add the cluster nodes to the cluster managed by this {@link ClusterManager}
     *
     * @param nodes
     */
    void addClusterNodes(final Collection<ClusterNode> nodes) {
        this.clusterNodes.addAll(nodes);
        // TODO: trigger a view update to carry out any connection management related tasks
    }

    /**
     * Close this {@link ClusterManager}. This will do any necessary cleanup of the cluster related resources
     * held by this manager. The {@link ClusterManager} will no longer be functional after it is closed and will
     * behave like a cluster which contains no {@link ClusterNode nodes}
     */
    void close() {

    }
}
