/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.NamingManager;
import java.util.Hashtable;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
class SimpleContext implements Context {
    private final Reference refInfo;
    private final Hashtable<?, ?> environment;

    public SimpleContext(Reference refInfo, Hashtable<?, ?> environment) {
        this.refInfo = refInfo;
        this.environment = environment;
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        try {
            return NamingManager.getObjectInstance(refInfo, name, this, environment);
        } catch (NamingException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object lookup(String name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.lookup");
    }

    @Override
    public void bind(Name name, Object obj) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.bind");
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.bind");
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.rebind");
    }

    @Override
    public void rebind(String name, Object obj) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.rebind");
    }

    @Override
    public void unbind(Name name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.unbind");
    }

    @Override
    public void unbind(String name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.unbind");
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.rename");
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.rename");
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.list");
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.list");
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.listBindings");
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.listBindings");
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.destroySubcontext");
    }

    @Override
    public void destroySubcontext(String name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.destroySubcontext");
    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.createSubcontext");
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.createSubcontext");
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.lookupLink");
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.lookupLink");
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.getNameParser");
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.getNameParser");
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.composeName");
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.composeName");
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.addToEnvironment");
    }

    @Override
    public Object removeFromEnvironment(String propName) throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.removeFromEnvironment");
    }

    @Override
    public Hashtable<?, ?> getEnvironment() throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.getEnvironment");
    }

    @Override
    public void close() throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.close");
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        throw new RuntimeException("NYI: org.jboss.ejb.client.naming.SimpleContext.getNameInNamespace");
    }
}
