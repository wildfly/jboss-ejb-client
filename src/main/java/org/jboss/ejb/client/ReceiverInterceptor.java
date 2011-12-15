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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ReceiverInterceptor implements EJBClientInterceptor {

    public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
        final EJBClientContext clientContext = context.getClientContext();
        final EJBClientTransactionContext transactionContext = EJBClientTransactionContext.getCurrent();
        final EJBLocator<?> locator = context.getLocator();
        final String transactionNode = transactionContext.getTransactionNode();
        final EJBReceiverContext receiverContext;
        if (transactionNode != null) {
            receiverContext = clientContext.requireNodeEJBReceiverContext(transactionNode);
            if (! receiverContext.getReceiver().acceptsModule(locator.getAppName(), locator.getModuleName(), locator.getDistinctName())) {
                throw new IllegalStateException(String.format("Node of the current transaction (%s) does not accept (%s)", locator));
            }
            final Affinity affinity = locator.getAffinity();
            if (affinity instanceof NodeAffinity) {
                if (! transactionNode.equals(((NodeAffinity)affinity).getNodeName())) {
                    throw new IllegalStateException(String.format("Node of the current transaction (%s) does not accept (%s)", transactionNode, locator));
                }
            } else if (affinity instanceof ClusterAffinity) {
                if (! clientContext.clusterContains(((ClusterAffinity)affinity).getClusterName(), transactionNode)) {
                    throw new IllegalStateException(String.format("Node of the current transaction (%s) does not accept (%s)", transactionNode, locator));
                }
            }
        } else {
            final Affinity affinity = locator.getAffinity();
            if (affinity instanceof NodeAffinity) {
                receiverContext = clientContext.requireNodeEJBReceiverContext(((NodeAffinity)affinity).getNodeName());
            } else if (affinity instanceof ClusterAffinity) {
                final Affinity weakAffinity = context.getInvocationHandler().getWeakAffinity();
                if (weakAffinity instanceof NodeAffinity) {
                    final EJBReceiver nodeReceiver = clientContext.getNodeEJBReceiver(((NodeAffinity) weakAffinity).getNodeName());
                    if (nodeReceiver != null && clientContext.clusterContains(((ClusterAffinity) affinity).getClusterName(), nodeReceiver.getNodeName())) {
                        receiverContext = clientContext.requireEJBReceiverContext(nodeReceiver);
                    } else {
                        receiverContext = clientContext.requireClusterEJBReceiverContext(((ClusterAffinity)affinity).getClusterName());
                    }
                } else {
                    receiverContext = clientContext.requireClusterEJBReceiverContext(((ClusterAffinity)affinity).getClusterName());
                }
            } else if (affinity == Affinity.NONE) {
                final Affinity weakAffinity = context.getInvocationHandler().getWeakAffinity();
                if (weakAffinity instanceof NodeAffinity) {
                    final EJBReceiver receiver = clientContext.getNodeEJBReceiver(((NodeAffinity) weakAffinity).getNodeName());
                    if (receiver != null) {
                        receiverContext = clientContext.requireEJBReceiverContext(receiver);
                    } else {
                        receiverContext = clientContext.requireEJBReceiverContext(clientContext.requireEJBReceiver(locator.getAppName(), locator.getModuleName(), locator.getDistinctName()));
                    }
                } else if (weakAffinity instanceof ClusterAffinity) {
                    final EJBReceiver receiver = clientContext.getClusterEJBReceiver(((ClusterAffinity) weakAffinity).getClusterName());
                    if (receiver != null) {
                        receiverContext = clientContext.requireEJBReceiverContext(receiver);
                    } else {
                        receiverContext = clientContext.requireEJBReceiverContext(clientContext.requireEJBReceiver(locator.getAppName(), locator.getModuleName(), locator.getDistinctName()));
                    }
                } else {
                    receiverContext = clientContext.requireEJBReceiverContext(clientContext.requireEJBReceiver(locator.getAppName(), locator.getModuleName(), locator.getDistinctName()));
                }
            } else {
                // should never happen
                throw new IllegalStateException("Unknown affinity type");
            }
        }
        context.setReceiverInvocationContext(new EJBReceiverInvocationContext(context, receiverContext));
        context.sendRequest();
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        return context.getResult();
    }
}
