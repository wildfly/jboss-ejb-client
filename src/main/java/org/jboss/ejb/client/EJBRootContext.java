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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.TimeUnit;

import javax.naming.Binding;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NamingException;

import org.jboss.ejb._private.Logs;
import org.wildfly.naming.client.AbstractContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.SimpleName;
import org.wildfly.naming.client.store.RelativeContext;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationContext;

class EJBRootContext extends AbstractContext {

    static final AttachmentKey<NamingProvider> NAMING_PROVIDER_ATTACHMENT_KEY = new AttachmentKey<>();

    private static final String PROPERTY_KEY_INVOCATION_TIMEOUT = "invocation.timeout";
    private final Affinity baseAffinity;
    private final NamingProvider namingProvider;

    EJBRootContext(final NamingProvider namingProvider, final FastHashtable<String, Object> env) {
        super(env);
        this.namingProvider = namingProvider;

        // check if strong affinity for this context has been set in the environment
        String clusterName = getClusterAffinityValueFromEnvironment();
        if (clusterName != null) {
            baseAffinity = new ClusterAffinity(clusterName);
        } else {
            baseAffinity = Affinity.NONE;
        }
    }

    protected Object lookupNative(final Name name) throws NamingException {
        final int size = name.size();
        if (size < 3) {
            return new RelativeContext(new FastHashtable<>(getEnvironment()), this, SimpleName.of(name));
        } else if (size > 4) {
            throw nameNotFound(name);
        }
        String appName = name.get(0);
        String moduleName = name.get(1);
        String distinctName;
        String lastPart = name.get(size - 1);
        int cp;
        String beanName = null;
        for (int i = 0; i < lastPart.length(); i = lastPart.offsetByCodePoints(i, 1)) {
            cp = lastPart.codePointAt(i);
            if (cp == '!') {
                beanName = lastPart.substring(0, i);
                lastPart = lastPart.substring(i + 1);
                break;
            } else if (cp == '?') {
                throw nameNotFound(name);
            }
        }
        if (beanName == null) {
            if (size == 3) {
                // name is of the form appName/moduleName/distinctName
                return new RelativeContext(new FastHashtable<>(getEnvironment()), this, SimpleName.of(name));
            }
            // no view type given; invalid
            throw nameNotFound(name);
        }
        // name is of the form appName/moduleName/distinctName/lastPart or appName/moduleName/lastPart
        distinctName = size == 4 ? name.get(2) : "";
        String viewType = null;
        for (int i = 0; i < lastPart.length(); i = lastPart.offsetByCodePoints(i, 1)) {
            cp = lastPart.codePointAt(i);
            if (cp == '?') {
                viewType = lastPart.substring(0, i);
                lastPart = lastPart.substring(i + 1);
                break;
            }
        }
        boolean stateful = false;
        if (viewType == null) {
            viewType = lastPart;
        } else {
            // parse, parse, parse
            int eq = -1, st = 0;
            for (int i = 0; i < lastPart.length(); i = lastPart.offsetByCodePoints(i, 1)) {
                cp = lastPart.codePointAt(i);
                if (cp == '=' && eq == -1) {
                    eq = i;
                }
                if (cp == '&') {
                    if ("stateful".equals(lastPart.substring(st, eq == -1 ? i : eq))) {
                        if (eq == -1 || "true".equalsIgnoreCase(lastPart.substring(eq + 1, i))) {
                            stateful = true;
                        }
                    }
                    st = cp + 1;
                    eq = -1;
                }
            }
            if ("stateful".equals(lastPart.substring(st, eq == -1 ? lastPart.length() : eq))) {
                if (eq == -1 || "true".equalsIgnoreCase(lastPart.substring(eq + 1))) {
                    stateful = true;
                }
            }
        }
        Class<?> view;
        try {
            view = Class.forName(viewType, false, getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw Logs.MAIN.lookupFailed(name, name, e);
        }
        final NamingProvider namingProvider = this.namingProvider;
        final EJBModuleIdentifier moduleIdentifier = new EJBModuleIdentifier(appName, moduleName, distinctName);
        final EJBIdentifier identifier = new EJBIdentifier(moduleIdentifier, beanName);
        final StatelessEJBLocator<?> statelessLocator = StatelessEJBLocator.create(view, identifier, baseAffinity);
        final Object proxy;
        if (stateful) {
            try {
                proxy = EJBClient.createSessionProxy(statelessLocator, namingProvider == null ? null : namingProvider.getProviderEnvironment().getAuthenticationContextSupplier() , namingProvider);
            } catch (Exception e) {
                throw Logs.MAIN.lookupFailed(name, name, e);
            }
        } else {
            proxy = EJBClient.createProxy(statelessLocator, namingProvider == null ? null : namingProvider.getProviderEnvironment().getAuthenticationContextSupplier());
        }
        if (namingProvider != null) EJBClient.putProxyAttachment(proxy, NAMING_PROVIDER_ATTACHMENT_KEY, namingProvider);

        // if "invocation.timeout" is set in environment properties, set this value to created proxy
        Long invocationTimeout = getLongValueFromEnvironment(PROPERTY_KEY_INVOCATION_TIMEOUT);
        if (invocationTimeout != null) {
            EJBClient.setInvocationTimeout(proxy, invocationTimeout.longValue(), TimeUnit.MILLISECONDS);
        }

        return proxy;
    }

    private static ClassLoader getContextClassLoader(){
        final SecurityManager sm = System.getSecurityManager();
        ClassLoader classLoader;
        if (sm != null) {
            classLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>) EJBRootContext::doGetContextClassLoader);
        } else {
            classLoader = doGetContextClassLoader();
        }
        return classLoader;
    }

    private static ClassLoader doGetContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    protected Object lookupLinkNative(final Name name) throws NamingException {
        return lookupNative(name);
    }

    protected CloseableNamingEnumeration<NameClassPair> listNative(final Name name) throws NamingException {
        throw notSupported();
    }

    protected CloseableNamingEnumeration<Binding> listBindingsNative(final Name name) throws NamingException {
        throw notSupported();
    }

    public void close() throws NamingException {
    }

    public String getNameInNamespace() throws NamingException {
        return "";
    }

    private Long getLongValueFromEnvironment(String key) throws NamingException {
        Object val = getEnvironment().get(key);
        if (val != null && val instanceof String) {
            return Long.valueOf(Long.parseLong((String) val));
        }
        return null;
    }

    /**
     * Check if the user has specified strong affinity to a cluster for this context and return the cluster name.
     * @return String the name of the cluster
     */
    private String getClusterAffinityValueFromEnvironment() {
        Object val;
        try {
            val = getEnvironment().get(EJBClient.CLUSTER_AFFINITY);
        } catch(NamingException ne) {
            Logs.MAIN.warn("Problem reading cluster affinity specification from env; skipping affinity assignment");
            return null;
        }
        if (val != null && val instanceof String) {
            return (String) val;
        }
        return null;
    }
}
