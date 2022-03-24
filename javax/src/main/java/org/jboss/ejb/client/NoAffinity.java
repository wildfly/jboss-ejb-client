package org.jboss.ejb.client;

import java.net.URI;

public class NoAffinity extends Affinity {
    public static final long serialVersionUID = -2052559528672779420L;

    /**
     * The specification for no particular affinity.
     */
    public static final Affinity NONE = new NoAffinity();

    public NoAffinity() {
    }

    public int hashCode() {
        return -1;
    }

    public URI getUri() {
        return null;
    }

    public boolean equals(final Object other) {
        return other == null;
    }

    public boolean equals(final Affinity obj) {
        return obj == null;
    }

    public Object readResolve() {
        return NONE;
    }

    public String toString() {
        return "None";
    }
}