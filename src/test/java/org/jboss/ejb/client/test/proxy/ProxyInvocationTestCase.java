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

package org.jboss.ejb.client.test.proxy;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.ejb.client.EJBClient;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URI;

/**
 * User: jpai
 */
@Ignore("This test should not be here, but in AS7 codebase")
@RunWith(Arquillian.class)
@RunAsClient
public class ProxyInvocationTestCase {

    private static final Logger logger = Logger.getLogger(ProxyInvocationTestCase.class);
    
    @Deployment
    public static Archive<?> createDeployment() {
        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, "myapp.ear");

        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "ejb.jar");
        jar.addPackage(ProxyInvocationTestCase.class.getPackage());

        ear.addAsModule(jar);

        return ear;
    }
    
    //@Ignore("The test should not rely on the remote endpoint being there.")
    @Test
    public void testJndiLookup() throws Exception {
        RemoteEcho proxy = EJBClient.proxy(new URI("remote://localhost:9999"), "myapp", "ejb", "EchoBean", RemoteEcho.class);
        final String message = "Hello World!!!";
        final String echoMessage = proxy.echo(message);
        logger.info("Got echo: " + echoMessage + " for message " + message);
        Assert.assertEquals("Unexpected echo", message, echoMessage);

    }

}
