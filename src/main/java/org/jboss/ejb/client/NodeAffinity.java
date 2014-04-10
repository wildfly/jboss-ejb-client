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
 * A single node affinity specification.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NodeAffinity extends Affinity {

    private static final long serialVersionUID = -1241023739831847480L;

    private final String nodeName;

    /**
     * Construct a new instance.
     *
     * @param nodeName the associated node name
     */
    public NodeAffinity(final String nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * Get the associated node name.
     *
     * @return the associated node name
     */
    public String getNodeName() {
        return nodeName;
    }

    public String toString() {
        return String.format("Node \"%s\"", nodeName);
    }

    public boolean equals(final Object other) {
        return other instanceof NodeAffinity && equals((NodeAffinity) other);
    }

    public boolean equals(final Affinity other) {
        return other instanceof NodeAffinity && equals((NodeAffinity) other);
    }

    public boolean equals(final NodeAffinity other) {
        return other != null && nodeName.equals(other.nodeName);
    }

    public int hashCode() {
        return nodeName.hashCode() + 53;
    }
}
