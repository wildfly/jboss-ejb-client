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
import org.jboss.ejb.client.ClusterNode;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.logging.Logger;
import org.jboss.remoting3.MessageInputStream;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for parsing the EJB remoting protocol messages which contain the complete clustering topology
 * which is sent from the server, when a cluster is formed or whenever a client connects to the server and the
 * cluster is already available
 *
 * @author Jaikiran Pai
 */
class ClusterTopologyMessageHandler extends ProtocolMessageHandler {

    private static final Logger logger = Logger.getLogger(ClusterTopologyMessageHandler.class);

    private final EJBReceiverContext ejbReceiverContext;

    ClusterTopologyMessageHandler(final EJBReceiverContext ejbReceiverContext) {
        this.ejbReceiverContext = ejbReceiverContext;
    }

    @Override
    protected void processMessage(final MessageInputStream messageInputStream) throws IOException {
        if (messageInputStream == null) {
            throw new IllegalArgumentException("Cannot read from null stream");
        }
        final Map<String, Collection<ClusterNode>> clusterTopologies = new HashMap<String, Collection<ClusterNode>>();
        try {
            final DataInput input = new DataInputStream(messageInputStream);
            // read the cluster count
            final int clusterCount = PackedInteger.readPackedInteger(input);
            for (int i = 0; i < clusterCount; i++) {
                // read the cluster name
                final String clusterName = input.readUTF();
                // read the cluster member count
                final int clusterMemberCount = PackedInteger.readPackedInteger(input);
                final Collection<ClusterNode> nodes = new ArrayList<ClusterNode>();
                // read the individual cluster member information
                for (int j = 0; i < clusterMemberCount; j++) {
                    // read the cluster member's node name
                    final String nodeName = input.readUTF();
                    // read the cluster member's IP/hostname
                    final String clusterNodeAddress = input.readUTF();
                    // read the EJB remoting connector port of the cluster member
                    final int ejbRemotingPort = PackedInteger.readPackedInteger(input);

                    // form a cluster node out of the available information 
                    final ClusterNode clusterNode = new ClusterNode(clusterName, nodeName, clusterNodeAddress, ejbRemotingPort);
                    nodes.add(clusterNode);
                }
                // add ito the map of cluster topologies
                clusterTopologies.put(clusterName, nodes);
            }
        } finally {
            messageInputStream.close();
        }
        // let the client context know about the cluster topologies
        final EJBClientContext clientContext = this.ejbReceiverContext.getClientContext();
        for (final Map.Entry<String, Collection<ClusterNode>> entry : clusterTopologies.entrySet()) {
            final String clusterName = entry.getKey();
            final Collection<ClusterNode> nodes = entry.getValue();
            logger.debug("Received a cluster topology for cluster named " + clusterName + " with " + nodes.size() + " nodes");
            // create a cluster context and add the nodes to it
            final ClusterContext clusterContext = clientContext.getOrCreateClusterContext(clusterName);
            clusterContext.addClusterNodes(nodes);
        }
    }
}
