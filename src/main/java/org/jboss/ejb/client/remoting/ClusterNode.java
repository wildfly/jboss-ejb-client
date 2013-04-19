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
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;

import org.jboss.logging.Logger;

/**
 * A {@link ClusterNode} holds the information of a server side cluster server instance
 *
 * @author Jaikiran Pai
 */
final class ClusterNode {

    private static final Logger logger = Logger.getLogger(ClusterNode.class);

    private static final Collection<InetAddress> ALL_INET_ADDRESSES;

    static {
        Collection<InetAddress> addresses;
        try {
            addresses = getAllApplicableInetAddresses();
        } catch (Throwable t) {
            logger.warn("Could not fetch the InetAddress(es) of this system due to " + t.getMessage());
            logger.debug("Failed while fetching InetAddress(es) of this system ", t);

            addresses = Collections.emptySet();
        }
        ALL_INET_ADDRESSES = addresses;
    }

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
    private final String cachedToString;

    ClusterNode(final String clusterName, final String nodeName, final ClientMapping[] clientMappings) {
        this.clusterName = clusterName;
        this.nodeName = nodeName;
        this.clientMappings = clientMappings;

        // resolve the destination from among the client mappings for this cluster node
        this.resolveDestination();

        this.cachedToString = this.generateToString();
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
     * Returns the protocol to use to connect tot he cluster node
     *
     * @return
     */
    String getDestinationProtocol() {
        return this.resolvedDestination.destinationProtocol;
    }

    /**
     * Returns the destination address of the cluster node
     *
     * @return
     */
    String getDestinationAddress() {
        return this.resolvedDestination.destinationAddress;
    }

    /**
     * Returns the destination port of the cluster node
     *
     * @return
     */
    int getDestinationPort() {
        return this.resolvedDestination.destinationPort;
    }

    boolean isDestinationResolved() {
        return this.resolvedDestination != null;
    }

    @Override
    public String toString() {
        return this.cachedToString;
    }

    private String generateToString() {
        return "ClusterNode{" +
                "clusterName='" + clusterName + '\'' +
                ", nodeName='" + nodeName + '\'' +
                ", clientMappings=" + (clientMappings == null ? null : Arrays.asList(clientMappings)) +
                ", resolvedDestination=" + resolvedDestination +
                '}';
    }

    private void resolveDestination() {
        for (final ClientMapping clientMapping : this.clientMappings) {
            final InetAddress sourceNetworkAddress = clientMapping.getSourceNetworkAddress();
            final int netMask = clientMapping.getSourceNetworkMaskBits();
            for (final InetAddress address : ALL_INET_ADDRESSES) {
                logger.debug("Checking for a match of client address " + address + " with client mapping " + clientMapping);
                final boolean match = NetworkUtil.belongsToNetwork(address, sourceNetworkAddress, (byte) (netMask & 0xff));
                if (match) {
                    logger.debug("Client mapping " + clientMapping + " matches client address " + address);
                    this.resolvedDestination = new ResolvedDestination(clientMapping.getDestinationAddress(), clientMapping.getDestinationPort(), clientMapping.getDestinationProtocol());
                    return;
                }
            }
        }
    }

    private static final class ResolvedDestination {
        private final String destinationAddress;
        private final int destinationPort;
        private final String destinationProtocol;

        ResolvedDestination(final String destinationAddress, final int destinationPort, final String destinationProtocol) {
            this.destinationProtocol = destinationProtocol;
            this.destinationAddress = NetworkUtil.formatPossibleIpv6Address(destinationAddress);
            this.destinationPort = destinationPort;
        }

        @Override
        public String toString() {
            return "[Destination address=" + this.destinationAddress + ", destination port="
                    + this.destinationPort + "]";
        }
    }

    /**
     * Returns all the {@link InetAddress}es that are applicable for the current system. This is done
     * by fetching all the {@link NetworkInterface network interfaces} and then getting the {@link InetAddress}es
     * for each of the network interface.
     *
     * @return
     * @throws SocketException
     */
    private static Collection<InetAddress> getAllApplicableInetAddresses() throws SocketException {
        final Collection<InetAddress> addresses = new HashSet<InetAddress>();
        final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            final NetworkInterface networkInterface = networkInterfaces.nextElement();
            final Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                addresses.add(inetAddresses.nextElement());
            }
        }
        return addresses;
    }

}
