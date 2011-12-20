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

/**
 * A {@link ClusterNode} holds the information of a server side cluster server instance
 *
 * @author Jaikiran Pai
 */
public final class ClusterNode {

    /**
     * The name of the cluster to which this cluster node belongs
     */
    private final String clusterName;

    /**
     * The node name of the cluster
     */
    private final String nodeName;

    /**
     * The hostname/IP address of this cluster node
     */
    private final String address;

    /**
     * The EJB remoting connector service port of this cluster node
     */
    private final int ejbRemotingConnectorPort;

    public ClusterNode(final String clusterName, final String nodeName, final String address, final int ejbRemotingConnectorPort) {
        this.clusterName = clusterName;
        this.nodeName = nodeName;
        this.ejbRemotingConnectorPort = ejbRemotingConnectorPort;
        this.address = address;
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
     * The returns the address (which can be a hostname or the IP address) of this cluster node
     *
     * @return
     */
    String getAddress() {
        return this.address;
    }

    /**
     * Returns the EJB remoting connector service port of this cluster node
     *
     * @return
     */
    int getEjbRemotingConnectorPort() {
        return this.ejbRemotingConnectorPort;
    }

    /**
     * Returns the node name of this cluster node
     *
     * @return
     */
    String getNodeName() {
        return this.nodeName;
    }
}
