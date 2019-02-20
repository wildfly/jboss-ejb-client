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
    public URIAffinity(final URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI is null");
        }
        this.uri = uri;
    }

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
