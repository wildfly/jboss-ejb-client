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

import org.wildfly.common.Assert;

/**
 * Information about a configured connection on an EJB client context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientConnection {
    private final URI destination;
    private final boolean forDiscovery;

    EJBClientConnection(final Builder builder) {
        destination = builder.destination;
        forDiscovery = builder.forDiscovery;
    }

    /**
     * Get the connection destination URI.
     *
     * @return the connection destination URI (not {@code null})
     */
    public URI getDestination() {
        return destination;
    }

    /**
     * Determine if this connection definition is intended to be used for discovery.
     *
     * @return {@code true} to use this connection for discovery if possible, {@code false} otherwise
     */
    public boolean isForDiscovery() {
        return forDiscovery;
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("EJBClientConnection(destination=");
        stringBuilder.append(destination);
        stringBuilder.append(", for discovery=");
        stringBuilder.append(forDiscovery);
        stringBuilder.append(")");
        return stringBuilder.toString();
    }

    /**
     * A builder for a client connection definition.
     */
    public static final class Builder {
        URI destination;
        boolean forDiscovery = true;

        /**
         * Construct a new instance.
         */
        public Builder() {
        }

        /**
         * Set the destination URI.
         *
         * @param destination the destination URI (must not be {@code null})
         * @return this builder
         */
        public Builder setDestination(final URI destination) {
            Assert.checkNotNullParam("destination", destination);
            this.destination = destination;
            return this;
        }

        /**
         * Set whether this connection should be used for discovery (defaults to {@code true}).
         *
         * @param forDiscovery {@code true} to use this connection for discovery, {@code false} otherwise
         * @return this builder
         */
        public Builder setForDiscovery(final boolean forDiscovery) {
            this.forDiscovery = forDiscovery;
            return this;
        }

        /**
         * Build a new {@link EJBClientConnection} instance based on the current contents of this builder.
         *
         * @return the new instance (not {@code null})
         */
        public EJBClientConnection build() {
            Assert.checkNotNullParam("destination", destination);
            return new EJBClientConnection(this);
        }
    }
}
