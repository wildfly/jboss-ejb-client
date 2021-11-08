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

import org.wildfly.common.Assert;

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
     * @param nodeName the associated node name (must not be {@code null})
     */
    public NodeAffinity(final String nodeName) {
        Assert.checkNotNullParam("nodeName", nodeName);
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

    public URI getUri() {
        try {
            return new URI("node", nodeName, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
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
