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

import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.logging.Logger;
import org.jboss.remoting3.MessageInputStream;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Parses the EJB remoting protocol messages, sent from the server, contain the cluster removal notifications
 *
 * @author Jaikiran Pai
 */
class ClusterRemovalMessageHandler extends ProtocolMessageHandler {

    private static final Logger logger = Logger.getLogger(ClusterRemovalMessageHandler.class);

    private final EJBReceiverContext ejbReceiverContext;

    ClusterRemovalMessageHandler(final EJBReceiverContext ejbReceiverContext) {
        this.ejbReceiverContext = ejbReceiverContext;
    }

    @Override
    protected void processMessage(final MessageInputStream messageInputStream) throws IOException {
        if (messageInputStream == null) {
            throw new IllegalArgumentException("Cannot read from a null stream");
        }
        final Collection<String> removedClusters = new HashSet<String>();
        try {
            final DataInput input = new DataInputStream(messageInputStream);
            // read the cluster count
            final int clusterCount = PackedInteger.readPackedInteger(input);
            // for each of the clusters, read the cluster name
            for (int i = 0; i < clusterCount; i++) {
                final String clusterName = input.readUTF();
                // add it to the removed clusters
                removedClusters.add(clusterName);
            }
        } finally {
            messageInputStream.close();
        }
        // let the client context know that about the removed clusters
        final EJBClientContext clientContext = this.ejbReceiverContext.getClientContext();
        logger.debug("Received a cluster removal message for " + removedClusters.size() + " clusters " + Arrays.toString(removedClusters.toArray()));
        for (final String clusterName : removedClusters) {
            // remove the cluster from the client context
            clientContext.removeCluster(clusterName);
        }
    }
}
