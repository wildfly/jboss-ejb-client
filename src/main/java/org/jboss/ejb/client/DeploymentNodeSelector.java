/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.wildfly.common.Assert;

/**
 * A selector which selects and returns a node, from among the passed eligible nodes, that can handle a specific
 * deployment within a EJB client context. Typical usage of {@link DeploymentNodeSelector} involves load balancing
 * calls to multiple nodes which can all handle the same deployment. This allows the application to have a deterministic
 * node selection policy while dealing with multiple nodes with same deployment.
 * <p>
 * Node selection is only used when discovery yields nodes as a result of its query to locate an EJB.  If discovery
 * yields a URI or cluster, this mechanism is not used.
 *
 * @author Jaikiran Pai
 */
public interface DeploymentNodeSelector {

    /**
     * Selects and returns a node from among the <code>eligibleNodes</code> to handle the invocation on a deployment
     * represented by the passed <code>appName</code>, <code>moduleName</code> and <code>distinctName</code> combination.
     * Implementations of this method must <b>not</b> return null or any other node name which isn't in the
     * <code>eligibleNodes</code>
     *
     * @param eligibleNodes the eligible nodes which can handle the deployment; not {@code null}, will not be empty
     * @param appName       the app name of the deployment
     * @param moduleName    the module name of the deployment
     * @param distinctName  the distinct name of the deployment
     * @return the node selection (must not be {@code null})
     */
    String selectNode(final String[] eligibleNodes, final String appName, final String moduleName, final String distinctName);

    /**
     * Create a deployment node selector that prefers one or more favorite nodes, falling back to another selector if
     * none of the favorites are found.
     *
     * @param favorites the favorite nodes, in decreasing order of preference (must not be {@code null})
     * @param fallback the fallback selector (must not be {@code null})
     * @return the selector (not {@code null})
     */
    static DeploymentNodeSelector favorite(Collection<String> favorites, DeploymentNodeSelector fallback) {
        Assert.checkNotNullParam("favorites", favorites);
        Assert.checkNotNullParam("fallback", fallback);
        return (eligibleNodes, appName, moduleName, distinctName) -> {
            final HashSet<String> set = new HashSet<String>(eligibleNodes.length);
            Collections.addAll(set, eligibleNodes);
            for (String favorite : favorites) {
                if (set.contains(favorite)) {
                    return favorite;
                }
            }
            return fallback.selectNode(eligibleNodes, appName, moduleName, distinctName);
        };
    }

    /**
     * A deployment node selector which prefers the first node always.  This will generally avoid load balancing in most
     * cases.
     */
    DeploymentNodeSelector FIRST = (eligibleNodes, appName, moduleName, distinctName) -> eligibleNodes[0];

    /**
     * A deployment node selector which randomly chooses the next node.  This will generally provide the best possible
     * load balancing over a large number of requests.
     */
    DeploymentNodeSelector RANDOM = (eligibleNodes, appName, moduleName, distinctName) -> eligibleNodes[ThreadLocalRandom.current().nextInt(eligibleNodes.length)];

    /**
     * A deployment node selector which uses an approximate round-robin policy among all of the eligible nodes.  Note
     * that the round-robin node count may be shared among multiple node sets, thus certain specific usage patterns
     * <em>may</em> defeat the round-robin behavior.
     */
    DeploymentNodeSelector ROUND_ROBIN = new DeploymentNodeSelector() {
        private final AtomicInteger counter = new AtomicInteger();

        public String selectNode(final String[] eligibleNodes, final String appName, final String moduleName, final String distinctName) {
            final int length = eligibleNodes.length;
            assert length > 0;
            return eligibleNodes[Math.floorMod(counter.getAndIncrement(), length)];
        }
    };
}
