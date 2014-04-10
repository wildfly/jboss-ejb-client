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
        if (uri == null || ! uri.isAbsolute()) return NONE;
        final String scheme = uri.getScheme();
        assert scheme != null; // due to isAbsolute() check
        switch (scheme) {
            case "node": return new NodeAffinity(uri.getSchemeSpecificPart());
            case "cluster": return new ClusterAffinity(uri.getSchemeSpecificPart());
            case "local": return LOCAL;
            default: return new URIAffinity(uri);
        }
    }

    public boolean equals(Object other) {
        return other instanceof Affinity && equals((Affinity) other);
    }

    public abstract boolean equals(Affinity other);

    public abstract int hashCode();

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        if (! (this instanceof URIAffinity || this instanceof NodeAffinity || this instanceof ClusterAffinity || this instanceof LocalAffinity || this instanceof NoAffinity)) {
            throw new InvalidClassException(getClass().getName(), "Disallowed affinity class");
        }
    }

    static class NoAffinity extends Affinity {

        private static final long serialVersionUID = -2052559528672779420L;

        public int hashCode() {
            return -1;
        }

        public boolean equals(final Object other) {
            return other == this;
        }

        public boolean equals(final Affinity obj) {
            return obj == this;
        }

        protected Object readResolve() {
            return NONE;
        }

        public String toString() {
            return "None";
        }
    }

    static class LocalAffinity extends Affinity {

        private static final long serialVersionUID = -2052559528672779420L;

        public int hashCode() {
            return -1;
        }

        public boolean equals(final Object other) {
            return other == this;
        }

        public boolean equals(final Affinity obj) {
            return obj == this;
        }

        protected Object readResolve() {
            return LOCAL;
        }

        public String toString() {
            return "Local";
        }
    }
}
