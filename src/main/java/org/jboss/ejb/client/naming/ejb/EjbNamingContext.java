/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.ejb.client.naming.ejb;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBHomeLocator;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.Logs;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.logging.Logger;

import javax.ejb.EJBHome;
import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.util.Hashtable;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
class EjbNamingContext implements Context {

    private static final Logger log = Logger.getLogger("org.jboss.ejb.client.naming");

    public static final EjbNamingContext ROOT = new EjbNamingContext();

    /* The environment configuration */
    private final Hashtable<String, Object> environment = new Hashtable<String, Object>();

    private final boolean root;
    private final String application;
    private final String module;
    private final String distinct;

    protected EjbNamingContext(final String application, final String module, final String distinct) {
        this.application = application;
        this.module = module;
        this.distinct = distinct;
        root = false;
    }

    protected EjbNamingContext() {
        application = null;
        module = null;
        distinct = null;
        root = true;
    }

    @Override
    public Object lookup(final Name name) throws NamingException {
        return lookup(name.toString());
    }

    @Override
    public Object lookup(final String name) throws NamingException {
        final EjbJndiIdentifier identifier;
        if (root) {
            identifier = EjbJndiNameParser.parse(name);
        } else if (application == null || application.isEmpty()) {
            identifier = EjbJndiNameParser.parse("ejb:" + name);
        } else if (module == null || module.isEmpty()) {
            identifier = EjbJndiNameParser.parse(application, name);
        } else if (distinct == null || distinct.isEmpty()) {
            identifier = EjbJndiNameParser.parse(application, module, name);
        } else {
            identifier = EjbJndiNameParser.parse(application, module, distinct, name);
        }
        if (identifier.getEjbName() == null) {
            return createEjbContext(identifier);
        }
        return createEjbProxy(identifier);
    }

    private Object createEjbContext(final EjbJndiIdentifier identifier) {
        return new EjbNamingContext(identifier.getApplication(), identifier.getModule(), identifier.getDistinctName());
    }

    protected Object createEjbProxy(final EjbJndiIdentifier identifier) throws NamingException {
        final Class<?> viewClass;
        try {
            viewClass = Class.forName(identifier.getViewName(), false, SecurityActions.getContextClassLoader());
        } catch (ClassNotFoundException e) {
            NamingException naming = Logs.MAIN.couldNotLoadProxyClass(identifier.getViewName());
            naming.setRootCause(e);
            throw naming;
        }
        try {
            return EJBHome.class.isAssignableFrom(viewClass) ? doCreateHomeProxy(viewClass.asSubclass(EJBHome.class), identifier) : doCreateProxy(viewClass, identifier);
        } catch (Exception e) {
            NamingException ne = new NamingException("Failed to create proxy");
            ne.initCause(e);
            throw ne;
        }
    }

    private <T extends EJBHome> T doCreateHomeProxy(Class<T> viewClass, EjbJndiIdentifier identifier) throws Exception {
        final EJBLocator<T> locator;
        final Map<String, String> options = identifier.getOptions();
        final boolean stateful = options.containsKey("stateful") && !"false".equalsIgnoreCase(options.get("stateful"));
        if (stateful) log.warnf("Ignoring 'stateful' option on lookup of home %s", viewClass);
        locator = new EJBHomeLocator<T>(viewClass, identifier.getApplication(), identifier.getModule(), identifier.getEjbName(), identifier.getDistinctName());
        return EJBClient.createProxy(locator);
    }

    private <T> T doCreateProxy(Class<T> viewClass, EjbJndiIdentifier identifier) throws Exception {
        final EJBLocator<T> locator;
        final Map<String, String> options = identifier.getOptions();
        final boolean stateful = options.containsKey("stateful") && !"false".equalsIgnoreCase(options.get("stateful"));
        if (stateful) {
            locator = EJBClient.createSession(Affinity.NONE, viewClass, identifier.getApplication(), identifier.getModule(), identifier.getEjbName(), identifier.getDistinctName());
        } else {
            locator = new StatelessEJBLocator<T>(viewClass, identifier.getApplication(), identifier.getModule(), identifier.getEjbName(), identifier.getDistinctName(), Affinity.NONE);
        }
        return EJBClient.createProxy(locator);
    }

    @Override
    public void bind(final Name name, final Object obj) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public void bind(final String name, final Object obj) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public void rebind(final Name name, final Object obj) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public void rebind(final String name, final Object obj) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public void unbind(final Name name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public void unbind(final String name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public void rename(final Name oldName, final Name newName) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public void rename(final String oldName, final String newName) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(final Name name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperation();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(final String name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperation();
    }

    @Override
    public NamingEnumeration<Binding> listBindings(final Name name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperation();
    }

    @Override
    public NamingEnumeration<Binding> listBindings(final String name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperation();
    }

    @Override
    public void destroySubcontext(final Name name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public void destroySubcontext(final String name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public Context createSubcontext(final Name name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public Context createSubcontext(final String name) throws NamingException {
        throw Logs.MAIN.unsupportedNamingOperationForReadOnlyContext();
    }

    @Override
    public Object lookupLink(final Name name) throws NamingException {
        return lookup(name);
    }

    @Override
    public Object lookupLink(final String name) throws NamingException {
        return lookup(name);
    }

    @Override
    public NameParser getNameParser(final Name name) throws NamingException {
        return new NameParser() {
            @Override
            public Name parse(final String name) throws NamingException {
                return new CompositeName(name);
            }
        };
    }

    @Override
    public NameParser getNameParser(final String name) throws NamingException {
        return new NameParser() {
            @Override
            public Name parse(final String name) throws NamingException {
                return new CompositeName(name);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    public Name composeName(Name name, Name prefix) throws NamingException {
        final Name result = (Name) prefix.clone();
        result.addAll(name);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public String composeName(String name, String prefix) throws NamingException {
        return composeName(new CompositeName(name), new CompositeName(prefix)).toString();
    }

    /**
     * {@inheritDoc}
     */
    public Object addToEnvironment(String propName, Object propVal) throws NamingException {
        final Object existing = environment.get(propName);
        environment.put(propName, propVal);
        return existing;
    }

    /**
     * {@inheritDoc}
     */
    public Object removeFromEnvironment(String propName) throws NamingException {
        return environment.remove(propName);
    }

    /**
     * {@inheritDoc}
     */
    public Hashtable<?, ?> getEnvironment() throws NamingException {
        return environment;
    }

    @Override
    public void close() throws NamingException {
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        if (application == null) {
            return "ejb:";
        } else if (module == null) {
            return "ejb:" + application;
        } else if (distinct == null) {
            return "ejb:" + application + "/" + module;
        } else {
            return "ejb:" + application + "/" + module + "/" + distinct;
        }
    }

}
