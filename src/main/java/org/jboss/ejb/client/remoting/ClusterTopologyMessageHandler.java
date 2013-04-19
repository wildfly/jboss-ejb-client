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

import org.jboss.ejb.client.ClusterContext;
import org.jboss.ejb.client.ClusterNodeManager;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Endpoint;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for parsing the EJB remoting protocol messages which contain the information about the nodes that
 * are part of the cluster. If this {@link ClusterTopologyMessageHandler} was created by passing <code>true</code>
 * as the <code>completeTopology</code> param during {@link #ClusterTopologyMessageHandler(ChannelAssociation, boolean)} construction}
 * then the message will be parsed as a complete topology i.e. any previous nodes in the cluster context corresponding
 * to the cluster will be removed before adding this nodes to the context. Else, the nodes will be considered as new
 * nodes and will just be added to the cluster context (without removing any existing nodes from the context).
 *
 * @author Jaikiran Pai
 */
class ClusterTopologyMessageHandler extends ProtocolMessageHandler {

    private static final Logger logger = Logger.getLogger(ClusterTopologyMessageHandler.class);

    private final ChannelAssociation channelAssociation;
    private final boolean completeTopology;

    ClusterTopologyMessageHandler(final ChannelAssociation channelAssociation, final boolean completeTopology) {
        this.channelAssociation = channelAssociation;
        this.completeTopology = completeTopology;
    }

    @Override
    protected void processMessage(final InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Cannot read from null stream");
        }
        final Map<String, Collection<ClusterNode>> clusterNodes = new HashMap<String, Collection<ClusterNode>>();
        try {
            final DataInput input = new DataInputStream(inputStream);
            // read the cluster count
            final int clusterCount = PackedInteger.readPackedInteger(input);
            for (int i = 0; i < clusterCount; i++) {
                // read the cluster name
                final String clusterName = input.readUTF();
                // read the cluster member count
                final int clusterMemberCount = PackedInteger.readPackedInteger(input);
                final Collection<ClusterNode> nodes = new ArrayList<ClusterNode>();
                // read the individual cluster member information
                for (int j = 0; j < clusterMemberCount; j++) {
                    // read the cluster member's node name
                    final String nodeName = input.readUTF();
                    // read the client-mapping count
                    final int clientMappingCount = PackedInteger.readPackedInteger(input);
                    final ClientMapping[] clientMappings = new ClientMapping[clientMappingCount];
                    // read each of the client-mapping(s)
                    for (int c = 0; c < clientMappingCount; c++) {
                        // read the client netmask which also contains the ipv4/ipv6 differentiator
                        final int netMaskWithIPFamilyDifferentiator = PackedInteger.readPackedInteger(input);

                        final int ipFamilyDifferentiator = netMaskWithIPFamilyDifferentiator & 1;
                        final int clientNetMask = netMaskWithIPFamilyDifferentiator >> 1;
                        final byte[] clientNetworkAddressBytes;
                        if (ipFamilyDifferentiator == 0) {
                            // ipv6
                            clientNetworkAddressBytes = new byte[16];
                        } else {
                            // ipv4
                            clientNetworkAddressBytes = new byte[4];
                        }
                        // read the client network address
                        input.readFully(clientNetworkAddressBytes);
                        final InetAddress clientNetworkAddress = InetAddress.getByAddress(clientNetworkAddressBytes);
                        // read the destination address
                        final String destinationAddress = input.readUTF();
                        // read the destination port
                        final short destinationPort = input.readShort();
                        // create a ClientMapping out of this
                        clientMappings[c] = new ClientMapping(clientNetworkAddress, clientNetMask & 0xff, destinationAddress, destinationPort, channelAssociation.getRemotingProtocol());
                    }
                    // form a cluster node out of the available information
                    final ClusterNode clusterNode = new ClusterNode(clusterName, nodeName, clientMappings);
                    nodes.add(clusterNode);
                }
                // add ito the map of cluster topologies
                clusterNodes.put(clusterName, nodes);
            }
        } finally {
            inputStream.close();
        }
        // let the client context know about the cluster topologies
        final EJBReceiverContext ejbReceiverContext = this.channelAssociation.getEjbReceiverContext();
        final EJBClientContext clientContext = ejbReceiverContext.getClientContext();
        for (final Map.Entry<String, Collection<ClusterNode>> entry : clusterNodes.entrySet()) {
            final String clusterName = entry.getKey();
            final Collection<ClusterNode> nodes = entry.getValue();
            logger.debug("Received a cluster node(s) addition message, for cluster named " + clusterName + " with " + nodes.size() + " nodes " + Arrays.toString(nodes.toArray()));
            // create a cluster context and add the nodes to it
            final ClusterContext clusterContext = clientContext.getOrCreateClusterContext(clusterName);
            // if this is a complete topology message, then we'll first remove any existing nodes from the cluster context
//            if (this.completeTopology) {
//                clusterContext.removeAllClusterNodes();
//            }
            this.addNodesToClusterContext(clusterContext, nodes);
        }
    }

    private void addNodesToClusterContext(final ClusterContext clusterContext, final Collection<ClusterNode> clusterNodes) {
        final Endpoint endpoint = this.channelAssociation.getChannel().getConnection().getEndpoint();
        final EJBClientConfiguration ejbClientConfiguration = clusterContext.getEJBClientContext().getEJBClientConfiguration();
        final ClusterNodeManager[] clusterNodeManagers = new ClusterNodeManager[clusterNodes.size()];
        int i = 0;
        for (final ClusterNode clusterNode : clusterNodes) {
            clusterNodeManagers[i++] = new RemotingConnectionClusterNodeManager(clusterContext, clusterNode, endpoint, ejbClientConfiguration, channelAssociation.getRemotingProtocol());
        }
        // add the cluster nodes to the cluster context
        clusterContext.addClusterNodes(clusterNodeManagers);
    }
}
