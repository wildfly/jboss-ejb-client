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

import org.jboss.ejb._private.Logs;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * The context object for handling explicit (not implicit) EJB 3.x stateful session bean creation.  Implicit session
 * creation will be done automatically upon stateless invocation of a stateful EJB.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBSessionCreationInvocationContext extends AbstractInvocationContext {

    private final EJBClientContext.InterceptorList interceptorList;
    private int interceptorChainIndex;
    private boolean retry;

    EJBSessionCreationInvocationContext(final StatelessEJBLocator<?> locator, final EJBClientContext ejbClientContext, AuthenticationContext authenticationContext, final EJBClientContext.InterceptorList interceptorList) {
        super(locator, ejbClientContext, authenticationContext);
        this.interceptorList = interceptorList;
    }

    SessionID proceedInitial() throws Exception {
        AuthenticationContext authenticationContext = getAuthenticationContext();
        if (authenticationContext != null) {
            return authenticationContext.runExFunction(EJBSessionCreationInvocationContext::proceed, this);
        } else {
            return proceed();
        }
    }

    /**
     * Proceed with the next interceptor in the chain, calling the resolved receiver in the end.
     *
     * @return the session ID (not {@code null})
     * @throws Exception if the EJB session creation failed for some reason
     */
    public SessionID proceed() throws Exception {
        final int idx = interceptorChainIndex++;
        try {
            final EJBClientInterceptorInformation[] chain = interceptorList.getInformation();
            if (idx > chain.length) {
                throw Assert.unreachableCode();
            }
            if (chain.length == idx) {
                final URI destination = getDestination();
                final EJBReceiver receiver = getClientContext().resolveReceiver(destination, getLocator());
                if (Logs.INVOCATION.isDebugEnabled()) {
                    Logs.INVOCATION.debugf("session creation proceed(): setting receiver, remote destination is: %s", destination);
                }
                setReceiver(receiver);

                final SessionID sessionID = receiver.createSession(new EJBReceiverSessionCreationContext(this));
                if (sessionID == null) {
                    throw Logs.INVOCATION.nullSessionID(receiver, getLocator().asStateless());
                }
                retry = false;
                return sessionID;
            } else {
                if (Logs.INVOCATION.isDebugEnabled()) {
                    Logs.INVOCATION.debugf("session creation proceed(): calling interceptor: %s", chain[idx].getInterceptorInstance());
                }
                return chain[idx].getInterceptorInstance().handleSessionCreation(this);
            }
        } finally {
            interceptorChainIndex --;
        }
    }

    public void requestRetry() {
        retry = true;
    }

    boolean shouldRetry() {
        return retry;
    }
}
