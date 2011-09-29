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

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class EjbJndiNameParserTestCase {

    @Test
    public void testApplicationContextName() {
        EjbJndiIdentifier result = EjbJndiNameParser.parse("ejb:appName");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertNull(result.getModule());
        result = EjbJndiNameParser.parse("ejb:appName/");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertNull(result.getModule());
    }

    @Test
    public void testModuleContextName() {
        EjbJndiIdentifier result = EjbJndiNameParser.parse("ejb:appName/moduleName");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertEquals("moduleName", result.getModule());
        Assert.assertNull(result.getDistinctName());
        Assert.assertNull(result.getEjbName());
        result = EjbJndiNameParser.parse("ejb:appName/moduleName/");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertEquals("moduleName", result.getModule());
        Assert.assertNull(result.getDistinctName());
        Assert.assertNull(result.getEjbName());
    }

    @Test
    public void testDistinctContextName() {
        EjbJndiIdentifier result = EjbJndiNameParser.parse("ejb:appName/moduleName/distinctName");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertEquals("moduleName", result.getModule());
        Assert.assertEquals("distinctName", result.getDistinctName());
        Assert.assertNull(result.getEjbName());
        result = EjbJndiNameParser.parse("ejb:appName/moduleName/distinctName/");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertEquals("moduleName", result.getModule());
        Assert.assertEquals("distinctName", result.getDistinctName());
        Assert.assertNull(result.getEjbName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEjbNameNoView() {
        EjbJndiNameParser.parse("ejb:appName/moduleName/distinctName/ejbName");
    }

    @Test
    public void testEjbViewNoDistinctName() {
        EjbJndiIdentifier result = EjbJndiNameParser.parse("ejb:appName/moduleName/ejbName!com.myView");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertEquals("moduleName", result.getModule());
        Assert.assertEquals("", result.getDistinctName());
        Assert.assertEquals("ejbName", result.getEjbName());
        Assert.assertEquals("com.myView", result.getViewName());
        Assert.assertEquals(0, result.getOptions().size());
    }

    @Test
    public void testEjbViewWithDistinctName() {
        EjbJndiIdentifier result = EjbJndiNameParser.parse("ejb:appName/moduleName/distinctName/ejbName!com.myView");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertEquals("moduleName", result.getModule());
        Assert.assertEquals("distinctName", result.getDistinctName());
        Assert.assertEquals("ejbName", result.getEjbName());
        Assert.assertEquals("com.myView", result.getViewName());
        Assert.assertEquals(0, result.getOptions().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEjbViewInvalidOption() {
        EjbJndiIdentifier result = EjbJndiNameParser.parse("ejb:appName/moduleName/distinctName/ejbName!com.myView?a=b=c");
    }

    @Test
    public void testEjbOptions() {
        EjbJndiIdentifier result = EjbJndiNameParser.parse("ejb:appName/moduleName/distinctName/ejbName!com.myView?stateful&fast=true&");
        Assert.assertEquals("appName", result.getApplication());
        Assert.assertEquals("moduleName", result.getModule());
        Assert.assertEquals("distinctName", result.getDistinctName());
        Assert.assertEquals("ejbName", result.getEjbName());
        Assert.assertEquals("com.myView", result.getViewName());
        Assert.assertEquals(2, result.getOptions().size());
        Assert.assertTrue(result.getOptions().containsKey("stateful"));
        Assert.assertNull(result.getOptions().get("stateful"));
        Assert.assertTrue(result.getOptions().containsKey("fast"));
        Assert.assertEquals("true", result.getOptions().get("fast"));

    }
}
