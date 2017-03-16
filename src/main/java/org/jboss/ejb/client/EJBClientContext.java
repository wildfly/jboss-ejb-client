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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb._private.NetworkUtil;
import org.wildfly.common.Assert;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.Discovery;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceType;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.ServicesQueue;
import org.wildfly.naming.client.NamingProvider;

/**
 * The public API for an EJB client context.  An EJB client context may be associated with (and used by) one or more threads concurrently.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientContext extends Attachable implements Contextual<EJBClientContext> {

    /**
     * The service type to use for EJB discovery.
     */
    public static final ServiceType EJB_SERVICE_TYPE = ServiceType.of("ejb", "jboss");

    private static final ContextManager<EJBClientContext> CONTEXT_MANAGER = new ContextManager<EJBClientContext>(EJBClientContext.class, "jboss.ejb.client");

    private static final Supplier<Discovery> DISCOVERY_SUPPLIER = doPrivileged((PrivilegedAction<Supplier<Discovery>>) Discovery.getContextManager()::getPrivilegedSupplier);

    private static final Supplier<EJBClientContext> GETTER = doPrivileged((PrivilegedAction<Supplier<EJBClientContext>>) CONTEXT_MANAGER::getPrivilegedSupplier);

    private static final EJBTransportProvider[] NO_TRANSPORT_PROVIDERS = new EJBTransportProvider[0];
    private static final String[] NO_STRINGS = new String[0];

    /**
     * The discovery attribute name which contains the application and module name of the located EJB.
     */
    public static final String FILTER_ATTR_EJB_MODULE = "ejb-module";
    /**
     * The discovery attribute name which contains the application and module name with the distinct name of the located EJB.
     */
    public static final String FILTER_ATTR_EJB_MODULE_DISTINCT = "ejb-module-distinct";
    /**
     * The discovery attribute name which contains a node name.
     */
    public static final String FILTER_ATTR_NODE = "node";
    /**
     * The discovery attribute name which contains a cluster name.
     */
    public static final String FILTER_ATTR_CLUSTER = "cluster";
    /**
     * The discovery attribute name for when a rule only applies to a specific source IP address range.
     */
    public static final String FILTER_ATTR_SOURCE_IP = "source-ip";

    static {
        CONTEXT_MANAGER.setGlobalDefaultSupplier(new ConfigurationBasedEJBClientContextSelector());
    }

    private final EJBTransportProvider[] transportProviders;
    private final long invocationTimeout;
    private final EJBReceiverContext receiverContext;
    private final List<EJBClientConnection> configuredConnections;
    private final Map<String, EJBClientCluster> configuredClusters;
    private final ClusterNodeSelector clusterNodeSelector;
    private final DeploymentNodeSelector deploymentNodeSelector;
    private final ClassValue<EJBProxyInterceptorInformation<?>> proxyInfoValue = new ClassValue<EJBProxyInterceptorInformation<?>>() {
        protected EJBProxyInterceptorInformation<?> computeValue(final Class<?> type) {
            return EJBProxyInterceptorInformation.construct(type, EJBClientContext.this);
        }
    };

    static final InterceptorList defaultInterceptors = new InterceptorList(new EJBClientInterceptorInformation[] {
        EJBClientInterceptorInformation.forClass(TransactionInterceptor.class)
    });

    private final InterceptorList classPathInterceptors;
    private final InterceptorList globalInterceptors;
    private final Map<String, InterceptorList> configuredPerClassInterceptors;
    private final Map<String, Map<EJBMethodLocator, InterceptorList>> configuredPerMethodInterceptors;

    EJBClientContext(Builder builder) {
        final List<EJBTransportProvider> builderTransportProviders = builder.transportProviders;
        if (builderTransportProviders == null || builderTransportProviders.isEmpty()) {
            transportProviders = NO_TRANSPORT_PROVIDERS;
        } else {
            transportProviders = builderTransportProviders.toArray(new EJBTransportProvider[builderTransportProviders.size()]);
        }
        invocationTimeout = builder.invocationTimeout;
        receiverContext = new EJBReceiverContext(this);
        final List<EJBClientConnection> clientConnections = builder.clientConnections;
        if (clientConnections == null || clientConnections.isEmpty()) {
            configuredConnections = Collections.emptyList();
        } else if (clientConnections.size() == 1) {
            configuredConnections = Collections.singletonList(clientConnections.get(0));
        } else {
            configuredConnections = Collections.unmodifiableList(new ArrayList<>(clientConnections));
        }
        final List<EJBClientCluster> clientClusters = builder.clientClusters;
        if (clientClusters == null || clientClusters.isEmpty()) {
            configuredClusters = Collections.emptyMap();
        } else if (clientClusters.size() == 1) {
            final EJBClientCluster clientCluster = clientClusters.get(0);
            configuredClusters = Collections.singletonMap(clientCluster.getName(), clientCluster);
        } else {
            Map<String, EJBClientCluster> map = new HashMap<>();
            for (EJBClientCluster clientCluster : clientClusters) {
                map.put(clientCluster.getName(), clientCluster);
            }
            configuredClusters = Collections.unmodifiableMap(map);
        }
        clusterNodeSelector = builder.clusterNodeSelector;
        deploymentNodeSelector = builder.deploymentNodeSelector;

        // global interceptors
        final List<EJBClientInterceptorInformation> globalInterceptors = builder.globalInterceptors;
        if (globalInterceptors != null) {
            final Iterator<EJBClientInterceptorInformation> globalInterceptorsIterator = globalInterceptors.iterator();
            if (globalInterceptorsIterator.hasNext()) {
                final EJBClientInterceptorInformation first = globalInterceptorsIterator.next();
                if (globalInterceptorsIterator.hasNext()) {
                    // two or more
                    final ArrayList<EJBClientInterceptorInformation> arrayList = new ArrayList<>();
                    arrayList.add(first);
                    do {
                        arrayList.add(globalInterceptorsIterator.next());
                    } while (globalInterceptorsIterator.hasNext());
                    if (arrayList.isEmpty()) {
                        this.globalInterceptors = InterceptorList.EMPTY;
                    } else {
                        this.globalInterceptors = new InterceptorList(arrayList.toArray(EJBClientInterceptorInformation.NO_INTERCEPTORS));
                    }
                } else {
                    // just one
                    this.globalInterceptors = first.getSingletonList();
                }
            } else {
                this.globalInterceptors = InterceptorList.EMPTY;
            }
        } else {
            this.globalInterceptors = InterceptorList.EMPTY;
        }

        // class path interceptors
        this.classPathInterceptors = doPrivileged((PrivilegedAction<InterceptorList>) EJBClientContext::getClassPathInterceptorList);

        // configured per-class interceptors
        final List<ClassInterceptor> classInterceptors = builder.classInterceptors;
        if (classInterceptors != null) {
            final Iterator<ClassInterceptor> classInterceptorsIterator = classInterceptors.iterator();
            if (classInterceptorsIterator.hasNext()) {
                final HashMap<String, ArrayList<EJBClientInterceptorInformation>> map = new HashMap<>();
                do {
                    final ClassInterceptor classInterceptor = classInterceptorsIterator.next();
                    map.computeIfAbsent(classInterceptor.getClassName(), ignored -> new ArrayList<>()).add(classInterceptor.getInterceptor());
                } while (classInterceptorsIterator.hasNext());
                final Iterator<Map.Entry<String, ArrayList<EJBClientInterceptorInformation>>> mapIterator = map.entrySet().iterator();
                if (mapIterator.hasNext()) {
                    final Map.Entry<String, ArrayList<EJBClientInterceptorInformation>> first = mapIterator.next();
                    if (mapIterator.hasNext()) {
                        Map<String, InterceptorList> targetMap = new HashMap<>(map.size());
                        targetMap.put(first.getKey(), InterceptorList.ofList(first.getValue()));
                        do {
                            final Map.Entry<String, ArrayList<EJBClientInterceptorInformation>> next = mapIterator.next();
                            targetMap.put(next.getKey(), InterceptorList.ofList(next.getValue()));
                        } while (mapIterator.hasNext());
                        this.configuredPerClassInterceptors = targetMap;
                    } else {
                        this.configuredPerClassInterceptors = Collections.singletonMap(first.getKey(), InterceptorList.ofList(first.getValue()));
                    }
                } else {
                    this.configuredPerClassInterceptors = Collections.emptyMap();
                }
            } else {
                this.configuredPerClassInterceptors = Collections.emptyMap();
            }
        } else {
            this.configuredPerClassInterceptors = Collections.emptyMap();
        }

        // configured per-method interceptors
        final List<MethodInterceptor> methodInterceptors = builder.methodInterceptors;
        if (methodInterceptors != null) {
            final Iterator<MethodInterceptor> methodInterceptorIterator = methodInterceptors.iterator();
            if (methodInterceptorIterator.hasNext()) {
                final HashMap<String, HashMap<EJBMethodLocator, ArrayList<EJBClientInterceptorInformation>>> map = new HashMap<>();
                do {
                    final MethodInterceptor methodInterceptor = methodInterceptorIterator.next();
                    map.computeIfAbsent(methodInterceptor.getClassName(), ignored -> new HashMap<>()).computeIfAbsent(methodInterceptor.getMethodLocator(), ignored -> new ArrayList<>()).add(methodInterceptor.getInterceptor());
                } while (methodInterceptorIterator.hasNext());
                final Iterator<Map.Entry<String, HashMap<EJBMethodLocator, ArrayList<EJBClientInterceptorInformation>>>> outerIter = map.entrySet().iterator();
                if (outerIter.hasNext()) {
                    final Map.Entry<String, HashMap<EJBMethodLocator, ArrayList<EJBClientInterceptorInformation>>> first = outerIter.next();
                    if (outerIter.hasNext()) {
                        Map<String, Map<EJBMethodLocator, InterceptorList>> targetMap = new HashMap<>(map.size());
                        targetMap.put(first.getKey(), calculateMethodInterceptors(first.getValue()));
                        do {
                            final Map.Entry<String, HashMap<EJBMethodLocator, ArrayList<EJBClientInterceptorInformation>>> next = outerIter.next();
                            targetMap.put(next.getKey(), calculateMethodInterceptors(next.getValue()));
                        } while (outerIter.hasNext());
                        this.configuredPerMethodInterceptors = targetMap;
                    } else {
                        this.configuredPerMethodInterceptors = Collections.singletonMap(first.getKey(), calculateMethodInterceptors(first.getValue()));
                    }
                } else {
                    this.configuredPerMethodInterceptors = Collections.emptyMap();
                }
            } else {
                this.configuredPerMethodInterceptors = Collections.emptyMap();
            }
        } else {
            this.configuredPerMethodInterceptors = Collections.emptyMap();
        }

        // this must be last
        for (EJBTransportProvider transportProvider : transportProviders) {
            transportProvider.notifyRegistered(receiverContext);
        }
    }

    private static Map<EJBMethodLocator, InterceptorList> calculateMethodInterceptors(final HashMap<EJBMethodLocator, ArrayList<EJBClientInterceptorInformation>> map) {
        final Iterator<Map.Entry<EJBMethodLocator, ArrayList<EJBClientInterceptorInformation>>> iterator = map.entrySet().iterator();
        if (iterator.hasNext()) {
            final Map.Entry<EJBMethodLocator, ArrayList<EJBClientInterceptorInformation>> first = iterator.next();
            if (iterator.hasNext()) {
                HashMap<EJBMethodLocator, InterceptorList> targetMap = new HashMap<>(map.size());
                targetMap.put(first.getKey(), InterceptorList.ofList(first.getValue()));
                do {
                    final Map.Entry<EJBMethodLocator, ArrayList<EJBClientInterceptorInformation>> next = iterator.next();
                    targetMap.put(next.getKey(), InterceptorList.ofList(next.getValue()));
                } while (iterator.hasNext());
                return targetMap;
            } else {
                return Collections.singletonMap(first.getKey(), InterceptorList.ofList(first.getValue()));
            }
        } else {
            return Collections.emptyMap();
        }
    }

    static InterceptorList getClassPathInterceptorList() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            final Enumeration<URL> resources = classLoader.getResources("META-INF/services/org.jboss.ejb.client.EJBClientInterceptor");
            if (resources.hasMoreElements()) {
                final ArrayList<EJBClientInterceptorInformation> list = new ArrayList<>();
                do {
                    final URL url = resources.nextElement();
                    try (InputStream st = url.openStream()) {
                        try (InputStreamReader isr = new InputStreamReader(st, StandardCharsets.UTF_8)) {
                            try (BufferedReader r = new BufferedReader(isr)) {
                                String line;
                                while ((line = r.readLine()) != null) {
                                    line = line.trim();
                                    if (line.isEmpty() || line.charAt(0) == '#') {
                                        continue;
                                    }
                                    try {
                                        final Class<?> interceptorClass = Class.forName(line, true, classLoader).asSubclass(EJBClientInterceptor.class);
                                        list.add(EJBClientInterceptorInformation.forClass(interceptorClass));
                                    } catch (ClassNotFoundException e) {
                                        // skip
                                    }
                                }
                            }
                        }
                    }
                } while (resources.hasMoreElements());
                if (list.isEmpty()) {
                    return InterceptorList.EMPTY;
                } else {
                    return new InterceptorList(list.toArray(EJBClientInterceptorInformation.NO_INTERCEPTORS));
                }
            } else {
                return InterceptorList.EMPTY;
            }
        } catch (IOException e) {
            return InterceptorList.EMPTY;
        }
    }

    /**
     * Get the context manager.  Simply calls the {@code static} method {@link #getContextManager()}.
     *
     * @return the context manager (not {@code null})
     */
    public ContextManager<EJBClientContext> getInstanceContextManager() {
        return getContextManager();
    }

    /**
     * Get the context manager.
     *
     * @return the context manager (not {@code null})
     */
    public static ContextManager<EJBClientContext> getContextManager() {
        return CONTEXT_MANAGER;
    }

    /**
     * Get the configured invocation timeout.  A value of zero indicates that invocations never time out.
     *
     * @return the configured invocation timeout
     */
    public long getInvocationTimeout() {
        return invocationTimeout;
    }

    /**
     * Get the pre-configured connections for this context.  This information may not be used by some transport providers
     * and mainly exists for legacy compatibility purposes.
     *
     * @return the pre-configured connections for this context (not {@code null})
     */
    public List<EJBClientConnection> getConfiguredConnections() {
        return configuredConnections;
    }

    /**
     * Get the initially configured clusters for this context.  The collection will not reflect any topology updates
     * received from peers.
     *
     * @return the initially configured clusters for this context
     */
    public Collection<EJBClientCluster> getInitialConfiguredClusters() {
        return configuredClusters.values();
    }

    /**
     * Get a copy of this context with the given interceptor(s) added.  If the array is {@code null} or empty, the
     * current context is returned as-is.
     *
     * @param interceptors the interceptor(s) to add
     * @return the new context (not {@code null})
     */
    public EJBClientContext withAddedInterceptors(EJBClientInterceptor... interceptors) {
        if (interceptors == null) {
            return this;
        }
        final int length = interceptors.length;
        if (length == 0) {
            return this;
        }
        final Builder builder = new Builder(this);
        boolean construct = false;
        for (EJBClientInterceptor interceptor : interceptors) {
            if (interceptor != null) {
                builder.addInterceptor(interceptor);
                construct = true;
            }
        }
        return construct ? builder.build() : this;
    }

    /**
     * Get a copy of this context with the given transport provider(s) added.  If the array is {@code null} or empty, the
     * current context is returned as-is.
     *
     * @param transportProviders the transport providers(s) to add
     * @return the new context (not {@code null})
     */
    public EJBClientContext withAddedTransportProviders(EJBTransportProvider... transportProviders) {
        if (transportProviders == null) {
            return this;
        }
        final int length = transportProviders.length;
        if (length == 0) {
            return this;
        }
        final Builder builder = new Builder(this);
        boolean construct = false;
        for (EJBTransportProvider transportProvider : transportProviders) {
            if (transportProvider != null) {
                builder.addTransportProvider(transportProvider);
                construct = true;
            }
        }
        return construct ? builder.build() : this;
    }

    EJBReceiver getTransportProvider(final String scheme) {
        for (EJBTransportProvider transportProvider : transportProviders) {
            if (transportProvider.supportsProtocol(scheme)) {
                return transportProvider.getReceiver(receiverContext, scheme);
            }
        }
        return null;
    }

    ServicesQueue discover(final FilterSpec filterSpec) {
        return getDiscovery().discover(EJB_SERVICE_TYPE, filterSpec);
    }

    Discovery getDiscovery() {
        return DISCOVERY_SUPPLIER.get();
    }

    InterceptorList getInterceptors(final Class<?> invokedProxy, final Method method) {
        return proxyInfoValue.get(invokedProxy).getInterceptors(method);
    }

    static final class ClassInterceptor {
        private final String className;
        private final EJBClientInterceptorInformation interceptor;

        ClassInterceptor(final String className, final EJBClientInterceptorInformation interceptor) {
            this.className = className;
            this.interceptor = interceptor;
        }

        String getClassName() {
            return className;
        }

        EJBClientInterceptorInformation getInterceptor() {
            return interceptor;
        }
    }

    static final class MethodInterceptor {
        private final String className;
        private final EJBMethodLocator methodLocator;
        private final EJBClientInterceptorInformation interceptor;

        MethodInterceptor(final String className, final EJBMethodLocator methodLocator, final EJBClientInterceptorInformation interceptor) {
            this.className = className;
            this.methodLocator = methodLocator;
            this.interceptor = interceptor;
        }

        String getClassName() {
            return className;
        }

        EJBMethodLocator getMethodLocator() {
            return methodLocator;
        }

        EJBClientInterceptorInformation getInterceptor() {
            return interceptor;
        }
    }

    /**
     * A builder for EJB client contexts.
     */
    public static final class Builder {

        List<EJBClientInterceptorInformation> globalInterceptors;
        List<ClassInterceptor> classInterceptors;
        List<MethodInterceptor> methodInterceptors;
        List<EJBTransportProvider> transportProviders;
        List<EJBClientConnection> clientConnections;
        List<EJBClientCluster> clientClusters;
        ClusterNodeSelector clusterNodeSelector = ClusterNodeSelector.DEFAULT;
        DeploymentNodeSelector deploymentNodeSelector = DeploymentNodeSelector.RANDOM;
        long invocationTimeout;

        /**
         * Construct a new instance.
         */
        public Builder() {
        }

        Builder(final EJBClientContext clientContext) {
            globalInterceptors = Arrays.stream(clientContext.globalInterceptors.getInformation()).collect(Collectors.toCollection(ArrayList::new));
            classInterceptors = new ArrayList<>();
            for (Map.Entry<String, InterceptorList> entry : clientContext.getConfiguredPerClassInterceptors().entrySet()) {
                final String className = entry.getKey();
                for (EJBClientInterceptorInformation information : entry.getValue().getInformation()) {
                    classInterceptors.add(new ClassInterceptor(className, information));
                }
            }
            methodInterceptors = new ArrayList<>();
            for (Map.Entry<String, Map<EJBMethodLocator, InterceptorList>> entry : clientContext.getConfiguredPerMethodInterceptors().entrySet()) {
                final String className = entry.getKey();
                for (Map.Entry<EJBMethodLocator, InterceptorList> entry1 : entry.getValue().entrySet()) {
                    final EJBMethodLocator methodLocator = entry1.getKey();
                    for (EJBClientInterceptorInformation information : entry1.getValue().getInformation()) {
                        methodInterceptors.add(new MethodInterceptor(className, methodLocator, information));
                    }
                }
            }
            transportProviders = new ArrayList<>();
            Collections.addAll(transportProviders, clientContext.transportProviders);
            clientConnections = new ArrayList<>();
            clientConnections.addAll(clientContext.getConfiguredConnections());
            clientClusters = new ArrayList<>();
            clientClusters.addAll(clientContext.configuredClusters.values());
            clusterNodeSelector = clientContext.clusterNodeSelector;
            deploymentNodeSelector = clientContext.deploymentNodeSelector;
            invocationTimeout = clientContext.invocationTimeout;
        }

        public Builder addInterceptor(EJBClientInterceptor interceptor) {
            Assert.checkNotNullParam("interceptor", interceptor);
            if (globalInterceptors == null) {
                globalInterceptors = new ArrayList<>();
            }
            globalInterceptors.add(EJBClientInterceptorInformation.forInstance(interceptor));
            return this;
        }

        public Builder addInterceptor(Class<? extends EJBClientInterceptor> interceptorClass) {
            Assert.checkNotNullParam("interceptorClass", interceptorClass);
            if (globalInterceptors == null) {
                globalInterceptors = new ArrayList<>();
            }
            globalInterceptors.add(EJBClientInterceptorInformation.forClass(interceptorClass));
            return this;
        }

        public Builder addClassInterceptor(String className, EJBClientInterceptor interceptor) {
            Assert.checkNotNullParam("className", className);
            Assert.checkNotNullParam("interceptor", interceptor);
            if (classInterceptors == null) {
                classInterceptors = new ArrayList<>();
            }
            classInterceptors.add(new ClassInterceptor(className, EJBClientInterceptorInformation.forInstance(interceptor)));
            return this;
        }

        public Builder addClassInterceptor(String className, Class<? extends EJBClientInterceptor> interceptorClass) {
            Assert.checkNotNullParam("className", className);
            Assert.checkNotNullParam("interceptorClass", interceptorClass);
            if (classInterceptors == null) {
                classInterceptors = new ArrayList<>();
            }
            classInterceptors.add(new ClassInterceptor(className, EJBClientInterceptorInformation.forClass(interceptorClass)));
            return this;
        }

        public Builder addMethodInterceptor(String className, EJBMethodLocator methodLocator, EJBClientInterceptor interceptor) {
            Assert.checkNotNullParam("className", className);
            Assert.checkNotNullParam("methodLocator", methodLocator);
            Assert.checkNotNullParam("interceptor", interceptor);
            if (methodInterceptors == null) {
                methodInterceptors = new ArrayList<>();
            }
            methodInterceptors.add(new MethodInterceptor(className, methodLocator, EJBClientInterceptorInformation.forInstance(interceptor)));
            return this;
        }

        public Builder addMethodInterceptor(String className, EJBMethodLocator methodLocator, Class<? extends EJBClientInterceptor> interceptorClass) {
            Assert.checkNotNullParam("className", className);
            Assert.checkNotNullParam("methodLocator", methodLocator);
            Assert.checkNotNullParam("interceptorClass", interceptorClass);
            if (methodInterceptors == null) {
                methodInterceptors = new ArrayList<>();
            }
            methodInterceptors.add(new MethodInterceptor(className, methodLocator, EJBClientInterceptorInformation.forClass(interceptorClass)));
            return this;
        }

        public Builder addTransportProvider(EJBTransportProvider provider) {
            Assert.checkNotNullParam("provider", provider);
            if (transportProviders == null) {
                transportProviders = new ArrayList<>();
            }
            transportProviders.add(provider);
            return this;
        }

        public Builder addClientConnection(EJBClientConnection connection) {
            Assert.checkNotNullParam("connection", connection);
            if (clientConnections == null) {
                clientConnections = new ArrayList<>();
            }
            clientConnections.add(connection);
            return this;
        }

        public Builder addClientCluster(EJBClientCluster cluster) {
            Assert.checkNotNullParam("cluster", cluster);
            if (clientClusters == null) {
                clientClusters = new ArrayList<>();
            }
            clientClusters.add(cluster);
            return this;
        }

        public Builder setClusterNodeSelector(final ClusterNodeSelector clusterNodeSelector) {
            Assert.checkNotNullParam("clusterNodeSelector", clusterNodeSelector);
            this.clusterNodeSelector = clusterNodeSelector;
            return this;
        }

        public Builder setDeploymentNodeSelector(final DeploymentNodeSelector deploymentNodeSelector) {
            Assert.checkNotNullParam("deploymentNodeSelector", deploymentNodeSelector);
            this.deploymentNodeSelector = deploymentNodeSelector;
            return this;
        }

        public Builder setInvocationTimeout(final long invocationTimeout) {
            Assert.checkMinimumParameter("invocationTimeout", 0L, invocationTimeout);
            this.invocationTimeout = invocationTimeout;
            return this;
        }

        public EJBClientContext build() {
            return new EJBClientContext(this);
        }
    }

    /**
     * Get the current client context for this thread.
     *
     * @return the current client context
     */
    public static EJBClientContext getCurrent() {
        final EJBClientContext clientContext = GETTER.get();
        if (clientContext == null) {
            throw Logs.MAIN.noEJBClientContextAvailable();
        }
        return clientContext;
    }

    /**
     * Get the current client context for this thread.  Delegates to {@link #getCurrent()}.
     *
     * @return the current client context for this thread
     */
    public static EJBClientContext requireCurrent() {
        return getCurrent();
    }

    <T> StatefulEJBLocator<T> createSession(final StatelessEJBLocator<T> statelessLocator, NamingProvider namingProvider) throws Exception {
        final LocatedAction<StatefulEJBLocator<T>, StatelessEJBLocator<T>, T> action =
                (receiver, originalLocator, newAffinity) -> receiver.createSession(originalLocator.withNewAffinity(newAffinity), namingProvider);
        return performLocatedAction(statelessLocator, action);
    }
    interface LocatedAction<R, L extends EJBLocator<T>, T> {
        R execute(EJBReceiver receiver, L originalLocator, Affinity newAffinity) throws Exception;
    }

    <R, L extends EJBLocator<T>, T> R performLocatedAction(final L locator, final LocatedAction<R, L, T> locatedAction) throws Exception {
        final Affinity affinity = locator.getAffinity();
        final String scheme;
        if (affinity instanceof NodeAffinity) {
            return discoverAffinityNode(locator, (NodeAffinity) affinity, locatedAction);
        } else if (affinity instanceof ClusterAffinity) {
            return discoverAffinityCluster(locator, (ClusterAffinity) affinity, locatedAction);
        } else if (affinity == Affinity.LOCAL) {
            scheme = "local";
        } else if (affinity instanceof URIAffinity) {
            scheme = affinity.getUri().getScheme();
        } else {
            assert affinity == Affinity.NONE;
            return discoverAffinityNone(locator, locatedAction);
        }
        final EJBReceiver transportProvider = getTransportProvider(scheme);
        if (transportProvider == null) {
            throw Logs.MAIN.noTransportProvider(locator, scheme);
        } else {
            return locatedAction.execute(transportProvider, locator, locator.getAffinity());
        }
    }

    <R, L extends EJBLocator<T>, T> R discoverAffinityNone(L locator, final LocatedAction<R, L, T> locatedAction) throws Exception {
        assert locator.getAffinity() == Affinity.NONE;

        try (final ServicesQueue servicesQueue = discover(getFilterSpec(locator.getIdentifier()))) {
            ServiceURL serviceURL;
            do {
                serviceURL = servicesQueue.takeService();
                if (serviceURL == null) {
                    throw withSuppressed(Logs.MAIN.noEJBReceiverAvailable(locator), servicesQueue.getProblems());
                }
            } while (! supports(serviceURL));
            ServiceURL nextServiceURL;
            do {
                nextServiceURL = servicesQueue.takeService();
            } while (nextServiceURL != null && ! supports(nextServiceURL));
            if (nextServiceURL == null) {
                // we only got one; optimal case
                final Affinity affinity = Affinity.forUri(serviceURL.getLocationURI());
                // recurse for one node if needed
                if (affinity instanceof NodeAffinity) {
                    return discoverAffinityNode(locator, (NodeAffinity) affinity, locatedAction);
                } else if (affinity instanceof ClusterAffinity) {
                    return discoverAffinityCluster(locator, (ClusterAffinity) affinity, locatedAction);
                } else if (affinity == Affinity.LOCAL) {
                    assert supports(serviceURL);
                    return locatedAction.execute(getTransportProvider("local"), locator, affinity);
                } else {
                    final EJBReceiver provider = getTransportProvider(affinity.getUri().getScheme());
                    if (provider == null) {
                        throw withSuppressed(Logs.MAIN.noEJBReceiverAvailable(locator), servicesQueue.getProblems());
                    }
                    return locatedAction.execute(provider, locator, affinity);
                }
            }
            // OK it's a multi-result situation, we could get any combination at this point
            List<URI> uris = new ArrayList<>();
            List<String> nodes = new ArrayList<>();
            List<String> clusters = new ArrayList<>();
            boolean[] local = new boolean[1];
            classify(serviceURL, uris, nodes, clusters, local);
            classify(nextServiceURL, uris, nodes, clusters, local);
            if (local[0]) {
                // shortcut for local invocation
                return locatedAction.execute(getTransportProvider("local"), locator, Affinity.LOCAL);
            }
            serviceURL = servicesQueue.takeService();
            while (serviceURL != null) {
                if (supports(serviceURL)) {
                    classify(serviceURL, uris, nodes, clusters, local);
                }
                if (local[0]) {
                    // shortcut for local invocation
                    return locatedAction.execute(getTransportProvider("local"), locator, Affinity.LOCAL);
                }
                serviceURL = servicesQueue.takeService();
            }
            // all classified now
            if (! uris.isEmpty()) {
                // always prefer a URI
                final URI uri;
                if (uris.size() == 1) {
                    // just use the first one
                    uri = uris.get(0);
                } else {
                    // TODO pull from config
                    DiscoveredURISelector selector = DiscoveredURISelector.RANDOM;
                    uri = selector.selectNode(uris, locator);
                    if (uri == null) {
                        throw Logs.MAIN.selectorReturnedNull(selector);
                    }
                }
                return locatedAction.execute(getTransportProvider(uri.getScheme()), locator, Affinity.forUri(uri));
            }
            if (! nodes.isEmpty()) {
                // nodes are the next best thing
                final String node;
                if (nodes.size() == 1) {
                    node = nodes.get(0);
                } else {
                    node = deploymentNodeSelector.selectNode(nodes.toArray(NO_STRINGS), locator.getAppName(), locator.getModuleName(), locator.getDistinctName());
                    if (node == null) {
                        throw Logs.MAIN.selectorReturnedNull(deploymentNodeSelector);
                    }
                }
                return discoverAffinityNode(locator, new NodeAffinity(node), locatedAction);
            }
            assert ! clusters.isEmpty();
            // last of all, find the first cluster and use it
            return discoverAffinityCluster(locator, new ClusterAffinity(clusters.get(0)), locatedAction);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Logs.MAIN.operationInterrupted();
        }
    }

    <R, L extends EJBLocator<T>, T> R discoverAffinityNode(final L locator, final NodeAffinity nodeAffinity, final LocatedAction<R, L, T> locatedAction) throws Exception {
        // we just need to find a single location for this node; therefore, we'll exit at the first opportunity.
        try (final ServicesQueue servicesQueue = discover(getFilterSpec(nodeAffinity))) {
            // we don't recurse into node or cluster for this case; furthermore we always use the first answer.
            ServiceURL serviceURL;
            for (;;) {
                // interruption is caught in the calling method
                serviceURL = servicesQueue.takeService();
                if (serviceURL == null) {
                    throw withSuppressed(Logs.MAIN.noEJBReceiverAvailable(locator), servicesQueue.getProblems());
                }
                final EJBReceiver receiver = getTransportProvider(serviceURL.getUriScheme());
                if (receiver != null) {
                    return locatedAction.execute(receiver, locator, Affinity.forUri(serviceURL.getLocationURI()));
                }
            }
        }
    }

    private <R, L extends EJBLocator<T>, T> R discoverAffinityCluster(final L locator, final ClusterAffinity clusterAffinity, final LocatedAction<R, L, T> locatedAction) throws Exception {
        final String clusterName = clusterAffinity.getClusterName();
        final EJBClientCluster cluster = configuredClusters.get(clusterName);
        final ClusterNodeSelector selector;
        if (cluster != null) {
            // we override a few things then
            final ClusterNodeSelector configSelector = cluster.getClusterNodeSelector();
            selector = configSelector == null ? clusterNodeSelector : configSelector;
        } else {
            selector = clusterNodeSelector;
        }
        // we expect multiple results
        Set<URI> unresolvedUris = new HashSet<>();
        Set<String> nodes = new HashSet<>();
        List<Throwable> problems;
        try (final ServicesQueue servicesQueue = discover(getFilterSpec(clusterAffinity))) {
            // interruption is caught in the calling method
            ServiceURL serviceURL = servicesQueue.takeService();
            while (serviceURL != null) {
                final String scheme = serviceURL.getUriScheme();
                switch (scheme) {
                    case "node": {
                        nodes.add(serviceURL.getLocationURI().getSchemeSpecificPart());
                        break;
                    }
                    case "local": {
                        // todo: special case
                        break;
                    }
                    case "cluster": {
                        // ignore cluster-in-cluster
                        break;
                    }
                    default: {
                        if (getTransportProvider(scheme) != null) {
                            unresolvedUris.add(serviceURL.getLocationURI());
                        }
                        break;
                    }
                }
                // interruption is caught in the calling method
                serviceURL = servicesQueue.takeService();
            }
            problems = servicesQueue.getProblems();
        }
        if (nodes.isEmpty()) {
            if (unresolvedUris.isEmpty()) {
                throw withSuppressed(Logs.MAIN.noEJBReceiverAvailable(locator), problems);
            } else {
                // TODO: we should have a selector here too
                final URI uri = unresolvedUris.iterator().next();
                return locatedAction.execute(getTransportProvider(uri.getScheme()), locator, URIAffinity.forUri(uri));
            }
        } else if (nodes.size() == 1) {
            return discoverAffinityNode(locator, new NodeAffinity(nodes.iterator().next()), locatedAction);
        } else {
            // find the subset of connected nodes
            final Map<String, URI> all = new HashMap<>();
            final Map<String, URI> connected = new HashMap<>();
            searchingNodes: for (String nodeName : nodes) {
                try (final ServicesQueue servicesQueue = discover(getNodeFilterSpec(nodeName))) {
                    // interruption is caught in the calling method
                    ServiceURL serviceURL = servicesQueue.takeService();
                    while (serviceURL != null) {
                        final String scheme = serviceURL.getUriScheme();
                        switch (scheme) {
                            case "node": {
                                // ignore node-in-node
                                break;
                            }
                            case "cluster": {
                                // ignore cluster-in-node
                                break;
                            }
                            default: {
                                // this includes "local"
                                final EJBReceiver transportProvider = getTransportProvider(scheme);
                                if (transportProvider != null) {
                                    // found a match, now see if it is "connected"
                                    final URI locationURI = serviceURL.getLocationURI();
                                    if (satisfiesSourceAddress(serviceURL, transportProvider)) {
                                        if (transportProvider.isConnected(locationURI)) {
                                            connected.put(nodeName, locationURI);
                                        }
                                        all.put(nodeName, locationURI);
                                    }
                                    continue searchingNodes;
                                }
                                break;
                            }
                        }
                        // interruption is caught in the calling method
                        serviceURL = servicesQueue.takeService();
                    }
                }
            }

            final String[] connectedArray = connected.keySet().toArray(NO_STRINGS);
            final String[] nodeArray = all.keySet().toArray(NO_STRINGS);
            final String node = selector.selectNode(clusterName, connectedArray, nodeArray);
            if (node == null) {
                throw Logs.MAIN.selectorReturnedNull(selector);
            }
            URI uri = all.get(node);
            if (uri == null) {
                throw withSuppressed(Logs.MAIN.noEJBReceiverAvailable(locator), problems);
            }
            return locatedAction.execute(getTransportProvider(uri.getScheme()), locator, Affinity.forUri(uri));
        }
    }

    private boolean supports(final ServiceURL serviceURL) {
        final String scheme = serviceURL.getLocationURI().getScheme();
        return "node".equals(scheme) || "cluster".equals(scheme) || getTransportProvider(scheme) != null;
    }

    private void classify(final ServiceURL serviceURL, final List<URI> uris, final List<String> nodes, final List<String> clusters, final boolean[] local) {
        final URI locationURI = serviceURL.getLocationURI();
        final String scheme = locationURI.getScheme();
        if ("node".equals(scheme)) {
            nodes.add(locationURI.getSchemeSpecificPart());
        } else if ("cluster".equals(scheme)) {
            clusters.add(locationURI.getSchemeSpecificPart());
        } else if ("local".equals(scheme)) {
            local[0] = true;
        } else {
            uris.add(locationURI);
        }
    }


    FilterSpec getFilterSpec(EJBIdentifier identifier) {
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

    FilterSpec getFilterSpec(NodeAffinity nodeAffinity) {
        return getNodeFilterSpec(nodeAffinity.getNodeName());
    }

    FilterSpec getNodeFilterSpec(String nodeName) {
        return FilterSpec.equal(FILTER_ATTR_NODE, nodeName);
    }

    FilterSpec getFilterSpec(ClusterAffinity clusterAffinity) {
        return FilterSpec.equal(FILTER_ATTR_CLUSTER, clusterAffinity.getClusterName());
    }

    boolean satisfiesSourceAddress(ServiceURL serviceURL, EJBReceiver receiver) {
        final List<AttributeValue> values = serviceURL.getAttributeValues(FILTER_ATTR_SOURCE_IP);
        // treat as match
        if (values.isEmpty()) return true;
        SocketAddress sourceAddress = receiver.getSourceAddress(serviceURL.getLocationURI());
        InetAddress inetAddress;
        if (sourceAddress instanceof InetSocketAddress) {
            inetAddress = ((InetSocketAddress) sourceAddress).getAddress();
        } else {
            inetAddress = null;
        }
        for (AttributeValue value : values) {
            if (! value.isString()) {
                continue;
            }
            final String string = value.toString();
            int slash = string.indexOf('/');
            if (slash == -1) {
                continue;
            }
            final InetAddress matchAddress;
            try {
                matchAddress = InetAddress.getByName(string.substring(0, slash));
            } catch (UnknownHostException e) {
                // the attribute isn't actually valid
                continue;
            }
            final int mask;
            try {
                mask = Integer.parseInt(string.substring(slash + 1));
            } catch (NumberFormatException e) {
                // the attribute isn't actually valid
                continue;
            }

            // now do the test
            if (inetAddress == null) {
                if (mask == 0) {
                    // it's zero, so we can just break out now because we have the only match we're gonna get
                    break;
                }
                // else fall out because we have no source address to test
            } else if (NetworkUtil.belongsToNetwork(inetAddress, matchAddress, mask)) {
                // matched!
                return true;
            }
        }
        return false;
    }

    InterceptorList getClassPathInterceptors() {
        return classPathInterceptors;
    }

    InterceptorList getGlobalInterceptors() {
        return globalInterceptors;
    }

    Map<String, InterceptorList> getConfiguredPerClassInterceptors() {
        return configuredPerClassInterceptors;
    }

    Map<String, Map<EJBMethodLocator, InterceptorList>> getConfiguredPerMethodInterceptors() {
        return configuredPerMethodInterceptors;
    }

    static <T extends Throwable> T withSuppressed(T original, Collection<Throwable> suppressed) {
        if (suppressed != null) {
            for (Throwable throwable : suppressed) {
                original.addSuppressed(throwable);
            }
        }
        return original;
    }

    static final class InterceptorList {
        static final InterceptorList EMPTY = new InterceptorList(new EJBClientInterceptorInformation[0]);

        private final EJBClientInterceptorInformation[] information;
        private final int hashCode;

        InterceptorList(final EJBClientInterceptorInformation[] information) {
            Arrays.sort(information);
            this.information = information;
            hashCode = Arrays.hashCode(information);
        }

        EJBClientInterceptorInformation[] getInformation() {
            return information;
        }

        public boolean equals(final Object obj) {
            return obj instanceof InterceptorList && Arrays.equals(information, ((InterceptorList) obj).information);
        }

        public int hashCode() {
            return hashCode;
        }

        private static InterceptorList ofList(final ArrayList<EJBClientInterceptorInformation> value) {
            if (value.isEmpty()) {
                return EMPTY;
            } else if (value.size() == 1) {
                return value.get(0).getSingletonList();
            } else {
                return new InterceptorList(value.toArray(EJBClientInterceptorInformation.NO_INTERCEPTORS));
            }
        }

        InterceptorList combine(final InterceptorList other) {
            return information.length == 0 ? other : other.information.length == 0 ? this : new InterceptorList(concat(information, other.information));
        }

        private static EJBClientInterceptorInformation[] concat(final EJBClientInterceptorInformation[] a, final EJBClientInterceptorInformation[] b) {
            final EJBClientInterceptorInformation[] c = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, c, a.length, b.length);
            return c;
        }
    }
}
