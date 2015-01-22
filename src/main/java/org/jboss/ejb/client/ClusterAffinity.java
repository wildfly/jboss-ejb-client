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
