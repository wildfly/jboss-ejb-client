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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ClusterContext} keeps track of a specific cluster and the {@link org.jboss.ejb.client.remoting.ClusterNode}s
 * in that cluster. A {@link ClusterContext} is always associated with a {@link EJBClientContext}
 *
 * @author Jaikiran Pai
 */
public final class ClusterContext implements EJBClientContext.EJBReceiverContextCloseHandler {

    private static final Logger logger = Logger.getLogger(ClusterContext.class);

    private static final ExecutorService executorService = Executors.newCachedThreadPool(new DaemonThreadFactory("ejb-client-cluster-node-connection-creation"));

    private final String clusterName;
    private final EJBClientContext clientContext;
    private final Map<String, ClusterNodeManager> nodeManagers = Collections.synchronizedMap(new HashMap<String, ClusterNodeManager>());
    // default to 10, will (optionally) be overridden by the ClusterConfiguration
    private long maxClusterNodeOpenConnections = 10;
    // default to RandomClusterNodeSelector
    private ClusterNodeSelector clusterNodeSelector = new RandomClusterNodeSelector();
    private final Set<ClusterContextListener> clusterContextListeners = new HashSet<ClusterContextListener>();

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
     * Returns null if there is no such receiver context available.
     *
     * @return
     */
    EJBReceiverContext getEJBReceiverContext(final EJBClientInvocationContext invocationContext) {
        final Set<String> excludedNodes = invocationContext == null ? new HashSet<String>() : new HashSet<String>(invocationContext.getExcludedNodes());
        return this.getEJBReceiverContext(excludedNodes);
    }

