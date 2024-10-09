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

import jakarta.ejb.EJBException;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.TypeReporter;
import org.jboss.ejb.client.test.common.TypeReporterBean;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests server-side filtering of classes before unmarshalling.
 *
 * A class resolver filter is a Function<String, boolean> which allows defining a mapping of class names to the
 * boolean values true or false and is used to control which invocation paramaters may be unmarshalled by the server.
 *
 * The class resolver filter is a parameter to the RemoteEJBService, used to initialize the server-side handing
 * of incoming EJB client requests. If the class resolver filter is null, no filtering is performed. Otherwise,
 * filtering is performed based on parameter class names and the boolean value returned by the class resolver filter.
 *
 * The DummyServer used in these test cases has a class resolver filter which rejects unmarshalling of the
 * IllegalArgumentException class only.
 *
 * @author Brian Stansberry
 */
public class UnmarshallingFilterTestCase extends AbstractEJBClientTestCase {
    private static final Logger logger = Logger.getLogger(UnmarshallingFilterTestCase.class);

    @Before
    public void beforeTest() throws Exception {
        // start a server
        startServer(0);
        deployCustomBean(0, APP_NAME, MODULE_NAME, DISTINCT_NAME, TypeReporter.class.getSimpleName(), new TypeReporterBean());
    }

    @After
    public void afterTest() {
        undeployCustomBean(0, APP_NAME, MODULE_NAME, DISTINCT_NAME, TypeReporterBean.class.getName());
        stopServer(0);
    }

    /**
     * Test the operation of the class resolver filter with an invocation whose paraeter type should be filtered,
     * as well as with an invocation whose parameter type shpuld not be filtered.
     */
    @Test
    public void testUnmarshallingFiltering() {
        logger.info("Testing unmarshalling filtering");

        // create a proxy for invocation
        final StatelessEJBLocator<TypeReporter> statelessEJBLocator = new StatelessEJBLocator<TypeReporter>(TypeReporter.class, APP_NAME, MODULE_NAME, TypeReporter.class.getSimpleName(), DISTINCT_NAME);
        final TypeReporter proxy = EJBClient.createProxy(statelessEJBLocator);

        // set the target for the invocation
        URI uri = null;
        try {
            uri = new URI("remote", null,"localhost", 6999, null, null,null);
        } catch(URISyntaxException use) {
            //
        }
        EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(uri));
        Assert.assertNotNull("Received a null proxy", proxy);
        logger.info("Created proxy for Echo: " + proxy.toString());

        // perform an invocation with parameter type String, which we expect not to be filtered
        logger.info("Invoking on proxy...");
        final String type = proxy.getObjectType("hello");
        Assert.assertEquals("Got an unexpected type", String.class.getName(), type);

        // perform an invocation with parameter type IllegalArgumentException, which we expect to be filtered
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
