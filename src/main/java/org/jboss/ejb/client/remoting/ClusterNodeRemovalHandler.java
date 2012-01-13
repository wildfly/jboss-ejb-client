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
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.logging.Logger;
import org.jboss.remoting3.MessageInputStream;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses a EJB remoting protocol message which corresponds to a cluster node removal message
 *
 * @author Jaikiran Pai
 */
class ClusterNodeRemovalHandler extends ProtocolMessageHandler {

    private static final Logger logger = Logger.getLogger(ClusterNodeRemovalHandler.class);

    private final ChannelAssociation channelAssociation;

    ClusterNodeRemovalHandler(final ChannelAssociation channelAssociation) {
        this.channelAssociation = channelAssociation;
    }

    @Override
    protected void processMessage(MessageInputStream messageInputStream) throws IOException {
        if (messageInputStream == null) {
            throw new IllegalArgumentException("Cannot read from null stream");
        }
        final Map<String, Collection<String>> removedNodesPerCluster = new HashMap<String, Collection<String>>();
        try {
            final DataInput input = new DataInputStream(messageInputStream);
            // read the cluster count
            final int clusterCount = PackedInteger.readPackedInteger(input);
            for (int i = 0; i < clusterCount; i++) {
                // read the cluster name
                final String clusterName = input.readUTF();
                // read the cluster member count
                final int clusterMemberCount = PackedInteger.readPackedInteger(input);
                final Collection<String> removedNodes = new ArrayList<String>();
                // read the removed cluster names
                for (int j = 0; j < clusterMemberCount; j++) {
                    final String nodeName = input.readUTF();
                    removedNodes.add(nodeName);
                }
                // add ito the map of cluster topology updates
                removedNodesPerCluster.put(clusterName, removedNodes);
            }
        } finally {
            messageInputStream.close();
        }
        // let the client context know about the removed cluster nodes
        final EJBReceiverContext ejbReceiverContext = this.channelAssociation.getEjbReceiverContext();
        final EJBClientContext clientContext = ejbReceiverContext.getClientContext();
        for (final Map.Entry<String, Collection<String>> entry : removedNodesPerCluster.entrySet()) {
            final String clusterName = entry.getKey();
            final Collection<String> removedNodes = entry.getValue();
            logger.debug("Received a cluster node(s) removal message, for cluster named " + clusterName + " with " + removedNodes.size() + " removed nodes " + Arrays.toString(removedNodes.toArray()));
            // get a cluster context for the cluster name
            final ClusterContext clusterContext = clientContext.getClusterContext(clusterName);
            // if there's no cluster context yet (which can happen if the cluster topology hasn't yet been received)
            // then there's no point in trying to remove the cluster node
            if (clusterContext != null) {
                for (final String nodeName : removedNodes) {
                    clusterContext.removeClusterNode(nodeName);
                }
            }
        }
    }
}
