package org.jboss.ejb.client;

import java.net.URI;
import java.net.URISyntaxException;

public class LocalAffinity extends Affinity {
    private static final long serialVersionUID = -2052559528672779420L;
        private static final URI uri;

        static {
            try {
                uri = new URI("local", "-", null);
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }

        public int hashCode() {
            return -1;
        }

        public URI getUri() {
            return uri;
        }

        public boolean equals(final Object other) {
            return other == this;
        }

        public boolean equals(final Affinity obj) {
            return obj == this;
        }

        protected Object readResolve() {
            return org.jboss.ejb.client.LocalAffinity.LOCAL;
        }

        public String toString() {
            return "Local";
        }
}