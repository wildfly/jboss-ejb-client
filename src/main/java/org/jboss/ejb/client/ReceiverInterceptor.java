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

import org.jboss.logging.Logger;

import java.util.Set;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ReceiverInterceptor implements EJBClientInterceptor {

    private static final Logger logger = Logger.getLogger(ReceiverInterceptor.class);

    public void handleInvocation(final EJBClientInvocationContext invocationContext) throws Exception {
        final EJBClientContext clientContext = invocationContext.getClientContext();
        final EJBLocator<?> locator = invocationContext.getLocator();
        final Set<String> excludedNodes = invocationContext.getExcludedNodes();
        final EJBClientTransactionContext transactionContext = EJBClientTransactionContext.getCurrent();
        final String transactionNode = transactionContext == null ? null : transactionContext.getTransactionNode();
        final EJBReceiverContext receiverContext;
        if (transactionNode != null) {
            if (excludedNodes.contains(transactionNode)) {
                throw Logs.MAIN.txNodeIsExcludedForInvocation(transactionNode, invocationContext);
            }
            receiverContext = clientContext.requireNodeEJBReceiverContext(transactionNode);
            if (!receiverContext.getReceiver().acceptsModule(locator.getAppName(), locator.getModuleName(), locator.getDistinctName())) {
                throw Logs.MAIN.nodeDoesNotAcceptLocator(transactionNode, locator);
            }
            final Affinity affinity = locator.getAffinity();
            if (affinity instanceof NodeAffinity) {
                if (!transactionNode.equals(((NodeAffinity) affinity).getNodeName())) {
                    throw Logs.MAIN.nodeDoesNotAcceptLocator(transactionNode, locator);
                }
            } else if (affinity instanceof ClusterAffinity) {
                if (!clientContext.clusterContains(((ClusterAffinity) affinity).getClusterName(), transactionNode)) {
                    throw Logs.MAIN.nodeDoesNotAcceptLocator(transactionNode, locator);
                }
            }
        } else {
            final Affinity affinity = locator.getAffinity();
            if (affinity instanceof NodeAffinity) {
                final String nodeName = ((NodeAffinity) affinity).getNodeName();
                if (excludedNodes.contains(nodeName)) {
                    throw Logs.MAIN.requiredNodeExcludedFromInvocation(locator, nodeName, invocationContext);
                }
                receiverContext = clientContext.requireNodeEJBReceiverContext(nodeName);
            } else if (affinity instanceof ClusterAffinity) {
                final Affinity weakAffinity = invocationContext.getInvocationHandler().getWeakAffinity();
                if (weakAffinity instanceof NodeAffinity) {
                    final String nodeName = ((NodeAffinity) weakAffinity).getNodeName();
                    final EJBReceiver nodeReceiver;
                    // ignore the weak affinity if the node has been marked as excluded for this invocation context
                    if (excludedNodes.contains(nodeName)) {
                        logger.debug("Ignoring weak affinity on node " + nodeName + " since that node has been marked as excluded for invocation context " + invocationContext);
                        nodeReceiver = null;
                    } else {
                        nodeReceiver = clientContext.getNodeEJBReceiver(nodeName);
                    }
                    if (nodeReceiver != null && clientContext.clusterContains(((ClusterAffinity) affinity).getClusterName(), nodeReceiver.getNodeName())) {
                        receiverContext = clientContext.requireEJBReceiverContext(nodeReceiver);
                    } else {
                        receiverContext = clientContext.requireClusterEJBReceiverContext(invocationContext, ((ClusterAffinity) affinity).getClusterName());
                        // if it works we want to stay here
                        invocationContext.getInvocationHandler().setWeakAffinity(new NodeAffinity(receiverContext.getReceiver().getNodeName()));
                    }
                } else {
                    receiverContext = clientContext.requireClusterEJBReceiverContext(invocationContext, ((ClusterAffinity) affinity).getClusterName());
                    // if it works we want to stay here
                    invocationContext.getInvocationHandler().setWeakAffinity(new NodeAffinity(receiverContext.getReceiver().getNodeName()));
                }
            } else if (affinity == Affinity.NONE) {
                final Affinity weakAffinity = invocationContext.getInvocationHandler().getWeakAffinity();
                if (weakAffinity instanceof NodeAffinity) {
                    final String nodeName = ((NodeAffinity) weakAffinity).getNodeName();
                    final EJBReceiver nodeReceiver;
                    // ignore the weak affinity if the node has been marked as excluded for this invocation context
                    if (excludedNodes.contains(nodeName)) {
                        logger.debug("Ignoring weak affinity on node " + nodeName + " since that node has been marked as excluded for invocation context " + invocationContext);
                        nodeReceiver = null;
                    } else {
                        nodeReceiver = clientContext.getNodeEJBReceiver(nodeName);
                    }
                    if (nodeReceiver != null) {
                        receiverContext = clientContext.requireEJBReceiverContext(nodeReceiver);
                    } else {
                        final EJBReceiver receiver = clientContext.requireEJBReceiver(invocationContext, locator.getAppName(), locator.getModuleName(), locator.getDistinctName());
                        receiverContext = clientContext.requireEJBReceiverContext(receiver);
                    }
                } else if (weakAffinity instanceof ClusterAffinity) {
                    final EJBReceiverContext clusterReceiverContext = clientContext.getClusterEJBReceiverContext(invocationContext, ((ClusterAffinity) weakAffinity).getClusterName());
                    if (clusterReceiverContext != null) {
                        receiverContext = clusterReceiverContext;
                    } else {
                        final EJBReceiver receiver = clientContext.requireEJBReceiver(invocationContext, locator.getAppName(), locator.getModuleName(), locator.getDistinctName());
                        receiverContext = clientContext.requireEJBReceiverContext(receiver);
                    }
                } else {
                    final EJBReceiver receiver = clientContext.requireEJBReceiver(invocationContext, locator.getAppName(), locator.getModuleName(), locator.getDistinctName());
                    receiverContext = clientContext.requireEJBReceiverContext(receiver);
                }
            } else {
                // should never happen
                throw new IllegalStateException("Unknown affinity type");
            }
        }
        invocationContext.setReceiverInvocationContext(new EJBReceiverInvocationContext(invocationContext, receiverContext));
        invocationContext.sendRequest();
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        return context.getResult();
    }
}
