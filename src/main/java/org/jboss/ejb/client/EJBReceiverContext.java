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

import org.jboss.logging.Logger;

import java.io.Closeable;
import java.util.Collection;

/**
 * The context used by receivers to communicate state changes with the EJB client context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBReceiverContext extends Attachable implements Closeable {
    private static final Logger logger = Logger.getLogger(EJBReceiverContext.class);
    private final EJBReceiver receiver;
    private final EJBClientContext clientContext;

    EJBReceiverContext(final EJBReceiver receiver, final EJBClientContext clientContext) {
        this.receiver = receiver;
        this.clientContext = clientContext;
    }

    EJBClientContext getClientContext() {
        return clientContext;
    }

    EJBReceiver getReceiver() {
        return receiver;
    }

    /**
     * Inform the EJB client context that this receiver is no longer available.
     */
    public void close() {
        this.clientContext.unregisterEJBReceiver(this.receiver);
    }

    public void clusterViewReceived(final String clusterName, final Collection<ClusterNode> clusterNodes) {
        logger.debug("Received a cluster topology for cluster named " + clusterName + " with " + clusterNodes.size() + " nodes");
        final ClusterManager clusterManager = this.clientContext.getOrCreateCluster(clusterName);
        clusterManager.addClusterNodes(clusterNodes);
    }

    public void clusterRemoved(final String clusterName) {
        logger.debug("Received a cluster removal notification for cluster named " + clusterName);
        this.clientContext.removeCluster(clusterName);
    }
}
