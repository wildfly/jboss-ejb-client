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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ClusterContext} keeps track of a specific cluster and the {@link org.jboss.ejb.client.remoting.ClusterNode}s
 * in that cluster. A {@link ClusterContext} is always associated with a {@link EJBClientContext}
 *
 * @author Jaikiran Pai
 */
public final class ClusterContext implements EJBClientContext.EJBReceiverContextCloseHandler {

    private static final Logger logger = Logger.getLogger(ClusterContext.class);

    private final String clusterName;
    private final EJBClientContext clientContext;
    private final Map<String, ClusterNodeManager> nodeManagers = Collections.synchronizedMap(new HashMap<String, ClusterNodeManager>());
    // default to 10, will (optionally) be overridden by the ClusterConfiguration
    private long maxClusterNodeOpenConnections = 10;
    // default to RandomClusterNodeSelector
    private ClusterNodeSelector clusterNodeSelector = new RandomClusterNodeSelector();

    /**
     * The nodes to which a connected has already been established in this cluster context
     */
    private final Set<String> connectedNodes = Collections.synchronizedSet(new HashSet<String>());


    ClusterContext(final String clusterName, final EJBClientContext clientContext, final EJBClientConfiguration ejbClientConfiguration) {
        this.clusterName = clusterName;
        this.clientContext = clientContext;
        if (ejbClientConfiguration != null && ejbClientConfiguration.getClusterConfiguration(this.clusterName) != null) {
            final EJBClientConfiguration.ClusterConfiguration clusterConfiguration = ejbClientConfiguration.getClusterConfiguration(this.clusterName);
            this.setupClusterSpecificConfigurations(clusterConfiguration);
        } else {
            this.maxClusterNodeOpenConnections = 10; // default to 10
        }
    }

    public String getClusterName() {
        return this.clusterName;
    }

    public EJBClientContext getEJBClientContext() {
        return this.clientContext;
    }

    /**
     * Returns a {@link EJBReceiverContext} from among the receiver contexts that are available in this cluster.
     *
     * @return
     * @throws IllegalArgumentException If there's no {@link EJBReceiverContext} available in this cluster
     */
    EJBReceiverContext requireEJBReceiverContext() throws IllegalArgumentException {
        final EJBReceiverContext ejbReceiverContext = this.getEJBReceiverContext();
        if (ejbReceiverContext == null) {
            throw new IllegalStateException("No EJB receiver contexts available in cluster " + clusterName);
        }
        return ejbReceiverContext;
    }

    /**
     * Returns a {@link EJBReceiverContext} from among the receiver contexts that are available in this cluster.
     * Returns null if there is no such receiver context available.
     *
     * @return
     */
    EJBReceiverContext getEJBReceiverContext() {
        if (nodeManagers.isEmpty()) {
            return null;
        }
        final Set<String> availableNodes = this.nodeManagers.keySet();
        // Let the cluster node selector decide which node to use from among the available nodes
        final String selectedNodeName = this.clusterNodeSelector.selectNode(this.clusterName, this.connectedNodes.toArray(new String[connectedNodes.size()]), availableNodes.toArray(new String[availableNodes.size()]));
        // only use the selected node name, if it's valid
        if (selectedNodeName == null || !this.nodeManagers.containsKey(selectedNodeName)) {
            logger.warn(clusterNodeSelector + " selected an invalid node name: " + selectedNodeName + " for cluster: "
                    + clusterName + ". No EJB receiver context can be selected");
            return null;
        }
        logger.debug(this.clusterNodeSelector + " has selected node " + selectedNodeName + ", in cluster " + this.clusterName);
        final EJBReceiverContext selectedNodeReceiverContext = this.clientContext.getNodeEJBReceiverContext(selectedNodeName);
        // the node is already connected, so return it
        if (selectedNodeReceiverContext != null) {
            return selectedNodeReceiverContext;
        }
        // A receiver hasn't been associated for the given node name, so let's do it now
        final ClusterNodeManager clusterNodeManager = this.nodeManagers.get(selectedNodeName);
        if (clusterNodeManager == null) {
            // we don't have a cluster node manager which could create the EJB receiver, for this
            // node name
            logger.error("Cannot create EJBReceiver since no cluster node manager found for node "
                    + selectedNodeName + " in cluster context for cluster " + clusterName);
            return null;
        }
        // get the receiver from the node manager
        final EJBReceiver ejbReceiver = clusterNodeManager.getEJBReceiver();
        if (ejbReceiver == null) {
            return null;
        }
        // register the receiver and let it create the receiver context
        this.registerEJBReceiver(selectedNodeName, ejbReceiver);
        // let the client context return the newly associated receiver context for the node name.
        // if it wasn't successfully associated (for example version handshake not completing)
        // then this will return null.
        return this.clientContext.getNodeEJBReceiverContext(selectedNodeName);
    }

    /**
     * Returns true if the cluster managed by this {@link ClusterContext} contains a node named <code>nodeName</code>.
     * Else returns false
     *
     * @param nodeName The node name
     * @return
     */
    boolean isNodeAvailable(final String nodeName) {
        if (nodeName == null) {
            return false;
        }
        return this.nodeManagers.containsKey(nodeName);
    }

