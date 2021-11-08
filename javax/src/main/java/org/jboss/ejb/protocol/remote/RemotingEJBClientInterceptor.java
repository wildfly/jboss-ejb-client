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

import javax.ejb.NoSuchEJBException;

import org.jboss.ejb.client.AbstractInvocationContext;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClientInterceptor;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBSessionCreationInvocationContext;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.annotation.ClientInterceptorPriority;

import static org.jboss.ejb.client.annotation.ClientInterceptorPriority.JBOSS_AFTER;

/**
 * The interceptor responsible for relaying invocation information back into the Remoting-based discovery system.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
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

    public SessionID handleSessionCreation(final EJBSessionCreationInvocationContext context) throws Exception {
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
            final EJBLocator<?> locator = context.getLocator();
            if (locator.isStateful()) {
                // Fix for EJBCLIENT-333: NoSuchEJBException can designate a bean (SFSB, SLSB) not being deployed
                // on a server, or a session for a bean (SFSB) not being present on a server. In case of SFSB,
                // set target affinity and weak affinity to NONE, let the NoSuchEJBException be propagated
                // back through the interceptors, and not remove its module entry from discovered node registry.
                // Removing the entire module will cause subsequent invocations of SLSB on the target server to fail
                // unexpectedly.
                context.setTargetAffinity(Affinity.NONE);
                context.setWeakAffinity(Affinity.NONE);
            } else {
                final RemoteEJBReceiver ejbReceiver = context.getClientContext().getAttachment(RemoteTransportProvider.ATTACHMENT_KEY);
                if (ejbReceiver != null) {
                    final EJBClientChannel ejbClientChannel = context.getAttachment(RemoteEJBReceiver.EJBCC_KEY);
                    if (ejbClientChannel != null) {
                        final NodeInformation nodeInformation = ejbReceiver.getDiscoveredNodeRegistry().getNodeInformation(((NodeAffinity) targetAffinity).getNodeName());
                        if (nodeInformation != null) {
                            nodeInformation.removeModule(ejbClientChannel, locator.getIdentifier().getModuleIdentifier());
                        }
                    }
                }
            }
        }
    }
}
