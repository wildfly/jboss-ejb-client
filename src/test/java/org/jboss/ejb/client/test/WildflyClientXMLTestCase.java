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
package org.jboss.ejb.client.test;

import org.jboss.ejb.client.EJBClientContext;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;

/**
 * Tests some basic features of using a wildfly-config.xml file to configure the various contextuals which
 * the EJB client library depends on for configuration of key cross cutting concerns.
 *
 * This processing reads a wildfly-config.xml file from the classpath (or one referenced by the system property
 * wildfly.config.url) and uses the configuration in that file to configure the following contextuals:
 * - Endpoint
 * - XNIOWorker
 * - EJBClientContext
 * - AuthenticationConfiguration
 * - Discovery
 * - WildflyHttpContext
 *
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 *
 * @todo this test should include complete coverage of all available settings realting to the EJBClientContext
 * at a minimum.
 */
public class WildflyClientXMLTestCase {

    private static final Logger logger = Logger.getLogger(WildflyClientXMLTestCase.class);
    private static final String CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME = "wildfly.config.url";
    private static final String CONFIGURATION_FILE = "wildfly-client.xml";
    private static final long INVOCATION_TIMEOUT = 10*1000;

    /**
     * Initialize the contextuals by setting the wildfly.config.url system property
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // make sure the desired configuration file is picked up for processing
        ClassLoader cl = WildflyClientXMLTestCase.class.getClassLoader();
        URL resource = cl != null ? cl.getResource(CONFIGURATION_FILE) : ClassLoader.getSystemResource(CONFIGURATION_FILE);
        File file = new File(resource.getFile());
        System.setProperty(CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME,file.getAbsolutePath());
        ClassCallback.beforeClassCallback();
    }

    /*
     * Tests that the invocation timeout value set in wildfly-config.xml is used to populate the invocation timeout
     * value in the EJBClientContext.
     */
    @Test
    public void testInvocationTimeout() {
        EJBClientContext clientContext = EJBClientContext.getCurrent();
        Assert.assertEquals("Got an unexpected timeout value", INVOCATION_TIMEOUT, clientContext.getInvocationTimeout());
    }
}
