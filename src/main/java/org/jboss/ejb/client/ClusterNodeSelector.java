/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

/**
 * A selector which selects and returns a node from the available nodes in a cluster. Typical usage of a
 * {@link ClusterNodeSelector} involve load balancing of calls to various nodes in the cluster.
 *
 * @author Jaikiran Pai
 */
public interface ClusterNodeSelector {

    /**
     * Returns a node from among the {@code totalAvailableNodes}, as the target node for EJB invocations.
     * The selector can decide whether to pick an already connected node (from the passed {@code connectedNodes})
     * or decide to select a node to which a connection hasn't yet been established. If a node to which a connection
     * hasn't been established is selected then the cluster context will create a connection to it.
     *
     * @param clusterName         the name of the cluster to which the nodes belong (will not be {@code null})
     * @param connectedNodes      the node names to which a connection has been established (may be empty but will not be {@code null})
     * @param totalAvailableNodes all available nodes in the cluster, including connected nodes (will not be empty or {@code null})
     * @return the selected node name (must not be {@code null})
     */
    String selectNode(final String clusterName, final String[] connectedNodes, final String[] totalAvailableNodes);
}
