/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

import static org.jboss.ejb._private.Keys.AUTHENTICATION_CONTEXT_ATTACHMENT_KEY;

import org.jboss.ejb.client.annotation.ClientInterceptorPriority;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * EJB client interceptor to capture the {@code AuthenticationContext} after any application interceptors.
 *
 * @author <a href="mailto:darran.lofthouse@redhat.com">Darran Lofthouse</a>
 */
@ClientInterceptorPriority(AuthenticationContextEJBClientInterceptor.PRIORITY)
public class AuthenticationContextEJBClientInterceptor implements EJBClientInterceptor {

    public static final int PRIORITY = ClientInterceptorPriority.JBOSS_AFTER + 25;

    @Override
    public SessionID handleSessionCreation(EJBSessionCreationInvocationContext context) throws Exception {
        return call(context::proceed, context);
    }

    @Override
    public void handleInvocation(EJBClientInvocationContext context) throws Exception {
        call(() -> {
            context.sendRequest();
            return null;
        }, context);
    }

    @Override
    public Object handleInvocationResult(EJBClientInvocationContext context) throws Exception {
        return call(context::getResult, context);
    }

    private <T> T call(ExceptionSupplier<T, Exception> action, AbstractInvocationContext context) throws Exception {
        final AuthenticationContext captured = AuthenticationContext.captureCurrent();
        final AuthenticationContext previous = context.putAttachment(AUTHENTICATION_CONTEXT_ATTACHMENT_KEY, captured);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                context.removeAttachment(AUTHENTICATION_CONTEXT_ATTACHMENT_KEY);
            } else {
                context.putAttachment(AUTHENTICATION_CONTEXT_ATTACHMENT_KEY, previous);
            }
        }

    }

}
