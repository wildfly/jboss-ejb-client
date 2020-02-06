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
import static org.jboss.ejb.client.DiscoveryEJBClientInterceptor.addInvocationBlackListedDestination;
import static org.jboss.ejb.client.DiscoveryEJBClientInterceptor.isBlackListed;
import static org.jboss.ejb.client.DiscoveryEJBClientInterceptor.shouldBlacklist;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.ejb.NoSuchEJBException;

import org.jboss.ejb._private.Keys;
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
        final NamingProvider namingProvider = context.getProxyAttachment(Keys.NAMING_PROVIDER_ATTACHMENT_KEY);
        if (namingProvider != null) {
            // make sure the naming provider is available to invocations
            context.putAttachment(Keys.NAMING_PROVIDER_ATTACHMENT_KEY, namingProvider);
        }
        if (namingProvider == null || context.getDestination() != null || context.getLocator().getAffinity() != Affinity.NONE) {
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("NamingEJBClientInterceptor: calling handleInvocation: skipping missing target");
            }
            context.putAttachment(SKIP_MISSING_TARGET, Boolean.TRUE);
            context.sendRequest();
        } else {
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("NamingEJBClientInterceptor: calling handleInvocation: setting destination");
            }
            if (setDestination(context, namingProvider)) try {
                context.sendRequest();
            } catch (NoSuchEJBException | RequestSendFailedException e){
                processMissingTarget(context, e);
                throw e;
            } else {
                throw Logs.INVOCATION.noMoreDestinations();
            }
        }
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        try {
            return context.getResult();
        } catch (NoSuchEJBException | RequestSendFailedException e){
            if (context.getAttachment(SKIP_MISSING_TARGET) != Boolean.TRUE) {
                processMissingTarget(context, e);
            }
            throw e;
        } finally {
            context.removeAttachment(SKIP_MISSING_TARGET);
        }
    }

    public SessionID handleSessionCreation(final EJBSessionCreationInvocationContext context) throws Exception {
        final NamingProvider namingProvider = context.getAttachment(Keys.NAMING_PROVIDER_ATTACHMENT_KEY);
        if (namingProvider == null || context.getDestination() != null || context.getLocator().getAffinity() != Affinity.NONE) {
            return context.proceed();
        } else {
            if (setDestination(context, namingProvider)) try {
                if (Logs.INVOCATION.isDebugEnabled()) {
                    Logs.INVOCATION.debugf("NamingEJBClientInterceptor: called setNamingDestination: destination = %s", context.getDestination());
                }
                SessionID theSessionID = context.proceed();
                if (Logs.INVOCATION.isDebugEnabled()) {
                    Logs.INVOCATION.debugf("NamingEJBClientInterceptor: returned from handleSessionCreation: sessionID = %s", theSessionID);
                }
                // we should setup session affinities here
                if (context instanceof EJBSessionCreationInvocationContext) {
                    // this will convert strong=NONE to target or URI (if target not defined)
                    // this will also convert strong=Cluster and weak = NONE to strong=Cluster and weak = target or URI (if target not defined)
                    DiscoveryEJBClientInterceptor.setupSessionAffinities((EJBSessionCreationInvocationContext)context);
                    if (Logs.INVOCATION.isDebugEnabled()) {
                        Logs.INVOCATION.debugf("NamingEJBClientInterceptor: called DiscoveryEJBClientInterceptor.setupSessionAffinities");
                    }
                }
                return theSessionID;
            } catch (NoSuchEJBException | RequestSendFailedException e) {
                processMissingTarget(context, e);
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
                if (locator.getAffinity() == Affinity.NONE) {
                    final Affinity weakAffinity = context.getWeakAffinity();
                    if (weakAffinity == Affinity.NONE) {
                        return setNamingDestination(context, namingProvider);
                    }
                }
            }
        }
        return true;
    }

    static boolean setNamingDestination(final AbstractInvocationContext context, final NamingProvider namingProvider) {
        final ProviderEnvironment providerEnvironment = namingProvider.getProviderEnvironment();
        final List<URI> providerUris = providerEnvironment.getProviderUris();
        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("NamingEJBClientInterceptor: calling setNamingDestination: providerURIs = %s, invocationContext type = %s", providerUris.toString(), context.getClass().getName());
        }
        // select the subset of providerURIs that agree with TransactionInterceptor's choices
        List<URI> uris = findPreferredURIs(context, providerUris);
        // if there are none, use all non-blacklisted providerURIs instead
        if (uris == null) {
            uris = new ArrayList<>(providerUris.size());
            for (URI uri : providerUris) {
                if (!isBlackListed(context, uri)) {
                    uris.add(uri);
                }
            }
        }

        final int size = uris.size();
        if (size == 0) {
            // we can't discover the location; fail
            return false;
        } else if (size == 1) {
            context.setDestination(uris.get(0));
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("NamingEJBClientInterceptor: setting destination: (size == 1), destination = %s", context.getDestination());
            }
            return true;
        } else {
            context.setDestination(uris.get(ThreadLocalRandom.current().nextInt(size)));
            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("NamingEJBClientInterceptor: setting destination: (size > 1), destination = %s", context.getDestination());
            }
        }

        if (context instanceof EJBSessionCreationInvocationContext) {
            DiscoveryEJBClientInterceptor.setupSessionAffinities((EJBSessionCreationInvocationContext)context);
        }

        return true;
    }

    private static List<URI> findPreferredURIs(AbstractInvocationContext context, List<URI> uris) {
        Collection<URI> attachment = context.getAttachment(TransactionInterceptor.PREFERRED_DESTINATIONS);
        if (attachment == null) {
            return null;
        }

        HashSet<URI> preferred = new HashSet<>(attachment);
        List<URI> result = null;
        for (URI check : uris) {
            if (preferred.contains(check) && !isBlackListed(context, check)) {
                if (result == null) {
                    result = new ArrayList<>(preferred.size());
                }
                result.add(check);
            }
        }
        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("NamingEJBClientInterceptor: computing preferred URIs: URIs = %s, preferred URIs = %s", uris, result);
        }

        return result;
    }

    private void processMissingTarget(final AbstractInvocationContext context, Exception cause) {
        final URI destination = context.getDestination();
        if (destination == null || context.getTargetAffinity() == Affinity.LOCAL) {
            // some later interceptor cleared it out on us
            return;
        }

        if (Logs.INVOCATION.isDebugEnabled()) {
            Logs.INVOCATION.debugf("NamingEJBClientInterceptor: missing target, *** retrying ***: locator = %s", context.getLocator());
        }

        // Oops, we got some wrong information!
        if(shouldBlacklist(cause)){
            addBlackListedDestination(destination);
        } else {
            addInvocationBlackListedDestination(context, destination);
        }

        final EJBLocator<?> locator = context.getLocator();
        if (! (locator.getAffinity() instanceof ClusterAffinity)) {
            // it *was* "none" affinity, but it has been relocated; locate it back again
            context.setLocator(locator.withNewAffinity(Affinity.NONE));

            if (Logs.INVOCATION.isDebugEnabled()) {
                Logs.INVOCATION.debugf("NamingEJBClientInterceptor: resetting strong affinity = %s", context.getLocator().getAffinity());
            }

        }
        // clear the weak affinity so that cluster invocations can be re-targeted.
        context.setWeakAffinity(Affinity.NONE);
        context.setTargetAffinity(null);
        context.setDestination(null);
        context.requestRetry();
    }
}
