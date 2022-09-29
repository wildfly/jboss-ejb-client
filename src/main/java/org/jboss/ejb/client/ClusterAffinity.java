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

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A cluster affinity specification.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ClusterAffinity extends Affinity {

    private static final long serialVersionUID = -8078602613739377911L;

    private final String clusterName;

    /**
     * Construct a new instance.
     *
     * @param clusterName the associated cluster name
     */
    public ClusterAffinity(final String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * Get the associated cluster name.
     *
     * @return the associated cluster name
     */
    public String getClusterName() {
        return clusterName;
    }

    public String toString() {
        return String.format("Cluster \"%s\"", clusterName);
    }

    public URI getUri() {
        try {
            return new URI("cluster", clusterName, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean equals(final Object other) {
        return other instanceof ClusterAffinity && equals((ClusterAffinity) other);
    }

    public boolean equals(final Affinity other) {
        return other instanceof ClusterAffinity && equals((ClusterAffinity) other);
    }

    public boolean equals(final ClusterAffinity other) {
        return other != null && clusterName.equals(other.clusterName);
    }

    public int hashCode() {
        return clusterName.hashCode() + 11;
    }
}
