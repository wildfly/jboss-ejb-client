/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;


/**
 * A {@link ClusterContextManager} is responsible for creating and managing the {@link ClusterContext} associated
 * to a {@link EJBClientContext}
 * @author Enrique González
 *
 */
public final class ClusterContextManager  {

    private static final Logger logger = Logger.getLogger(ClusterContextManager.class);
    /**
     * Cluster contexts mapped against their cluster name
     */
    private final Map<String, ClusterContext> clusterContexts;

    /**
     * Client context reference
     */
    private EJBClientContext ejbClientContext;
    private ClusterFormationNotifier clusterFormationNotifier;

    ClusterContextManager(EJBClientContext ejbClientContext) {
         this.clusterContexts = Collections.synchronizedMap(new HashMap<String, ClusterContext>());
         this.ejbClientContext = ejbClientContext;
    }

   /**
    * Returns a {@link ClusterContext} corresponding to the passed <code>clusterName</code>. If no
    * such cluster context exists, a new one is created and returned. Subsequent invocations on this
    * {@link EJBClientContext} for the same cluster name will return this same {@link ClusterContext}, unless
    * the cluster has been removed from this client context.
    *
    * @param clusterName The name of the cluster
    * @return
    */
    public synchronized ClusterContext getOrCreateClusterContext(String clusterName) {
        ClusterContext clusterContext = this.clusterContexts.get(clusterName);
        if (clusterContext == null) {
            clusterContext = new ClusterContext(clusterName, this.ejbClientContext, this.ejbClientContext.getEJBClientConfiguration());
            // register a listener which will trigger a notification when nodes are added to the cluster
            clusterContext.registerListener(this.clusterFormationNotifier);
            this.clusterContexts.put(clusterName, clusterContext);
        }
        return clusterContext;
    }

   /**
    * Returns true if the <code>nodeName</code> belongs to a cluster named <code>clusterName</code>. Else
    * returns false.
    *
    * @param clusterName The name of the cluster
    * @param nodeName    The name of the node within the cluster
    * @return
    */
    public synchronized ClusterContext getClusterContext(String clusterName) {
        return this.clusterContexts.get(clusterName);
    }

    /**
     * Removes the cluster identified by the <code>clusterName</code> from this client context
     *
     * @param clusterName The name of the cluster
     */
    public synchronized void removeClusterContext(String clusterName) {
        final ClusterContext clusterContext = this.clusterContexts.remove(clusterName);
        if (clusterContext == null) {
            return;
        }
        try {
            // close the cluster context to allow it to cleanup any resources
            clusterContext.close();
        } catch (Throwable t) {
            // ignore
            logger.debug("Ignoring an error that occured while closing a cluster context for cluster named " + clusterName, t);
        }
    }

    /**
     * Returns true if the <code>nodeName</code> belongs to a cluster named <code>clusterName</code>. Else
     * returns false.
     *
     * @param clusterName The name of the cluster
     * @param nodeName    The name of the node within the cluster
     * @return
     */
    public synchronized boolean clusterContains(String clusterName, String nodeName) {
        final ClusterContext clusterContext = this.clusterContexts.get(clusterName);
        if (clusterContext == null) {
            return false;
        }
        return clusterContext.isNodeAvailable(nodeName);
    }

    /**
     * Returns a {@link EJBReceiverContext} for the <code>clusterName</code>. If there's no such receiver context
     * for the cluster, then this method returns null
     *
     * @param clusterName       The name of the cluster
     * @param invocationContext
     * @return
     */
    public synchronized EJBReceiverContext getClusterEJBReceiverContext(String clusterName, EJBClientInvocationContext invocationContext) {

        final ClusterContext clusterContext = this.clusterContexts.get(clusterName);
        if (clusterContext == null) {
            return null;
        }
        return clusterContext.getEJBReceiverContext(invocationContext);
    }