    /**
     * Returns a {@link EJBReceiverContext} from among the receiver contexts that are available in this cluster. The
     * node names in <code>excludedNodes</code> will <b>not</b> be considered while selecting a receiver from this
     * cluster context.
     * <p/>
     * Returns null if there is no such receiver context available.
     *
     * @param excludedNodes The node names that should be excluded while choosing a receiver from the cluster context
     * @return
     */
    private EJBReceiverContext getEJBReceiverContext(final Set<String> excludedNodes) {
        if (nodeManagers.isEmpty()) {
            return null;
        }
        final Set<String> availableNodes = this.nodeManagers.keySet();
        // remove the excluded nodes
        availableNodes.removeAll(excludedNodes);
        if (availableNodes.isEmpty()) {
            logger.debug("No nodes available in cluster " + this.clusterName + " for selecting a receiver context");
            return null;
        }
        final Set<String> alreadyConnectedNodes = this.connectedNodes;
        // remove the excluded nodes
        alreadyConnectedNodes.removeAll(excludedNodes);

        // Let the cluster node selector decide which node to use from among the available nodes
        final String selectedNodeName = this.clusterNodeSelector.selectNode(this.clusterName, alreadyConnectedNodes.toArray(new String[alreadyConnectedNodes.size()]), availableNodes.toArray(new String[availableNodes.size()]));
        // only use the selected node name, if it's valid
        if (selectedNodeName == null || selectedNodeName.trim().isEmpty()) {
            logger.warn(clusterNodeSelector + " selected an invalid node name: " + selectedNodeName + " for cluster: "
                    + clusterName + ". No EJB receiver context can be selected");
            return null;
        }
        logger.debug(this.clusterNodeSelector + " has selected node " + selectedNodeName + ", in cluster " + this.clusterName);
        // add this selected node name to excluded set, so that we don't try fetching a receiver for it
        // again
        excludedNodes.add(selectedNodeName);

        final ClusterNodeManager clusterNodeManager = this.nodeManagers.get(selectedNodeName);
        if (clusterNodeManager == null) {
            logger.debug("No node manager available for node: " + selectedNodeName + " in cluster: " + clusterName);
            // See if the selector selected a node which got removed while the selection was happening.
            // If so, then try some other node (if any) in the cluster
            if (availableNodes.contains(selectedNodeName) || alreadyConnectedNodes.contains(selectedNodeName)) {
                // this means that the node was valid when the selection was happening, but was probably
                // removed from the cluster before we could fetch a node manager for it
                // let's try a different node, this current one will be excluded
                final Set<String> nodesInThisCluster = this.nodeManagers.keySet();
                // if all nodes have been excluded/tried, just return null indicating no receiver is available
                if (excludedNodes.containsAll(nodesInThisCluster)) {
                    logger.debug("All nodes have been tried for a receiver, in cluster " + clusterName + ". No suitable receiver found");
                    return null;
                } else {
                    // explicit isDebugEnabled() check to prevent the Arrays conversion in the log message, when
                    // debug isn't enabled
                    if (logger.isDebugEnabled()) {
                        logger.debug("Retrying receiver selection in cluster " + clusterName + " with excluded nodes " + Arrays.toString(excludedNodes.toArray()));
                    }
                    // try a different node
                    return this.getEJBReceiverContext(excludedNodes);
                }
            }
            logger.debug("Node selector returned a non-existent " + selectedNodeName + " for cluster: " + clusterName + ". No EJB receiver context can be selected");
            return null;
        }
        final EJBReceiverContext selectedNodeReceiverContext = this.clientContext.getNodeEJBReceiverContext(selectedNodeName);
        // the node is already connected, so return it
        if (selectedNodeReceiverContext != null) {
            return selectedNodeReceiverContext;
        }
        // get the receiver from the node manager
        final EJBReceiver ejbReceiver = clusterNodeManager.getEJBReceiver();
        if (ejbReceiver != null) {
            // register the receiver and let it create the receiver context
            this.registerEJBReceiver(ejbReceiver);
            // let the client context return the newly associated receiver context for the node name.
            // if it wasn't successfully associated (for example version handshake not completing)
            // then this will return null.
            final EJBReceiverContext ejbReceiverContext = this.clientContext.getNodeEJBReceiverContext(selectedNodeName);
            if (ejbReceiverContext != null) {
                return ejbReceiverContext;
            }
        }
        // try some other node (if any) in this cluster. The currently selected node is
        // excluded from this next attempt
        final Set<String> nodesInThisCluster = this.nodeManagers.keySet();
        // if all nodes have been excluded/tried, just return null indicating no receiver is available
        if (excludedNodes.containsAll(nodesInThisCluster)) {
            logger.debug("All nodes have been tried for a receiver, in cluster " + clusterName + ". No suitable receiver found");
            return null;
        } else {
            // explicit isDebugEnabled() check to prevent the Arrays conversion in the log message, when
            // debug isn't enabled
            if (logger.isDebugEnabled()) {
                logger.debug("Retrying receiver selection in cluster " + clusterName + " with excluded nodes " + Arrays.toString(excludedNodes.toArray()));
            }
            // try a different node
            return this.getEJBReceiverContext(excludedNodes);
        }
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
     * @deprecated Since 1.0.6.Final. Use {@link #addClusterNodes(ClusterNodeManager...)} instead
     */
    public void addClusterNode(final String nodeName, final ClusterNodeManager clusterNodeManager) {
        this.addClusterNodes(clusterNodeManager);
    }

    /**
     * Adds the cluster nodes managed by the {@link ClusterNodeManager clusterNodeManagers}, to this cluster context
     *
     * @param clusterNodeManagers The cluster node managers
     */
    public void addClusterNodes(final ClusterNodeManager... clusterNodeManagers) {
        if (clusterNodeManagers == null) {
            return;
        }
        try {
            final Set<Future> futureAssociationResults = new HashSet<Future>();
            for (int i = 0; i < clusterNodeManagers.length; i++) {
                final ClusterNodeManager clusterNodeManager = clusterNodeManagers[i];
                if (clusterNodeManager == null) {
                    continue;
                }
                final String nodeName = clusterNodeManager.getNodeName();
                if (nodeName == null || nodeName.trim().isEmpty()) {
                    throw Logs.MAIN.nodeNameCannotBeNullOrEmptyStringForCluster(this.clusterName);
                }
                this.nodeManagers.put(nodeName, clusterNodeManager);
                // If the connected nodes in this cluster context hasn't yet reached the max allowed limit, then create a new
                // receiver and associate it with a receiver context (if the node isn't already connected to)
                if (!this.connectedNodes.contains(nodeName) && this.connectedNodes.size() < maxClusterNodeOpenConnections) {
                    // submit a task which will create and associate a EJB receiver with this cluster context
                    futureAssociationResults.add(executorService.submit(new EJBReceiverAssociationTask(this, nodeName)));
                }
            }
            // wait for the associations to be completed so that the other threads which are expecting
            // some associated nodes in this cluster context, don't run into a race condition
            for (final Future futureAssociationResult : futureAssociationResults) {
                try {
                    futureAssociationResult.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // ignore
                }
            }
        } finally {
            for (final ClusterContextListener listener : this.clusterContextListeners) {
                try {
                    listener.clusterNodesAdded(this.clusterName, clusterNodeManagers);
                } catch (Throwable t) {
                    // ignore any errors thrown by the listeners
                    logger.debug("Ignoring the exception thrown by listener " + listener, t);
                }
            }
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
        // Note, we do *not* close the associated EJBReceiverContext for the node name, from the
        // EJB client context, because the node receiver might have been created outside of this
        // cluster context (either auto-created or associated explicitly by the user code).
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
     * Register a {@link EJBReceiver} with this cluster context
     *
     * @param receiver The EJB receiver for that node
     */
    public void registerEJBReceiver(final EJBReceiver receiver) {
        if (receiver == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB receiver");
        }
        final String nodeName = receiver.getNodeName();
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
            logger.debug(this + " Added a new EJB receiver in cluster context " + clusterName + " for node " + nodeName + ". Total nodes in cluster context = " + this.connectedNodes.size());
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
        logger.debug("Node " + nodeName + " removed from cluster context " + this + " for cluster " + this.clusterName);
    }

    /**
     * Registers a {@link ClusterContextListener} to this {@link ClusterContext}
     *
     * @param listener The cluster context listener
     */
    void registerListener(final ClusterContextListener listener) {
        if (listener == null) {
            return;
        }
        this.clusterContextListeners.add(listener);
    }

    /**
     * Unregisters the passed {@link ClusterContextListener listener} from this cluster context
     *
     * @param listener The cluster context listener to unregister
     * @return Returns true if the listener was previously registered and has been unregistered after the call to this method,
     *         from the cluster context. Returns false otherwise
     */
    boolean unregisterListener(final ClusterContextListener listener) {
        return this.clusterContextListeners.remove(listener);
    }

    /**
     * A {@link EJBReceiverAssociationTask} creates and associates a {@link EJBReceiver}
     * with a {@link ClusterContext}
     */
    private class EJBReceiverAssociationTask implements Callable<Void> {

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
        public Void call() throws Exception {
            final ClusterNodeManager clusterNodeManager = this.clusterContext.nodeManagers.get(this.nodeName);
            if (clusterNodeManager == null) {
                // we don't have a cluster node manager which could create the EJB receiver, for this
                // node name
                logger.error("Cannot create EJBReceiver since no cluster node manager found for node "
                        + nodeName + " in cluster context for cluster " + clusterName);
                return null;
            }
            // get the EJB receiver from the node manager
            final EJBReceiver ejbReceiver = clusterNodeManager.getEJBReceiver();
            if (ejbReceiver == null) {
                return null;
            }
            // associate the receiver with the cluster context
            this.clusterContext.registerEJBReceiver(ejbReceiver);
            return null;
        }
    }

    interface ClusterContextListener {
        void clusterNodesAdded(final String clusterName, final ClusterNodeManager... nodes);

    }

}