    /**
     * Adds a cluster node and the {@link ClusterNodeManager} associated with that node, to this cluster context
     *
     * @param nodeName           The cluster node name
     * @param clusterNodeManager The cluster node manager for that node
     */
    public void addClusterNode(final String nodeName, final ClusterNodeManager clusterNodeManager) {
        this.nodeManagers.put(nodeName, clusterNodeManager);
        // If the connected nodes in this cluster context hasn't yet reached the max allowed limit, then create a new
        // receiver and associate it with a receiver context (if the node isn't already connected to)
        if (!this.connectedNodes.contains(nodeName) && this.connectedNodes.size() < maxClusterNodeOpenConnections) {
            // submit a task which will create and associate a EJB receiver with this cluster context
            // TODO: This will be changed later to be better handled via a ExecutorService
            final Thread connectionCreationThread = new Thread(new EJBReceiverAssociationTask(this, nodeName));
            connectionCreationThread.setDaemon(true);
            connectionCreationThread.start();
        }
    }

    /**
     * Removes a previously assoicated cluster node, if any, from this cluster context.
     *
     * @param nodeName The node name
     */
    public void removeClusterNode(final String nodeName) {
        // remove from the node managers
        this.nodeManagers.remove(nodeName);
        // remove any EJB receiver contexts for this node name
        this.connectedNodes.remove(nodeName);
    }

    /**
     * Removes all previously associated cluster node(s), if any, from this cluster context
     */
    public void removeAllClusterNodes() {
        this.nodeManagers.clear();
        this.connectedNodes.clear();
    }

    /**
     * Close this {@link ClusterContext}. This will do any necessary cleanup of the cluster related resources
     * held by this manager. The {@link ClusterContext} will no longer be functional after it is closed and will
     * behave like a cluster context which contains no nodes
     */
    void close() {
        this.removeAllClusterNodes();
    }

    /**
     * Register a {@link EJBReceiver} for the <code>nodeName</code>, with this cluster context
     *
     * @param nodeName The node name
     * @param receiver The EJB receiver for that node
     */
    public void registerEJBReceiver(final String nodeName, final EJBReceiver receiver) {
        if (receiver == null) {
            throw new IllegalArgumentException("receiver is null");
        }
        if (this.connectedNodes.contains(nodeName)) {
            // nothing to do
            return;
        }
        // let the client context manage the registering
        this.clientContext.registerEJBReceiver(receiver, this);
        // now let's get back the receiver context that got associated, so that we too can keep a track of those
        // receiver contexts
        final EJBReceiverContext ejbReceiverContext = this.clientContext.getNodeEJBReceiverContext(nodeName);
        // it's possible that while associating a receiver context to a receiver, there were problems (like
        // version handshake not being completed) which might have resulted in the receiver context not being
        // created for this node. So let's do a null check here
        if (ejbReceiverContext != null) {
            // add it to our connected nodes
            this.connectedNodes.add(nodeName);
            logger.info("Added a new EJB receiver in cluster context " + clusterName + " for node " + nodeName + ". Total nodes in cluster context = " + this.connectedNodes.size());
        }
    }

    private void setupClusterSpecificConfigurations(final EJBClientConfiguration.ClusterConfiguration clusterConfiguration) {
        final long maxLimit = clusterConfiguration.getMaximumAllowedConnectedNodes();
        // don't use 0 or negative values
        if (maxLimit > 0) {
            this.maxClusterNodeOpenConnections = maxLimit;
        }
        final ClusterNodeSelector nodeSelector = clusterConfiguration.getClusterNodeSelector();
        if (nodeSelector != null) {
            this.clusterNodeSelector = nodeSelector;
        }

    }

    @Override
    public void receiverContextClosed(EJBReceiverContext receiverContext) {
        final String nodeName = receiverContext.getReceiver().getNodeName();
        this.connectedNodes.remove(nodeName);
    }

    /**
     * A {@link EJBReceiverAssociationTask} creates and associates a {@link EJBReceiver}
     * with a {@link ClusterContext}
     */
    private class EJBReceiverAssociationTask implements Runnable {

        private final ClusterContext clusterContext;
        private final String nodeName;

        /**
         * @param clusterContext The cluster context
         * @param nodeName       The node name
         */
        EJBReceiverAssociationTask(final ClusterContext clusterContext, final String nodeName) {
            this.nodeName = nodeName;
            this.clusterContext = clusterContext;
        }


        @Override
        public void run() {
            final ClusterNodeManager clusterNodeManager = this.clusterContext.nodeManagers.get(this.nodeName);
            if (clusterNodeManager == null) {
                // we don't have a cluster node manager which could create the EJB receiver, for this
                // node name
                logger.error("Cannot create EJBReceiver since no cluster node manager found for node "
                        + nodeName + " in cluster context for cluster " + clusterName);
                return;
            }
            // get the EJB receiver from the node manager
            final EJBReceiver ejbReceiver = clusterNodeManager.getEJBReceiver();
            if (ejbReceiver == null) {
                return;
            }
            // associate the receiver with the cluster context
            this.clusterContext.registerEJBReceiver(this.nodeName, ejbReceiver);
        }
    }
}