   /**
    * Returns a {@link EJBReceiverContext} for the <code>clusterName</code>. If there's no such receiver context
    * for the cluster, then this method throws an {@link IllegalArgumentException}
    *
    * @param clusterName The name of the cluster
    * @param invocationContext 
    * @return
    * @throws IllegalArgumentException If there's no EJB receiver context available for the cluster
    */
    public synchronized EJBReceiverContext requireClusterEJBReceiverContext(String clusterName, EJBClientInvocationContext invocationContext) {
        ClusterContext clusterContext = this.clusterContexts.get(clusterName);
        if (clusterContext == null) {
            // let's wait for some time to see if the asynchronous cluster topology becomes available.
            // Note that this isn't a great thing to do for clusters which might have been removed or for clusters
            // which will never be formed, since this wait results in a 5 second delay in the invocation. But ideally
            // such cases should be pretty low.
            logger.debug("Waiting for cluster topology information to be available for cluster named " + clusterName);
            this.waitForClusterTopology(clusterName);
            // see if the cluster context was created during this wait time
            clusterContext = this.clusterContexts.get(clusterName);
            if (clusterContext == null) {
                throw Logs.MAIN.noClusterContextAvailable(clusterName);
            }
        }


        return this.getClusterEJBReceiverContext(clusterName, invocationContext);
    }

    /**
     * Waits for (a maximum of 5 seconds for) a cluster topology to be available for <code>clusterName</code>
     *
     * @param clusterName The name of the cluster
     */
    private void waitForClusterTopology(final String clusterName) {
        final CountDownLatch clusterFormationLatch = new CountDownLatch(1);
        // register for the notification
        this.clusterFormationNotifier.registerForClusterFormation(clusterName, clusterFormationLatch);
        // now wait (max 5 seconds)
        try {
            final boolean receivedClusterTopology = clusterFormationLatch.await(5, TimeUnit.SECONDS);
            if (receivedClusterTopology) {
                logger.debug("Received the cluster topology for cluster named " + clusterName + " during the wait time");
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            // unregister from the cluster formation notification
            this.clusterFormationNotifier.unregisterFromClusterNotification(clusterName, clusterFormationLatch);
        }
    }


    /**
     * A notifier which can be used for waiting for cluster formation events
     */
    private final class ClusterFormationNotifier implements ClusterContext.ClusterContextListener {

        private final Map<String, List<CountDownLatch>> clusterFormationListeners = new HashMap<String, List<CountDownLatch>>();

        /**
         * Register for cluster formation event notification.
         *
         * @param clusterName The name of the cluster
         * @param latch       The {@link CountDownLatch} which the caller can use to wait for the cluster formation
         *                    to take place. The {@link ClusterFormationNotifier} will invoke the {@link java.util.concurrent.CountDownLatch#countDown()}
         *                    when the cluster is formed
         */
        void registerForClusterFormation(final String clusterName, final CountDownLatch latch) {
            synchronized (this.clusterFormationListeners) {
                List<CountDownLatch> listeners = this.clusterFormationListeners.get(clusterName);
                if (listeners == null) {
                    listeners = new ArrayList<CountDownLatch>();
                    this.clusterFormationListeners.put(clusterName, listeners);
                }
                listeners.add(latch);
            }
        }

        /**
         * Callback invocation for the cluster formation event. This method will invoke {@link java.util.concurrent.CountDownLatch#countDown()}
         * on each of the waiting {@link CountDownLatch} for the cluster.
         *
         * @param clusterName The name of the cluster
         */
        void notifyClusterFormation(final String clusterName) {
            final List<CountDownLatch> listeners;
            synchronized (this.clusterFormationListeners) {
                // remove the waiting listeners
                listeners = this.clusterFormationListeners.remove(clusterName);
            }
            if (listeners == null) {
                return;
            }
            // notify any waiting listeners
            for (final CountDownLatch latch : listeners) {
                latch.countDown();
            }
        }

        /**
         * Unregisters from cluster formation notifications for the cluster
         *
         * @param clusterName The name of the cluster
         * @param latch       The {@link CountDownLatch} which will be unregistered from the waiting {@link CountDownLatch}es
         */
        void unregisterFromClusterNotification(final String clusterName, final CountDownLatch latch) {
            synchronized (this.clusterFormationListeners) {
                final List<CountDownLatch> listeners = this.clusterFormationListeners.get(clusterName);
                if (listeners == null) {
                    return;
                }
                listeners.remove(latch);
            }
        }

        @Override
        public void clusterNodesAdded(String clusterName, ClusterNodeManager... nodes) {
            this.notifyClusterFormation(clusterName);
        }
    }

}

