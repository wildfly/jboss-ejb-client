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

package org.jboss.ejb.client.remoting;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * A {@link ClusterNode} holds the information of a server side cluster server instance
 *
 * @author Jaikiran Pai
 */
final class ClusterNode {

    /**
     * The name of the cluster to which this cluster node belongs
     */
    private final String clusterName;

    /**
     * The node name of the cluster
     */
    private final String nodeName;

    private final ClientMapping[] clientMappings;
    private ResolvedDestination resolvedDestination;

    public ClusterNode(final String clusterName, final String nodeName, final ClientMapping[] clientMappings) {
        this.clusterName = clusterName;
        this.nodeName = nodeName;
        this.clientMappings = clientMappings;
        // resolve the destination from among the client mappings for this cluster node
        this.resolveDestination();
    }

    /**
     * Returns the name of the cluster to which this cluster node belongs
     *
     * @return
     */
    String getClusterName() {
        return this.clusterName;
    }

    /**
     * Returns the node name of this cluster node
     *
     * @return
     */
    String getNodeName() {
        return this.nodeName;
    }

    /**
     * Returns the destination address of the cluster node
     * @return
     */
    String getDestinationAddress() {
        return this.resolvedDestination.destinationAddress;
    }

    /**
     * Returns the destination port of the cluster node
     * @return
     */
    int getDestinationPort() {
        return this.resolvedDestination.destinationPort;
    }
    
    boolean isDestinationResolved() {
        return this.resolvedDestination != null;
    }

    private void resolveDestination() {
        for (final ClientMapping clientMapping : this.clientMappings) {
            final InetAddress sourceNetworkAddress = clientMapping.getSourceNetworkAddress();
            final int netMask = clientMapping.getSourceNetworkMaskBits();
            final boolean match = NetworkUtil.belongsToNetwork(getOurAddress(), sourceNetworkAddress, (byte) (netMask & 0xff));
            if (match) {
                this.resolvedDestination = new ResolvedDestination(clientMapping.getDestinationAddress(), clientMapping.getDestinationPort());
                return;
            }
        }
    }
    
    private final class ResolvedDestination {
        private final String destinationAddress;
        private final int destinationPort;
        
        ResolvedDestination(final String destinationAddress, final int destinationPort) {
            this.destinationAddress = destinationAddress;
            this.destinationPort = destinationPort;
        }
    }

    // TODO: This is a hack and will go soon, once we allow configuring client IP
    private InetAddress getOurAddress() {
        return null;
    }
}
