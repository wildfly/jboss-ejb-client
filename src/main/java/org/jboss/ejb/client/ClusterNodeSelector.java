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

import java.util.Collection;

/**
 * A selector which selects and returns a node from the available nodes in a cluster. The {@link EJBReceiver}
 * corresponding to the selected node will then be used to forward the invocations on a EJB. Typical usage of a
 * {@link ClusterNodeSelector} involve load balancing of calls to various nodes in the cluster
 *
 * @author Jaikiran Pai
 */
public interface ClusterNodeSelector {

    /**
     * Returns the node, from among the <code>availableNodes</code> as the target node for EJB invocations
     *
     * @param clusterName    The name of the cluster to which the nodes belong
     * @param availableNodes The available nodes in the cluster
     * @return
     */
    String selectNode(final String clusterName, final Collection<String> availableNodes);
}
