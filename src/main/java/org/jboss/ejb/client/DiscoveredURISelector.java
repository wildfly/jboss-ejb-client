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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.wildfly.common.Assert;

/**
 * A selector which selects and returns a URI, from among the passed eligible URIs, that can handle a specific
 * deployment within a EJB client context. Typical usage of {@link DiscoveredURISelector} involves load balancing
 * calls to multiple targets which can all handle the same deployment. This allows the application to have a deterministic
 * target selection policy while dealing with multiple targets with same deployment.
 * <p>
 * Target selection is only used when discovery yields more than one URI as a result of its query to locate an EJB.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Jaikiran Pai
 */
public interface DiscoveredURISelector {

    /**
     * Selects and returns a URI from among the {@code eligibleUris} to handle the invocation on a deployment
     * represented by the passed in {@code locator}.  Implementations of this method must <em>not</em> return
     * {@code null} or any other node name which isn't in the {@code eligibleUris} list.
     * <p>
     * Note that the list sent in for {@code eligibleUris} is <em>indexed</em>, meaning that the {@code contains} operation
     * is guaranteed to run in constant time.
     *
     * @param eligibleUris an <em>indexed</em> list of the eligible nodes which can handle the deployment; not {@code null}, will not be empty
     * @param locator the locator of the EJB being invoked upon
     * @return the URI selection (must not be {@code null}, must be one of the URIs from {@code eligibleUris})
     */
    URI selectNode(final List<URI> eligibleUris, final EJBLocator<?> locator);

    /**
     * Create a deployment URI selector that prefers one or more favorite nodes, falling back to another selector if
     * none of the favorites are found.
     *
     * @param favorites the favorite nodes, in decreasing order of preference (must not be {@code null})
     * @param fallback the fallback selector (must not be {@code null})
     * @return the selector (not {@code null})
     */
    static DiscoveredURISelector favorite(Collection<URI> favorites, DiscoveredURISelector fallback) {
        Assert.checkNotNullParam("favorites", favorites);
        Assert.checkNotNullParam("fallback", fallback);
        return (eligibleUris, locator) -> {
            for (URI favorite : favorites) {
                if (eligibleUris.contains(favorite)) {
                    return favorite;
                }
            }
            return fallback.selectNode(eligibleUris, locator);
        };
    }

    /**
     * A deployment URI selector which prefers the first URI always.  This will generally avoid load balancing in most
     * cases.
     */
    DiscoveredURISelector FIRST = (eligibleUris, locator) -> eligibleUris.iterator().next();

    /**
     * A deployment URI selector which randomly chooses the next URI.  This will generally provide the best possible
     * load balancing over a large number of requests.
     */
    DiscoveredURISelector RANDOM = (eligibleUris, locator) -> eligibleUris.get(ThreadLocalRandom.current().nextInt(eligibleUris.size()));

    /**
     * A deployment URI selector which uses an approximate round-robin policy among all of the eligible URIs.  Note
     * that the round-robin URI count may be shared among multiple node sets, thus certain specific usage patterns
     * <em>may</em> defeat the round-robin behavior.
     */
    DiscoveredURISelector ROUND_ROBIN = new DiscoveredURISelector() {
        private final AtomicInteger counter = new AtomicInteger();

        public URI selectNode(final List<URI> eligibleUris, final EJBLocator<?> locator) {
            final int length = eligibleUris.size();
            assert length > 0;
            return eligibleUris.get(Math.floorMod(counter.getAndIncrement(), length));
        }
    };
}
