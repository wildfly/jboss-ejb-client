/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import org.jboss.ejb.client.EJBClient;
import org.jboss.logging.Logger;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: jpai
 */
class InitialContext implements Context {

    private static final Logger logger = Logger.getLogger(InitialContext.class);

    // TODO: This pattern needs to be fixed. For example, appName and moduleName can contain special characters
    private static final Pattern GLOBAL_JNDI_NAME_PATTERN = Pattern.compile("(java:global/)([a-zA-Z]+[/])?([a-zA-Z]+[/])([a-zA-Z_\\$]+)([\\!][a-zA-Z_\\.\\$]+)?");

    private final EJBClient ejbClient;

    private final Hashtable environment;

    public InitialContext(Hashtable<?, ?> environment) throws NamingException {
        this.environment = environment;
        // TODO: this is a hack for now
        if (environment != null && environment.containsKey(EJBClient.EJB_REMOTE_PROVIDER_URI)) {
            this.ejbClient = new EJBClient(environment);
        } else {
            this.ejbClient = null;
        }
    }

    @Override
    public Object lookup(String name) throws NamingException {
        // TODO: Major hack for now! We only support java:global EJB3.1 jndi names
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        Matcher matcher = GLOBAL_JNDI_NAME_PATTERN.matcher(name);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid jndi name, cannot lookup " + name);
        }
        if (this.ejbClient == null) {
            throw new NamingException("Need to specify " + EJBClient.EJB_REMOTE_PROVIDER_URI + " while creating " + this.getClass().getName());
        }
        String appName = null;
        String moduleName = null;
        String beanName = null;
        String beanInterface = null;
        // strip off the "!" from the beginning of last group
        if (matcher.group(5) != null) {
            beanInterface = matcher.group(5).substring(1);
        }

        beanName = matcher.group(4);

        final String moduleNameGroup = matcher.group(3);
        if (moduleNameGroup != null) {
            // strip off the "/" from the end
            moduleName = moduleNameGroup.substring(0, moduleNameGroup.length() - 1);
        }

        final String appNameGroup = matcher.group(2);
        if (appNameGroup != null) {
            // strip off "/" from the end
            appName = appNameGroup.substring(0, appNameGroup.length() - 1);
        }
        logger.debug("Creating proxy for appName: " + appName + " moduleName: " + moduleName + " beanName: " + beanName + " beanInterface " + beanInterface);
        Class<?> beanInterfaceType = null;
        if (beanInterface != null) {
            try {
                beanInterfaceType = this.loadClass(beanInterface);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot lookup " + name, e);
            }
        }
        return this.ejbClient.getProxy(appName, moduleName, beanName, beanInterfaceType);
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }


    @Override
    public void bind(Name name, Object obj) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void rebind(String name, Object obj) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void unbind(Name name) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void unbind(String name) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void destroySubcontext(String name) throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Object removeFromEnvironment(String propName) throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Hashtable<?, ?> getEnvironment() throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void close() throws NamingException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private Class<?> loadClass(final String klass) throws ClassNotFoundException {
        if (klass == null) {
            return null;
        }
        return Class.forName(klass, false, Thread.currentThread().getContextClassLoader());
    }

    public static void main(String[] args) throws Exception {
        InitialContext ctx = new InitialContext(new Hashtable<Object, Object>());
        ctx.lookup("java:global/app/module/bean!org.jboss.ejb.client.test.SimpleLookupTestCase$SimpleRemoteBusinessInterface");
        ctx.lookup("java:global/b/c!d");
        ctx.lookup("java:global/a/b/c");
    }

}
