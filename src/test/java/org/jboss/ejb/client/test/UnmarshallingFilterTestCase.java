/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client.test;

import java.io.InvalidClassException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.ejb.EJBException;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.TypeReporter;
import org.jboss.ejb.client.test.common.TypeReporterBean;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests server-sdie filtering of classes before unmarshalling.
 *
 * @author Brian Stansberry
 */
public class UnmarshallingFilterTestCase {
    private static final Logger logger = Logger.getLogger(LearningTestCase.class);

    // module
    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private static final String SERVER_NAME = "test-server";

    private static DummyServer server;
    private static boolean serverStarted = false;

    @BeforeClass
    public static void beforeTest() throws Exception {
        // start a server
        server = new DummyServer("localhost", 6999, SERVER_NAME);
        server.start();
        serverStarted = true;
        logger.info("Started server ...");

        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, TypeReporter.class.getSimpleName(), new TypeReporterBean());
        logger.info("Registered module ...");
    }

    @AfterClass
    public static void afterTest() {
        server.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, TypeReporterBean.class.getName());
        logger.info("Unregistered module ...");

        if (serverStarted) {
            try {
                server.stop();
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            }
        }
        logger.info("Stopped server ...");
    }

    /**
     * Test a basic invocation
     */
    @Test
    public void testUnmarshallingFiltering() {
        logger.info("Testing unmarshalling filtering");

        // create a proxy for invocation
        final StatelessEJBLocator<TypeReporter> statelessEJBLocator = new StatelessEJBLocator<TypeReporter>(TypeReporter.class, APP_NAME, MODULE_NAME, TypeReporter.class.getSimpleName(), DISTINCT_NAME);
        final TypeReporter proxy = EJBClient.createProxy(statelessEJBLocator);
        URI uri = null;
        try {
            uri = new URI("remote", null,"localhost", 6999, null, null,null);
        } catch(URISyntaxException use) {
            //
        }
        EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(uri));
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        logger.info("Invoking on proxy...");
        // invoke on the proxy (use a URIAffinity for now)
        final String type = proxy.getObjectType("hello");
        Assert.assertEquals("Got an unexpected type", String.class.getName(), type);

        try {
            final String bad = proxy.getObjectType(new IllegalArgumentException("bad"));
            Assert.fail("IllegalArgumentException was not rejected; got " + bad);
        } catch (EJBException e) {
            // The specific cause type isn't so important; checking it is just a guard against
            // the call failing for spurious reasons. If the impl changes such that this assert
            // is no longer correct it's fine to remove or change it.
            Assert.assertTrue(e.getCause().toString(), e.getCause() instanceof InvalidClassException);
        }
    }
}
