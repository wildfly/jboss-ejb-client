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

package org.jboss.ejb.client;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;

/**
 * Information about a configured cluster on an EJB client context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientCluster {
    private final String name;
    private final long maximumConnectedNodes;
    private final long connectTimeoutMilliseconds;
    private final ClusterNodeSelector clusterNodeSelector;
    private final AuthenticationConfiguration overrideConfiguration;

    EJBClientCluster(final Builder builder) {
        name = builder.name;
        maximumConnectedNodes = builder.maximumConnectedNodes;
        connectTimeoutMilliseconds = builder.connectTimeoutMilliseconds;
        clusterNodeSelector = builder.clusterNodeSelector;
        overrideConfiguration = builder.overrideConfiguration;
    }

    /**
     * Get the name of the configured cluster.
     *
     * @return the name of the configured cluster (not {@code null})
     */
    public String getName() {
        return name;
    }

    /**
     * Get the maximum number of nodes to connect.
     *
     * @return the maximum number of nodes to connect, or 0 for no limit
     */
    public long getMaximumConnectedNodes() {
        return maximumConnectedNodes;
    }

    /**
     * Get the connection timeout value in milliseconds.  This value overrides any preconfigured values.
     *
     * @return the connection timeout (in milliseconds), 0 for no timeout, or -1 to use the context default
     */
    public long getConnectTimeoutMilliseconds() {
        return connectTimeoutMilliseconds;
    }

    /**
     * Get the cluster node selector to use.
     *
     * @return the cluster node selector, or {@code null} if the default selector from the context should be used
     */
    public ClusterNodeSelector getClusterNodeSelector() {
        return clusterNodeSelector;
    }

    /**
     * Get the overriding authentication configuration in use for nodes in this cluster, overriding the caller's default.
     *
     * @return the authentication configuration to use or {@code null} to use the standard inherited authentication configuration
     */
    public AuthenticationConfiguration getOverrideConfiguration() {
        return overrideConfiguration;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("EJBClientCluster(name=");
        builder.append(name);
        builder.append(", max nodes=");
        builder.append(maximumConnectedNodes);
        builder.append(", connect timeout milis=");
        builder.append(connectTimeoutMilliseconds);
        builder.append(", selector=");
        builder.append(clusterNodeSelector);
        builder.append(", override config=");
        builder.append(overrideConfiguration);
        builder.append(")");
        return builder.toString();
    }
    /**
     * A builder for a cluster definition.
     */
    public static final class Builder {

        private String name;
        private long maximumConnectedNodes = 0;
        private long connectTimeoutMilliseconds = -1L;
        private ClusterNodeSelector clusterNodeSelector;
        private AuthenticationConfiguration overrideConfiguration;

        /**
         * Construct a new instance.
         */
        public Builder() {
        }

        public Builder setName(final String name) {
            Assert.checkNotNullParam("name", name);
            this.name = name;
            return this;
        }

        public Builder setMaximumConnectedNodes(final long maximumConnectedNodes) {
            Assert.checkMinimumParameter("maximumConnectedNodes", 0, maximumConnectedNodes);
            this.maximumConnectedNodes = maximumConnectedNodes;
            return this;
        }

        public Builder setConnectTimeoutMilliseconds(final long connectTimeoutMilliseconds) {
            Assert.checkMinimumParameter("connectTimeoutMilliseconds", -1L, connectTimeoutMilliseconds);
            this.connectTimeoutMilliseconds = connectTimeoutMilliseconds;
            return this;
        }

        public Builder setClusterNodeSelector(final ClusterNodeSelector clusterNodeSelector) {
            this.clusterNodeSelector = clusterNodeSelector;
            return this;
        }

        public Builder setOverrideConfiguration(final AuthenticationConfiguration overrideConfiguration) {
            this.overrideConfiguration = overrideConfiguration;
            return this;
        }

        /**
         * Build a new {@link EJBClientCluster} instance based on the current contents of this builder.
         *
         * @return the new instance (not {@code null})
         */
        public EJBClientCluster build() {
            Assert.checkNotNullParam("name", name);
            return new EJBClientCluster(this);
        }
    }
}
