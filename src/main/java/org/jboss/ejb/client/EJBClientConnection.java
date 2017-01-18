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

    EJBClientConnection(final Builder builder) {
        destination = builder.destination;
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
     * A builder for a client connection definition.
     */
    public static final class Builder {
        URI destination;

        /**
         * Construct a new instance.
         */
        public Builder() {
        }

        /**
         * Set the destination URI.
         *
         * @param destination the destination URI (must not be {@code null})
         */
        public void setDestination(final URI destination) {
            Assert.checkNotNullParam("destination", destination);
            this.destination = destination;
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
