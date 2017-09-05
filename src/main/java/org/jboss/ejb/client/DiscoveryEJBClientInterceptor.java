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

import static java.security.AccessController.doPrivileged;
import static org.jboss.ejb.client.EJBClientContext.EJB_SERVICE_TYPE;
import static org.jboss.ejb.client.EJBClientContext.FILTER_ATTR_CLUSTER;
import static org.jboss.ejb.client.EJBClientContext.FILTER_ATTR_EJB_MODULE;
import static org.jboss.ejb.client.EJBClientContext.FILTER_ATTR_EJB_MODULE_DISTINCT;
import static org.jboss.ejb.client.EJBClientContext.FILTER_ATTR_NODE;
import static org.jboss.ejb.client.EJBClientContext.FILTER_ATTR_SOURCE_IP;
import static org.jboss.ejb.client.EJBClientContext.withSuppressed;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.ejb.NoSuchEJBException;
import javax.security.sasl.SaslException;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientInterceptorPriority;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.Inet;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.Discovery;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.ServicesQueue;

/**
 * The EJB client interceptor responsible for discovering the destination of a request.  If a destination is already
 * established, the interceptor passes the invocation through unmodified.  If the interceptor cannot locate the
 * destination, the invocation will proceed without a destination (and ultimately fail if no other interceptor
 * resolves the destination).
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@ClientInterceptorPriority(DiscoveryEJBClientInterceptor.PRIORITY)
public final class DiscoveryEJBClientInterceptor implements EJBClientInterceptor {
    private static final Supplier<Discovery> DISCOVERY_SUPPLIER = doPrivileged((PrivilegedAction<Supplier<Discovery>>) Discovery.getContextManager()::getPrivilegedSupplier);

    private static final String[] NO_STRINGS = new String[0];

    /**
     * This interceptor's priority.
     */
    public static final int PRIORITY = ClientInterceptorPriority.JBOSS_AFTER + 100;

    private static final AttachmentKey<Set<URI>> BL_KEY = new AttachmentKey<>();

    /**
     * Construct a new instance.
     */
    public DiscoveryEJBClientInterceptor() {
    }

    public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
        List<Throwable> problems = executeDiscovery(context);
        try {
            context.sendRequest();
        } catch (NoSuchEJBException | RequestSendFailedException e) {
            if (isTargetMissing(e)) {
                processMissingTarget(context);
            }
            throw e;
        } finally {
            if (problems != null) for (Throwable problem : problems) {
                context.addSuppressed(problem);
            }
        }
    }

    private boolean isTargetMissing(Exception e) {
        if (e.getCause() instanceof SaslException) {
            return false;
        }
        return true;
    }

    public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
        final Object result;
        try {
            result = context.getResult();
        } catch (NoSuchEJBException | RequestSendFailedException e) {
            if (isTargetMissing(e)) {
                processMissingTarget(context);
            }
            throw e;
        }
        final EJBLocator<?> locator = context.getLocator();
        if (locator.isStateful() && locator.getAffinity() instanceof ClusterAffinity && context.getWeakAffinity() == Affinity.NONE) {
            // set the weak affinity to the location of the session (in case it failed over)
            final Affinity targetAffinity = context.getTargetAffinity();
            if (targetAffinity != null) {
                context.setWeakAffinity(targetAffinity);
            } else {
                final URI destination = context.getDestination();
                if (destination != null) {
                    context.setWeakAffinity(URIAffinity.forUri(destination));
                }
                // if destination is null, then an interceptor set the location
            }
        }
        return result;
    }

    public SessionID handleSessionCreation(final EJBSessionCreationInvocationContext context) throws Exception {
        List<Throwable> problems = executeDiscovery(context);
        SessionID sessionID;
        try {
            sessionID = context.proceed();
        } catch (NoSuchEJBException | RequestSendFailedException e) {
            if (isTargetMissing(e)) {
                processMissingTarget(context);
            }
            throw withSuppressed(e, problems);
        } catch (Exception t) {
            throw withSuppressed(t, problems);
        }
        final EJBLocator<?> locator = context.getLocator();
        if (locator.getAffinity() == Affinity.NONE) {
            // physically relocate this EJB
            final Affinity targetAffinity = context.getTargetAffinity();
            if (targetAffinity != null) {
                context.setLocator(locator.withNewAffinity(targetAffinity));
            } else {
                final URI destination = context.getDestination();
                if (destination != null) {
                    context.setLocator(locator.withNewAffinity(URIAffinity.forUri(destination)));
                }
                // if destination is null, then an interceptor set the location
            }
        }
        if (locator.getAffinity() instanceof ClusterAffinity && context.getWeakAffinity() == Affinity.NONE) {
            // set the weak affinity to the location of the session!
            final Affinity targetAffinity = context.getTargetAffinity();
            if (targetAffinity != null) {
                context.setWeakAffinity(targetAffinity);
            } else {
                final URI destination = context.getDestination();
                if (destination != null) {
                    context.setWeakAffinity(URIAffinity.forUri(destination));
                }
                // if destination is null, then an interceptor set the location
            }
        }
        return sessionID;
    }

    private void processMissingTarget(final AbstractInvocationContext context) {
        final URI destination = context.getDestination();

        if (destination == null) {
            // nothing we can/should do.
            return;
        }
        // Oops, we got some wrong information!
        Set<URI> set = context.getAttachment(BL_KEY);
        if (set == null) {
            final Set<URI> appearing = context.putAttachmentIfAbsent(BL_KEY, set = new HashSet<>());
            if (appearing != null) {
                set = appearing;
            }
        }

        Logs.INVOCATION.tracef("Calling processMissingTarget(locator = %s, weak affinity = %s, missing target = %s)", context.getLocator(), context.getWeakAffinity(), destination);

        set.add(destination);
        // clear the weak affinity so that cluster invocations can be re-targeted.
        context.setWeakAffinity(Affinity.NONE);
        context.setTargetAffinity(null);
        context.setDestination(null);
        context.requestRetry();
    }

    ServicesQueue discover(final FilterSpec filterSpec) {
        return getDiscovery().discover(EJB_SERVICE_TYPE, filterSpec);
    }

    Discovery getDiscovery() {
        return DISCOVERY_SUPPLIER.get();
    }

    private List<Throwable> executeDiscovery(AbstractInvocationContext context) {
        if (context.getDestination() != null) {
            // Someone else discovered already!
            return null;
        }
        final EJBLocator<?> locator = context.getLocator();
        final Affinity affinity = locator.getAffinity();
        final Affinity weakAffinity = context.getWeakAffinity();

        Logs.INVOCATION.tracef("Calling executeDiscovery(locator = %s, weak affinity = %s)", locator, weakAffinity);

        FilterSpec filterSpec, fallbackFilterSpec;

        if (affinity instanceof URIAffinity || affinity == Affinity.LOCAL) {
            final Set<URI> blacklist = context.getAttachment(BL_KEY);
            if (blacklist == null || ! blacklist.contains(affinity.getUri())) {
                // Simple; just set a fixed destination
                context.setDestination(affinity.getUri());
                context.setTargetAffinity(affinity);
            }
            return null;
        } else if (affinity == Affinity.NONE && weakAffinity instanceof URIAffinity) {
            final Set<URI> blacklist = context.getAttachment(BL_KEY);
            if (blacklist == null || ! blacklist.contains(weakAffinity.getUri())) {
                context.setDestination(weakAffinity.getUri());
                context.setTargetAffinity(weakAffinity);
            }
            return null;
        } else if (affinity == Affinity.NONE && weakAffinity instanceof NodeAffinity) {
            filterSpec = FilterSpec.equal(FILTER_ATTR_NODE, ((NodeAffinity) weakAffinity).getNodeName());
            return doFirstMatchDiscovery(context, filterSpec, null);
        } else if (affinity instanceof NodeAffinity) {
            filterSpec = FilterSpec.equal(FILTER_ATTR_NODE, ((NodeAffinity) affinity).getNodeName());
            return doFirstMatchDiscovery(context, filterSpec, null);
        } else if (affinity instanceof ClusterAffinity) {
            if (weakAffinity instanceof NodeAffinity) {
                filterSpec = FilterSpec.all(
                    FilterSpec.equal(FILTER_ATTR_CLUSTER, ((ClusterAffinity) affinity).getClusterName()),
                    FilterSpec.equal(FILTER_ATTR_NODE, ((NodeAffinity) weakAffinity).getNodeName())
                );
                fallbackFilterSpec = FilterSpec.all(
                    FilterSpec.equal(FILTER_ATTR_CLUSTER, ((ClusterAffinity) affinity).getClusterName()),
                    FilterSpec.hasAttribute(FILTER_ATTR_NODE)
                );
                return doFirstMatchDiscovery(context, filterSpec, fallbackFilterSpec);
            } else if (weakAffinity instanceof URIAffinity || weakAffinity == Affinity.LOCAL) {
                // try direct
                context.setDestination(weakAffinity.getUri());
                context.setTargetAffinity(weakAffinity);
                return null;
            } else {
                // regular cluster discovery
                filterSpec = FilterSpec.all(
                    FilterSpec.equal(FILTER_ATTR_CLUSTER, ((ClusterAffinity) affinity).getClusterName())
                );
                return doClusterDiscovery(context, filterSpec);
            }
        } else {
            // no affinity in particular
            assert affinity == Affinity.NONE;
            filterSpec = getFilterSpec(locator.getIdentifier().getModuleIdentifier());
            return doAnyDiscovery(context, filterSpec, locator);
        }
    }

    private List<Throwable> doFirstMatchDiscovery(AbstractInvocationContext context, final FilterSpec filterSpec, final FilterSpec fallbackFilterSpec) {
        Logs.INVOCATION.tracef("Performing first-match discovery(locator = %s, weak affinity = %s, filter spec = %s)", context.getLocator(), context.getWeakAffinity(), filterSpec);
        final List<Throwable> problems;
        final Set<URI> set = context.getAttachment(BL_KEY);
        try (final ServicesQueue queue = discover(filterSpec)) {
            ServiceURL serviceURL;
            while ((serviceURL = queue.takeService()) != null) {
                final URI location = serviceURL.getLocationURI();
                if (set == null || ! set.contains(location)) {
                    // Got a match!  See if there's a node affinity to set for the invocation.
                    final AttributeValue nodeValue = serviceURL.getFirstAttributeValue(FILTER_ATTR_NODE);
                    if (nodeValue != null) {
                        context.setTargetAffinity(new NodeAffinity(nodeValue.toString()));
                    } else {
                        // just set the URI
                        context.setTargetAffinity(URIAffinity.forUri(location));
                    }
                    context.setDestination(location);
                    Logs.INVOCATION.tracef("Performed first-match discovery(target affinity = %s, destination = %s)", context.getTargetAffinity(), context.getDestination());
                    return queue.getProblems();
                }
            }
            problems = queue.getProblems();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Logs.MAIN.operationInterrupted();
        }
        // No good; fall back to cluster discovery.
        if (fallbackFilterSpec != null) {
            assert context.getLocator().getAffinity() instanceof ClusterAffinity;
            Logs.INVOCATION.tracef("Performed first-match discovery, no match, falling back to cluster discovery");
            final List<Throwable> problems2 = doClusterDiscovery(context, fallbackFilterSpec);
            if (problems2.isEmpty()) {
                return problems;
            } else if (problems.isEmpty()) {
                return problems2;
            } else {
                final ArrayList<Throwable> problems3 = new ArrayList<>(problems.size() + problems2.size());
                problems3.addAll(problems);
                problems3.addAll(problems2);
                return problems3;
            }
        } else {
            // no match!
            Logs.INVOCATION.tracef("Performed first-match discovery, no match");
        }
        return problems;
    }

    private List<Throwable> doAnyDiscovery(AbstractInvocationContext context, final FilterSpec filterSpec, final EJBLocator<?> locator) {
        Logs.INVOCATION.tracef("Performing any discovery(locator = %s, weak affinity = %s, filter spec = %s)", context.getLocator(), context.getWeakAffinity(), filterSpec);
        final List<Throwable> problems;
        // blacklist
        final Set<URI> blacklist = context.getAttachment(BL_KEY);
        final Map<URI, String> nodes = new HashMap<>();
        final Map<String, URI> uris = new HashMap<>();
        int nodeless = 0;
        try (final ServicesQueue queue = discover(filterSpec)) {
            ServiceURL serviceURL;
            while ((serviceURL = queue.takeService()) != null) {
                final URI location = serviceURL.getLocationURI();
                if (blacklist == null || ! blacklist.contains(location)) {
                    // Got a match!  See if there's a node affinity to set for the invocation.
                    final AttributeValue nodeValue = serviceURL.getFirstAttributeValue(FILTER_ATTR_NODE);
                    if (nodeValue != null) {
                        if (nodes.remove(location, null)) {
                            nodeless--;
                        }
                        final String nodeName = nodeValue.toString();
                        nodes.put(location, nodeName);
                        uris.put(nodeName, location);
                    } else {
                        // just set the URI but don't overwrite a separately-found node name
                        if (nodes.putIfAbsent(location, null) == null) {
                            nodeless++;
                        }
                    }
                    context.setDestination(location);
                }
            }
            problems = queue.getProblems();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Logs.MAIN.operationInterrupted();
        }

        if (nodes.isEmpty()) {
            // no match
            Logs.INVOCATION.tracef("Performed any discovery, no match");
            return problems;
        }
        URI location;
        String nodeName;
        if (nodes.size() == 1) {
            final Map.Entry<URI, String> entry = nodes.entrySet().iterator().next();
            location = entry.getKey();
            nodeName = entry.getValue();
            Logs.INVOCATION.tracef("Performed first-match discovery(target affinity(node) = %s, destination = %s)", nodeName, location);
        } else if (nodeless == 0) {
            // use the deployment node selector
            // todo: configure on client context
            DeploymentNodeSelector selector = DeploymentNodeSelector.RANDOM;
            nodeName = selector.selectNode(nodes.values().toArray(NO_STRINGS), locator.getAppName(), locator.getModuleName(), locator.getDistinctName());
            if (nodeName == null) {
                throw Logs.INVOCATION.selectorReturnedNull(selector);
            }
            location = uris.get(nodeName);
            if (location == null) {
                throw Logs.INVOCATION.selectorReturnedUnknownNode(selector, nodeName);
            }
            Logs.INVOCATION.tracef("Performed first-match discovery, nodes > 1, deployment selector used(target affinity(node) = %s, destination = %s)", nodeName, location);
        } else {
            // todo: configure on client context
            DiscoveredURISelector selector = DiscoveredURISelector.RANDOM;
            location = selector.selectNode(new ArrayList<>(nodes.keySet()), locator);
            if (location == null) {
                throw Logs.INVOCATION.selectorReturnedNull(selector);
            }
            nodeName = nodes.get(location);
            if (nodeName == null) {
                throw Logs.INVOCATION.selectorReturnedUnknownNode(selector, location.toString());
            }
            Logs.INVOCATION.tracef("Performed first-match discovery, nodes > 1, URI selector used(target affinity(node) = %s, destination = %s)", nodeName, location);
        }

        context.setDestination(location);
        if (nodeName != null) context.setTargetAffinity(new NodeAffinity(nodeName));
        return problems;
    }

    private List<Throwable> doClusterDiscovery(AbstractInvocationContext context, final FilterSpec filterSpec) {
        Logs.INVOCATION.tracef("Performing cluster discovery(locator = %s, weak affinity = %s, filter spec = %s)", context.getLocator(), context.getWeakAffinity(), filterSpec);
        Map<String, URI> nodes = new HashMap<>();
        final EJBClientContext clientContext = context.getClientContext();
        final List<Throwable> problems;
        final Set<URI> set = context.getAttachment(BL_KEY);
        try (final ServicesQueue queue = discover(filterSpec)) {
            ServiceURL serviceURL;
            while ((serviceURL = queue.takeService()) != null) {
                final URI location = serviceURL.getLocationURI();
                if (set == null || ! set.contains(location)) {
                    final EJBReceiver transportProvider = clientContext.getTransportProvider(location.getScheme());
                    if (transportProvider != null && satisfiesSourceAddress(serviceURL, transportProvider)) {
                        final AttributeValue nodeNameValue = serviceURL.getFirstAttributeValue(FILTER_ATTR_NODE);
                        // should always be true, but no harm in checking
                        if (nodeNameValue != null) {
                            nodes.put(nodeNameValue.toString(), location);
                        }
                    }
                }
            }
            problems = queue.getProblems();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Logs.MAIN.operationInterrupted();
        }
        final EJBLocator<?> locator = context.getLocator();

        if (nodes.isEmpty()) {

            Logs.INVOCATION.tracef("Performed cluster discovery, nodes is empty");

            return problems;
        } else if (nodes.size() == 1) {
            // just one choice, use it
            final Map.Entry<String, URI> entry = nodes.entrySet().iterator().next();
            final String nodeName = entry.getKey();
            final URI uri = entry.getValue();
            context.setTargetAffinity(new NodeAffinity(nodeName));
            context.setDestination(uri);

            Logs.INVOCATION.tracef("Performed cluster discovery (target affinity = %s, destination = %s)", context.getTargetAffinity(), context.getDestination());

            return problems;
        }
        // we have to run through the node selection process
        ArrayList<String> availableNodes = new ArrayList<>(nodes.size());
        ArrayList<String> connectedNodes = new ArrayList<>(nodes.size());
        for (Map.Entry<String, URI> entry : nodes.entrySet()) {
            final String nodeName = entry.getKey();
            final URI uri = entry.getValue();
            final EJBReceiver transportProvider = clientContext.getTransportProvider(uri.getScheme());
            if (transportProvider != null) {
                availableNodes.add(nodeName);
                if (transportProvider.isConnected(uri)) {
                    connectedNodes.add(nodeName);
                }
            }
        }
        Logs.INVOCATION.tracef("Performing cluster discovery (connected nodes = %s, available nodes = %s)", connectedNodes, availableNodes);

        final ClusterNodeSelector selector = clientContext.getClusterNodeSelector();
        final String selectedNode = selector.selectNode(((ClusterAffinity) locator.getAffinity()).getClusterName(), connectedNodes.toArray(NO_STRINGS), availableNodes.toArray(NO_STRINGS));
        if (selectedNode == null) {
            throw withSuppressed(Logs.MAIN.selectorReturnedNull(selector), problems);
        }
        final URI uri = nodes.get(selectedNode);
        if (uri == null) {
            throw withSuppressed(Logs.MAIN.selectorReturnedUnknownNode(selector, selectedNode), problems);
        }
        // got it!
        context.setDestination(uri);
        context.setTargetAffinity(new NodeAffinity(selectedNode));

        Logs.INVOCATION.tracef("Performed cluster discovery (target affinity = %s, destination = %s)", context.getTargetAffinity(), context.getDestination());

        return problems;
    }

    FilterSpec getFilterSpec(EJBModuleIdentifier identifier) {
        final String appName = identifier.getAppName();
        final String moduleName = identifier.getModuleName();
        final String distinctName = identifier.getDistinctName();
        if (distinctName != null && ! distinctName.isEmpty()) {
            if (appName.isEmpty()) {
                return FilterSpec.equal(FILTER_ATTR_EJB_MODULE_DISTINCT, moduleName + '/' + distinctName);
            } else {
                return FilterSpec.equal(FILTER_ATTR_EJB_MODULE_DISTINCT, appName + '/' + moduleName + '/' + distinctName);
            }
        } else {
            if (appName.isEmpty()) {
                return FilterSpec.equal(FILTER_ATTR_EJB_MODULE, moduleName);
            } else {
                return FilterSpec.equal(FILTER_ATTR_EJB_MODULE, appName + '/' + moduleName);
            }
        }
    }

    boolean satisfiesSourceAddress(ServiceURL serviceURL, EJBReceiver receiver) {
        final List<AttributeValue> values = serviceURL.getAttributeValues(FILTER_ATTR_SOURCE_IP);
        // treat as match
        if (values.isEmpty()) return true;
        final URI uri = serviceURL.getLocationURI();
        InetSocketAddress sourceAddress = receiver.getSourceAddress(new InetSocketAddress(uri.getHost(), uri.getPort()));
        InetAddress inetAddress;
        if (sourceAddress != null) {
            inetAddress = sourceAddress.getAddress();
        } else {
            inetAddress = null;
        }
        for (AttributeValue value : values) {
            if (! value.isString()) {
                continue;
            }
            final CidrAddress matchAddress = Inet.parseCidrAddress(value.toString());
            if (matchAddress == null) {
                // invalid address
                continue;
            }

            // now do the test
            if (inetAddress == null) {
                if (matchAddress.getNetmaskBits() == 0) {
                    return true;
                }
            } else if (matchAddress.matches(inetAddress)) {
                return true;
            }
        }
        return false;
    }
}
