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
package org.jboss.ejb.client;

import org.jboss.ejb.client.naming.ejb.EjbNamingContextSetup;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.lang.reflect.Proxy;

/**
 * Tests the ejb: jndi context
 *
 * @author Stuart Douglas
 */
public class EjbNamespaceTestCase {

    @BeforeClass
    public static void before() {
        EjbNamingContextSetup.setupEjbNamespace();
    }

    @Test
    public void testEjbNamespaceLookup() throws NamingException {
        Object result = new InitialContext().lookup("ejb:app/module/distinct/MyEjb!org.jboss.ejb.client.SimpleInterface");
        Assert.assertTrue(result instanceof SimpleInterface);
        final EJBInvocationHandler<?> handler = (EJBInvocationHandler<?>) Proxy.getInvocationHandler(result);
        final EJBLocator<? extends SimpleInterface> locator = handler.getLocator().narrowTo(SimpleInterface.class);
        Assert.assertEquals("app", locator.getAppName());
        Assert.assertEquals("module", locator.getModuleName());
        Assert.assertEquals("distinct", locator.getDistinctName());
        Assert.assertEquals("MyEjb", locator.getBeanName());
    }

    @Test
    public void testEjbContextLookup() throws NamingException {
        Context context = (Context) new InitialContext().lookup("ejb:");
        context = (Context) context.lookup("app");
        context = (Context) context.lookup("module");
        context = (Context) context.lookup("distinct");
        Object result = context.lookup("MyEjb!org.jboss.ejb.client.SimpleInterface");
        Assert.assertTrue(result instanceof SimpleInterface);
        final EJBInvocationHandler<?> handler = (EJBInvocationHandler<?>) Proxy.getInvocationHandler(result);
        final EJBLocator<? extends SimpleInterface> locator = handler.getLocator().narrowTo(SimpleInterface.class);
        Assert.assertEquals("app", locator.getAppName());
        Assert.assertEquals("module", locator.getModuleName());
        Assert.assertEquals("distinct", locator.getDistinctName());
        Assert.assertEquals("MyEjb", locator.getBeanName());
    }

    @Test
    public void testEjbNamespaceLookupWithoutDistinct() throws NamingException {
        Object result = new InitialContext().lookup("ejb:app/module/MyEjb!org.jboss.ejb.client.SimpleInterface");
        Assert.assertTrue(result instanceof SimpleInterface);
        final EJBInvocationHandler<?> handler = (EJBInvocationHandler<?>) Proxy.getInvocationHandler(result);
        final EJBLocator<? extends SimpleInterface> locator = handler.getLocator().narrowTo(SimpleInterface.class);
        Assert.assertEquals("app", locator.getAppName());
        Assert.assertEquals("module", locator.getModuleName());
        Assert.assertEquals("", locator.getDistinctName());
        Assert.assertEquals("MyEjb", locator.getBeanName());
    }
}
