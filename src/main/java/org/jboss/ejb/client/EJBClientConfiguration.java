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

import java.util.Iterator;

import javax.security.auth.callback.CallbackHandler;

import org.xnio.OptionMap;

/**
 * {@link EJBClientConfiguration} is responsible for providing the configurations that will be used
 * for creating EJB receivers and managing the EJB client context. Some of these configurations are related
 * to remoting endpoints, connections which will be used to create {@link org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver}s
 * for the {@link org.jboss.ejb.client.EJBClientContext}
 *
 * @author Jaikiran Pai
 */
public interface EJBClientConfiguration {

    /**
     * Returns the endpoint name to be used for creating the remoting endpoint. This method must <b>not</b>
     * return null
     *
     * @return
     */
    String getEndpointName();

    /**
     * Returns the endpoint creation {@link OptionMap options} that will be used for creating the remoting
     * endpoint. This method must <b>not</b> return null.
     *
     * @return
     */
    OptionMap getEndpointCreationOptions();

    /**
     * Returns the {@link OptionMap options} that will be used for creating a remote connection provider.
     * This method must <b>not</b> return null.
     *
     * @return
     */
    OptionMap getRemoteConnectionProviderCreationOptions();

    /**
     * Returns the default {@link CallbackHandler} that will be used while creating remoting connections.
     * Individual connection configurations, cluster configurations, cluster node configurations can override
     * the {@link CallbackHandler} to be used while creating the connections
     * <p/>
     * This method must <b>not</b> return null.
     *
     * @return
     */
    CallbackHandler getCallbackHandler();

    /**
     * Returns the connection configurations. If there are no such configurations, then this method will return
     * an empty {@link Iterator}
     *
     * @return
     */
    Iterator<RemotingConnectionConfiguration> getConnectionConfigurations();

    /**
     * Returns a cluster configuration corresponding to the passed <code>clusterName</code>.
     * Returns null if no such cluster configuration exists.
     *
     * @param clusterName The name of the cluster
     * @return
     */
    ClusterConfiguration getClusterConfiguration(final String clusterName);

    /**
     * Returns the timeout, in milliseconds, that will be used for EJB invocations. A value of zero
     * or a negative value will imply a "wait forever" semantic where the invocation will never timeout
     * and the client will wait for the invocation result indefinitely.
     *
     * @return
     */
    long getInvocationTimeout();

    /**
     * Returns the wait timeout, in milliseconds, that will be used when the reconnect tasks are submitted.
     * The reconnect tasks are submitted in parallel and hence the value returned by this method <i>need not</i> be
     * the sum of time each reconnect task takes. The EJB client context will wait for a maximum of this amount of time
     * in milliseconds, before giving up on the reconnect attempt. If all reconnect tasks finish before this timeout,
     * then the EJB client context doesn't wait for any longer.
     * <p/>
     * If this method returns zero or a negative value, then a default of 10 second timeout will be used.
     *
     * @return
     */
    long getReconnectTasksTimeout();

    /**
     * Returns the wait timeout, in milliseconds, that will be used between the reconnect tasks retries.
     * Once a connection is lost, the ejbclient submit a new scheduled a new reconnect task till the connection is reestablished.
     * If one task fails to reconnect the ejb client will wait this interval till next retry.
     * If this method returns zero or a negative value, then a default of 5 second timeout will be used.
     *
     * @return
     */
    long getReconnectTasksInterval();

    /**
     * Returns the {@link DeploymentNodeSelector} to be used for the {@link EJBClientContext} created
     * out of this {@link EJBClientConfiguration}. If this method returns null, then it's upto the implementation
     * to use some default {@link DeploymentNodeSelector}
     *
     * @return
     */
    DeploymentNodeSelector getDeploymentNodeSelector();

    /**
     * Holds the common configurations that are required for connection creation
     */
    interface CommonConnectionCreationConfiguration {
        /**
         * Returns the {@link OptionMap options} that will be used during connection creation. This method must
         * <b>not</b> return null
         *
         * @return
         */
        OptionMap getConnectionCreationOptions();

        /**
         * Returns the {@link CallbackHandler} that will be used during connection creation. This method must
         * <b>not</b> return null
         *
         * @return
         */
        CallbackHandler getCallbackHandler();

        /**
         * Returns the connection timeout in milliseconds, that will be used during connection creation
         *
         * @return
         */
        long getConnectionTimeout();

        /**
         * Returns the {@link OptionMap options} that will be used during creation of a {@link org.jboss.remoting3.Channel}
         * for the connection
         *
         * @return
         */
        OptionMap getChannelCreationOptions();

        /**
         * If this method returns true, then the EJB client API will try and connect to the destination host "eagerly". when the {@link EJBClientContext}
         * is being created out of the {@link EJBClientConfiguration} to which this connection configuration belongs.
         * <p/>
         * On the other hand, if this method returns false, then the EJB client API will try to connect to the destination host only if no other node/EJBReceiver within the EJB client context
         * can handle a EJB invocation request. i.e. it tries to establish the connection lazily/on-demand.
         *
         * @return
         */
        boolean isConnectEagerly();
    }

    /**
     * Holds the connection specific configurations
     */
    interface RemotingConnectionConfiguration extends CommonConnectionCreationConfiguration {

        /**
         * Returns the host name/IP address to be used during connection creation. This method must <b>not</b>
         * return null
         *
         * @return
         */
        String getHost();

        /**
         * Returns the port that will be used during connection creation
         *
         * @return
         */
        int getPort();

        /**
         * The protocol to use. Can be remoting, http-remoting or https-remoting
         *
         *
         * @return The protocol
         */
        String getProtocol();

    }

    /**
     * Holds cluster specific configurations
     */
    interface ClusterConfiguration extends CommonConnectionCreationConfiguration {
        /**
         * Returns the cluster name. This method must <b>not</b> return null
         *
         * @return
         */
        String getClusterName();

        /**
         * Returns the maximum number of nodes which are allowed to be connected at a given time, in this cluster
         *
         * @return
         */
        long getMaximumAllowedConnectedNodes();

        /**
         * Returns the {@link ClusterNodeSelector} to be used for this cluster. This method <b>can</b> return
         * null, in which case the cluster will use some default {@link ClusterNodeSelector}
         *
         * @return
         */
        ClusterNodeSelector getClusterNodeSelector();

        /**
         * Returns the configuration corresponding to the <code>nodeName</code> in this cluster. Returns null
         * if no such configuration exists
         *
         * @param nodeName The name of the node in this cluster
         * @return
         */
        ClusterNodeConfiguration getNodeConfiguration(final String nodeName);

    }

    /**
     * Holds the cluster node specific configuration
     */
    interface ClusterNodeConfiguration extends CommonConnectionCreationConfiguration {
        /**
         * Returns the name of the node. This method must <b>not</b> return null
         *
         * @return
         */
        String getNodeName();

    }

}
