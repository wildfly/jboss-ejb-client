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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.ejb._private.Logs;
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
 * @author <a href="mailto:wfink@redhat.com">Wolf Dieter Fink</a>
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
                    if (Logs.MAIN.isDebugEnabled()) {
                        Logs.MAIN.debugf("FAVORITE node %s for [app: %s, module: %s,  distinctname: %s]", favorite, appName, moduleName, distinctName);
                    }
                    return favorite;
                }
            }
            if(Logs.MAIN.isDebugEnabled()) {
                Logs.MAIN.debugf("FAVORITE no favorite found use fallback for [eligibleNodes %s, app: %s, module: %s,  distinctname: %s]", Arrays.deepToString(eligibleNodes), appName, moduleName, distinctName);
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
            if(Logs.MAIN.isTraceEnabled()) {
                Logs.MAIN.tracef("ROUND_ROBIN [nodes=%s appName=%s moduleName=%s, distinctName=%s]", Arrays.deepToString(eligibleNodes), appName, moduleName, distinctName);
            }
            String node = eligibleNodes[Math.floorMod(counter.getAndIncrement(), length)];
            if(Logs.MAIN.isDebugEnabled()) {
                Logs.MAIN.debugf("ROUND_ROBIN select node %s for [app: %s, module: %s,  distinctname: %s]", node, appName, moduleName, distinctName);
            }
            return node;
        }
    };

    /**
     * A deployment node selector which check the server name if inside and prefer it if available for selection.
     */
    DeploymentNodeSelector RANDOM_PREFER_LOCAL = new DeploymentNodeSelector() {
        private final String localNodeName = SecurityUtils.getString(SystemProperties.JBOSS_NODE_NAME);

        public String selectNode(final String[] eligibleNodes, final String appName, final String moduleName, final String distinctName) {
            if(Logs.MAIN.isTraceEnabled()) {
                Logs.MAIN.tracef("RANDOM_PREFER_LOCAL (%s) [nodes=%s appName=%s moduleName=%s, distinctName=%s]", localNodeName, Arrays.deepToString(eligibleNodes), appName, moduleName, distinctName);
            }
            // Just a single node available, so just return it
            if (eligibleNodes.length == 1) {
                return eligibleNodes[0];
            }
            // prefer local node if available
            if(localNodeName != null) {
	            for (final String eligibleNode : eligibleNodes) {
	                if (localNodeName.equals(eligibleNode)) {
                      if (Logs.MAIN.isDebugEnabled()) {
	                        Logs.MAIN.debugf("RANDOM_PREFER_LOCAL select local node %s for [app: %s, module: %s,  distinctname: %s]", eligibleNode, appName, moduleName, distinctName);
                      }
	                    return eligibleNode;
	                }
	            }
            }
            // select one randomly
            if (Logs.MAIN.isDebugEnabled()) {
                Logs.MAIN.debug("RANDOM_PREFER_LOCAL local node not avaialble, fallback to RANDOM selection");
            }
            return RANDOM.selectNode(eligibleNodes, appName, moduleName, distinctName);
        }
    };
}
