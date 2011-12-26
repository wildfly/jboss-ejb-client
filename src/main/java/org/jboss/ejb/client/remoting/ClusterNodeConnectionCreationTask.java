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
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

import java.util.concurrent.TimeUnit;

/**
 * @author Jaikiran Pai
 */
public class ClusterNodeConnectionCreationTask implements Runnable {

    private static final Logger logger = Logger.getLogger(ClusterNodeConnectionCreationTask.class);

    private final ClusterContext clusterContext;
    private final ClusterNode clusterNode;

    public ClusterNodeConnectionCreationTask(final ClusterContext clusterContext, final ClusterNode clusterNode) {
        this.clusterContext = clusterContext;
        this.clusterNode = clusterNode;
    }

    @Override
    public void run() {
        final Endpoint endpoint = null;
        final RemotingConnectionConfigurator remotingConnectionConfigurator = RemotingConnectionConfigurator.configuratorFor(endpoint);
        try {
            final Connection connection = remotingConnectionConfigurator.createConnection(this.clusterNode, OptionMap.EMPTY, null, 5, TimeUnit.SECONDS);
            this.clusterContext.registerConnection(clusterNode.getNodeName(), connection);
        } catch (Exception e) {
            // log and ignore
            logger.warn("Could not create a connection for cluster node with node name " + clusterNode.getNodeName() + " in cluster context " + this.clusterContext, e);
        }
    }
}
