/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.net.URI;

/**
 * A URI affinity specification.  Create instances using {@link Affinity#forUri(URI)}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class URIAffinity extends Affinity {

    private static final long serialVersionUID = -8437624625197058354L;

    private final URI uri;

    /**
     * Construct a new instance.
     *
     * @param uri the URI to bind to (must not be {@code null})
     */
    URIAffinity(final URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI is null");
        }
        this.uri = uri;
    }

    /**
     * Get the associated URI.
     *
     * @return the associated URI (not {@code null})
     */
    public URI getUri() {
        return uri;
    }

    Object writeReplace() {
        String s = uri.getScheme();
        if (s.equals("node")) {
            return new NodeAffinity(uri.getSchemeSpecificPart());
        } else if (s.equals("cluster")) {
            return new ClusterAffinity(uri.getSchemeSpecificPart());
        } else if (s.equals("local")) {
            return LOCAL;
        } else {
            // keep same object
            return this;
        }
    }

    public String toString() {
        return String.format("URI<%s>", uri);
    }

    public boolean equals(final Object other) {
        return other instanceof URIAffinity && equals((URIAffinity) other);
    }

    public boolean equals(final Affinity other) {
        return other instanceof URIAffinity && equals((URIAffinity) other);
    }

    public boolean equals(final URIAffinity other) {
        return other != null && uri.equals(other.uri);
    }

    public int hashCode() {
        return uri.hashCode();
    }
}
