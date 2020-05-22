/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.ejb.server;

import java.net.InetAddress;
import java.util.List;

import org.jboss.remoting3.Connection;

/**
 * A legacy cluster topology notification client.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ClusterTopologyListener {

    void clusterTopology(List<ClusterInfo> clusterInfoList);

    void clusterRemoval(List<String> clusterNames);

    void clusterNewNodesAdded(ClusterInfo newClusterInfo);

    void clusterNodesRemoved(List<ClusterRemovalInfo> clusterRemovalInfoList);

    /**
     * Returns the remoting connection associated with this listener
     * @return a remoting connection
     */
    Connection getConnection();

    final class ClusterInfo {
        private final String clusterName;
        private final List<NodeInfo> nodeInfoList;

        public ClusterInfo(final String clusterName, final List<NodeInfo> nodeInfoList) {
            this.clusterName = clusterName;
            this.nodeInfoList = nodeInfoList;
        }

        public String getClusterName() {
            return clusterName;
        }

        public List<NodeInfo> getNodeInfoList() {
            return nodeInfoList;
        }
    }

    final class NodeInfo {
        private final String nodeName;
        private final List<MappingInfo> mappingInfoList;

        public NodeInfo(final String nodeName, final List<MappingInfo> mappingInfoList) {
            this.nodeName = nodeName;
            this.mappingInfoList = mappingInfoList;
        }

        public String getNodeName() {
            return nodeName;
        }

        public List<MappingInfo> getMappingInfoList() {
            return mappingInfoList;
        }
    }

    final class MappingInfo {
        private final String destinationAddress;
        private final int destinationPort;
        private final InetAddress sourceAddress;
        private final int netmaskBits;

        public MappingInfo(final String destinationAddress, final int destinationPort, final InetAddress sourceAddress, final int netmaskBits) {
            this.destinationAddress = destinationAddress;
            this.destinationPort = destinationPort;
            this.sourceAddress = sourceAddress;
            this.netmaskBits = netmaskBits;
        }

        public String getDestinationAddress() {
            return destinationAddress;
        }

        public int getDestinationPort() {
            return destinationPort;
        }

        public InetAddress getSourceAddress() {
            return sourceAddress;
        }

        public int getNetmaskBits() {
            return netmaskBits;
        }
    }

    final class ClusterRemovalInfo {
        private final String clusterName;
        private final List<String> nodeNames;

        public ClusterRemovalInfo(final String clusterName, final List<String> nodeNames) {
            this.clusterName = clusterName;
            this.nodeNames = nodeNames;
        }

        public String getClusterName() {
            return clusterName;
        }

        public List<String> getNodeNames() {
            return nodeNames;
        }
    }
}
