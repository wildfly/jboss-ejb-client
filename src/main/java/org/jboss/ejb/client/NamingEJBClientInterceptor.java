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

import static org.jboss.ejb.client.DiscoveryEJBClientInterceptor.addBlackListedDestination;
import static org.jboss.ejb.client.DiscoveryEJBClientInterceptor.isBlackListed;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.ejb.NoSuchEJBException;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientInterceptorPriority;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.ProviderEnvironment;

/**
 * EJB client interceptor to discover a target location based on naming context information in the EJB proxy.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@ClientInterceptorPriority(NamingEJBClientInterceptor.PRIORITY)
public final class NamingEJBClientInterceptor implements EJBClientInterceptor {
    /**
     * This interceptor's priority.
     */
    public static final int PRIORITY = ClientInterceptorPriority.JBOSS_AFTER + 50;

    private static final AttachmentKey<Boolean> SKIP_MISSING_TARGET = new AttachmentKey<>();

    public NamingEJBClientInterceptor() {
    }

    public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
        final NamingProvider namingProvider = context.getProxyAttachment(EJBRootContext.NAMING_PROVIDER_ATTACHMENT_KEY);
        if (namingProvider == null || context.getDestination() != null || ! isNoneOrCluster(context.getLocator().getAffinity())) {
            context.putAttachment(SKIP_MISSING_TARGET, Boolean.TRUE);
            context.sendRequest();
        } else {
            if (setDestination(context, namingProvider)) try {
                context.sendRequest();
            } catch (NoSuchEJBException | RequestSendFailedException e) {
                processMissingTarget(context);
                throw e;
            } else {
                throw Logs.INVOCATION.noMoreDestinations();
            }
        }
    }

    private boolean isNoneOrCluster(final Affinity affinity) {
        return affinity == Affinity.NONE || affinity instanceof ClusterAffinity;
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        try {
            return context.getResult();
        } catch (NoSuchEJBException | RequestSendFailedException e) {
            if (context.getAttachment(SKIP_MISSING_TARGET) != Boolean.TRUE) {
                processMissingTarget(context);
            }
            throw e;
        } finally {
            context.removeAttachment(SKIP_MISSING_TARGET);
        }
    }

    public SessionID handleSessionCreation(final EJBSessionCreationInvocationContext context) throws Exception {
        final NamingProvider namingProvider = context.getAttachment(EJBRootContext.NAMING_PROVIDER_ATTACHMENT_KEY);
        if (namingProvider == null || context.getDestination() != null || ! isNoneOrCluster(context.getLocator().getAffinity())) {
            return context.proceed();
        } else {
            if (setDestination(context, namingProvider)) try {
                return context.proceed();
            } catch (NoSuchEJBException | RequestSendFailedException e) {
                processMissingTarget(context);
                throw e;
            } else {
                throw Logs.INVOCATION.noMoreDestinations();
            }
        }
    }

    private static boolean setDestination(final AbstractInvocationContext context, final NamingProvider namingProvider) {
        if (namingProvider != null) {
            final URI destination = context.getDestination();
            if (destination == null) {
                final EJBLocator<?> locator = context.getLocator();
                if (locator.getAffinity() == Affinity.NONE || locator.getAffinity() instanceof ClusterAffinity) {
                    final Affinity weakAffinity = context.getWeakAffinity();
                    if (weakAffinity == Affinity.NONE || weakAffinity instanceof ClusterAffinity) {
                        final ProviderEnvironment providerEnvironment = namingProvider.getProviderEnvironment();
                        final List<URI> providerUris = providerEnvironment.getProviderUris();
                        final List<URI> uris = new ArrayList<>(providerUris.size());
                        for (URI uri : providerUris) {
                            if (! isBlackListed(context, uri)) {
                                uris.add(uri);
                            }
                        }
                        final int size = uris.size();
                        if (size == 0) {
                            // we can't discover the location; fail
                            return false;
                        } else if (size == 1) {
                            context.setDestination(uris.get(0));
                            return true;
                        } else {
                            context.setDestination(uris.get(ThreadLocalRandom.current().nextInt(size)));
                        }
                    }
                }
            }
        }
        return true;
    }

    private void processMissingTarget(final AbstractInvocationContext context) {
        final URI destination = context.getDestination();
        if (destination == null) {
            // some later interceptor cleared it out on us
            return;
        }

        // Oops, we got some wrong information!
        addBlackListedDestination(context, destination);

        final EJBLocator<?> locator = context.getLocator();
        if (! (locator.getAffinity() instanceof ClusterAffinity)) {
            // it *was* "none" affinity, but it has been relocated; locate it back again
            context.setLocator(locator.withNewAffinity(Affinity.NONE));
        }
        // clear the weak affinity so that cluster invocations can be re-targeted.
        context.setWeakAffinity(Affinity.NONE);
        context.setTargetAffinity(null);
        context.setDestination(null);
        context.requestRetry();
    }
}
