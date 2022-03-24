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

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URI;

/**
 * The affinity specification for an EJB proxy.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class Affinity implements Serializable {

    private static final long serialVersionUID = -2985180758368879373L;

    /**
     * The specification for no particular affinity.
     */
    public static final Affinity NONE = new NoAffinity();

    /**
     * The specification for the local EJB environment.
     */
    public static final Affinity LOCAL = new LocalAffinity();

    /**
     * Key which will be used in the invocation context data for passing around the weak affinity
     * associated with a EJB.
     */
    public static final String WEAK_AFFINITY_CONTEXT_KEY = "jboss.ejb.weak.affinity";

    Affinity() {
    }

    /**
     * Get the affinity specification corresponding to the given URI.
     *
     * @param uri the URI
     * @return the affinity specification (not {@code null})
     */
    public static Affinity forUri(URI uri) {
        if (uri == null || !uri.isAbsolute()) return NONE;
        final String scheme = uri.getScheme();
        assert scheme != null; // due to isAbsolute() check
        switch (scheme) {
            case "node":
                return new NodeAffinity(uri.getSchemeSpecificPart());
            case "cluster":
                return new ClusterAffinity(uri.getSchemeSpecificPart());
            case "local":
                return LOCAL;
            default:
                return new URIAffinity(uri);
        }
    }

    /**
     * Get the associated URI.
     *
     * @return the associated URI (not {@code null})
     */
    public abstract URI getUri();

    public boolean equals(Object other) {
        return other instanceof Affinity && equals((Affinity) other);
    }

    public abstract boolean equals(Affinity other);

    public abstract int hashCode();

    private void readObject(ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        objectInputStream.defaultReadObject();
        if (!(this instanceof URIAffinity || this instanceof NodeAffinity || this instanceof ClusterAffinity || this instanceof LocalAffinity || this instanceof NoAffinity)) {
            throw new InvalidClassException(getClass().getName(), "Disallowed affinity class");
        }
    }

}