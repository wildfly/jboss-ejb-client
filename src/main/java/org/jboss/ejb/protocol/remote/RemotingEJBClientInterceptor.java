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

package org.jboss.ejb.protocol.remote;

import static org.jboss.ejb.client.annotation.ClientInterceptorPriority.JBOSS_AFTER;

import javax.ejb.NoSuchEJBException;

import org.jboss.ejb.client.AbstractInvocationContext;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClientInterceptor;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBSessionCreationInvocationContext;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.annotation.ClientInterceptorPriority;
import org.kohsuke.MetaInfServices;

/**
 * The interceptor responsible for relaying invocation information back into the Remoting-based discovery system.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
@ClientInterceptorPriority(RemotingEJBClientInterceptor.PRIORITY)
public final class RemotingEJBClientInterceptor implements EJBClientInterceptor {

    /**
     * This interceptor's priority.
     */
    public static final int PRIORITY = JBOSS_AFTER + 200;

    public RemotingEJBClientInterceptor() {
    }

    public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
        context.sendRequest();
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        try {
            return context.getResult();
        } catch (NoSuchEJBException e) {
            // EJB is not present on target node!
            removeNode(context);
            throw e;
        }
    }

    public StatefulEJBLocator<?> handleSessionCreation(final EJBSessionCreationInvocationContext context) throws Exception {
        try {
            return context.proceed();
        } catch (NoSuchEJBException e) {
            // EJB is not present on target node!
            removeNode(context);
            throw e;
        }
    }

    private void removeNode(final AbstractInvocationContext context) {
        final Affinity targetAffinity = context.getTargetAffinity();
        if (targetAffinity instanceof NodeAffinity) {
            final RemoteEJBReceiver ejbReceiver = context.getClientContext().getAttachment(RemoteTransportProvider.ATTACHMENT_KEY);
            if (ejbReceiver != null) {
                final EJBClientChannel ejbClientChannel = context.getAttachment(RemoteEJBReceiver.EJBCC_KEY);
                if (ejbClientChannel != null) {
                    final NodeInformation nodeInformation = ejbReceiver.getDiscoveredNodeRegistry().getNodeInformation(((NodeAffinity) targetAffinity).getNodeName());
                    if (nodeInformation != null) {
                        nodeInformation.removeModule(ejbClientChannel, context.getLocator().getIdentifier().getModuleIdentifier());
                    }
                }
            }
        }
    }
}
