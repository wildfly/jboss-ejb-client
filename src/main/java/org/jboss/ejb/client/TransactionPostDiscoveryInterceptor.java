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

import static org.jboss.ejb.client.TransactionInterceptor.APPLICATIONS;
import static org.jboss.ejb.client.TransactionInterceptor.Application;
import static org.jboss.ejb.client.TransactionInterceptor.PREFERRED_DESTINATIONS;
import static org.jboss.ejb.client.TransactionInterceptor.toApplication;

import java.net.URI;
import java.util.concurrent.ConcurrentMap;

import jakarta.ejb.NoSuchEJBException;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientInterceptorPriority;
import org.wildfly.transaction.client.ContextTransactionManager;


/**
 * The client interceptor which associates discovery output with the current
 * transaction of the invocation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:jason.greene@redhat.com">Jason T. Greene</a>
 */
@ClientInterceptorPriority(TransactionPostDiscoveryInterceptor.PRIORITY)
public final class TransactionPostDiscoveryInterceptor implements EJBClientInterceptor {
    private static final ContextTransactionManager transactionManager = ContextTransactionManager.getInstance();

    static final AttachmentKey<Application> APPLICATION = new AttachmentKey<>();


    /**
     * This interceptor's priority.
     */
    public static final int PRIORITY = ClientInterceptorPriority.JBOSS_AFTER + 150;

    /**
     * Construct a new instance.
     */
    public TransactionPostDiscoveryInterceptor() {
    }

    public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
        ConcurrentMap<Application, URI> applications = context.getAttachment(APPLICATIONS);
        if (applications != null) {
            URI destination = context.getDestination();
            Application registered = updateOrFollowApplication(context, applications, true);
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("TransactionPostDiscoveryInterceptor: calling handleInvocation, destination = %s, application = %s", destination, registered);
            }
            try {
                context.sendRequest();
            } catch (NoSuchEJBException | RequestSendFailedException e) {
                if (registered != null) {
                    // Clear sticky association only if this path registered it
                    applications.remove(registered, destination);
                }
                context.removeAttachment(APPLICATIONS);
                context.removeAttachment(PREFERRED_DESTINATIONS);
                context.removeAttachment(APPLICATION);
                throw e;
            }
        } else {
            context.sendRequest();
        }
    }

    public SessionID handleSessionCreation(final EJBSessionCreationInvocationContext context) throws Exception {
        ConcurrentMap<Application, URI> applications = context.getAttachment(APPLICATIONS);
        if (applications != null) {
            URI destination = context.getDestination();
            Application registered = updateOrFollowApplication(context, applications, false);
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("TransactionPostDiscoveryInterceptor: calling handleSessionCreation, destination = %s, application = %s", destination, registered);
            }
            try {
                return context.proceed();
            } catch (NoSuchEJBException | RequestSendFailedException e) {
                if (registered != null) {
                    // Clear sticky association only if this path registered it
                    applications.remove(registered, destination);
                }
                throw e;
            } finally {
                context.removeAttachment(APPLICATIONS);
                context.removeAttachment(PREFERRED_DESTINATIONS);
            }
        }

        return context.proceed();
    }

    private Application updateOrFollowApplication(AbstractInvocationContext context, ConcurrentMap<Application, URI> applications, boolean register) {
        URI destination = context.getDestination();
        if (destination != null) {
            EJBIdentifier identifier = context.getLocator().getIdentifier();
            Application application = toApplication(identifier);
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("TransactionPostDiscoveryInterceptor: calling updateOrFollowApplication, destination = %s, application = %s", destination, application);
            }
            URI existing = applications.putIfAbsent(application, destination);
            if (existing != null) {
                if (Logs.INVOCATION.isDebugEnabled()) {
                    Logs.INVOCATION.debugf("TransactionPostDiscoveryInterceptor: calling updateOrFollowApplication, updating from map, destination = %s", existing);
                }
                // Someone else set a mapping, use it instead
                context.setDestination(existing);
            } else {
                if (register) {
                    if (Logs.INVOCATION.isDebugEnabled()) {
                        Logs.INVOCATION.debugf("TransactionPostDiscoveryInterceptor: calling updateOrFollowApplication, added destination for application, application = %s", application);
                    }
                    context.putAttachment(APPLICATION, application);
                }

                return application;
            }
        } else {
            Logs.TXN.trace("Failed assertion: a destination was supposed ot be present but wasn't");
        }

        return null;
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        ConcurrentMap<Application, URI> applications = context.getAttachment(APPLICATIONS);
        Application application = context.getAttachment(APPLICATION);
        URI destination = context.getDestination();
        try {
            return context.getResult();
        } catch (RequestSendFailedException | NoSuchEJBException e) {
            if (application != null) {
                applications.remove(application, destination);
            }
            throw e;
        }  finally {
            context.removeAttachment(APPLICATIONS);
            context.removeAttachment(PREFERRED_DESTINATIONS);
            context.removeAttachment(APPLICATION);
        }
    }
}
