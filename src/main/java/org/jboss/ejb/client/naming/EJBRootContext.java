/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.ejb.client.naming;

import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.naming.Binding;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NamingException;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.wildfly.naming.client.AbstractContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;
import org.wildfly.naming.client.SimpleName;
import org.wildfly.naming.client.store.RelativeContext;
import org.wildfly.naming.client.util.FastHashtable;

class EJBRootContext extends AbstractContext {
    private final Affinity affinity;

    EJBRootContext(final URI providerUri, final FastHashtable<String, Object> env) {
        super(env);
        if (providerUri == null) {
            affinity = Affinity.NONE;
        } else {
            final String scheme = providerUri.getScheme();
            if (scheme == null) {
                affinity = Affinity.NONE;
            } else {
                affinity = Affinity.forUri(providerUri);
            }
        }
    }

    protected Object lookupNative(final Name name) throws NamingException {
        final int size = name.size();
        if (size < 4) {
            return new RelativeContext(new FastHashtable<>(getEnvironment()), this, SimpleName.of(name));
        } else if (size > 4) {
            throw nameNotFound(name);
        }
        String appName = name.get(0);
        String moduleName = name.get(1);
        String distinctName = name.get(2);
        String lastPart = name.get(3);
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
            // no view type given; invalid
            throw nameNotFound(name);
        }
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
        }
        Class<?> view;
        try {
            view = Class.forName(viewType, false, getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw Logs.MAIN.lookupFailed(name, name, e);
        }
        EJBLocator<?> locator;
        if (stateful) {
            try {
                locator = EJBClient.createSession(affinity, view, appName, moduleName, beanName, distinctName);
            } catch (Exception e) {
                throw Logs.MAIN.lookupFailed(name, name, e);
            }
        } else {
            locator = createLocator(view, appName, moduleName, beanName, distinctName);
        }
        return EJBClient.createProxy(locator);
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

    private <T> StatelessEJBLocator<T> createLocator(Class<T> viewType, String appName, String moduleName, String beanName, String distinctName) {
        return new StatelessEJBLocator<T>(viewType, appName, moduleName, beanName, distinctName, affinity);
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
}
