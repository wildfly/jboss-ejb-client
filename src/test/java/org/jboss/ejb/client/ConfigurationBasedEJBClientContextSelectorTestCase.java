/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client;

import java.io.File;
import java.net.URL;

import org.jboss.ejb.client.test.ClassCallback;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests some basic features of ConfigurationBasedEJBClientContextSelector
 *
 * @author <a href="mailto:jbaesner@redhat.com">Joerg Baesner</a>
 */
public class ConfigurationBasedEJBClientContextSelectorTestCase {

    private static final String CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME = "wildfly.config.url";
    private static final String CONFIGURATION_FILE = "wildfly-client.xml";

    /**
     * Do any general setup here
     * 
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // make sure the desired configuration file is picked up
        ClassLoader cl = ConfigurationBasedEJBClientContextSelectorTestCase.class.getClassLoader();
        URL resource = cl != null ? cl.getResource(CONFIGURATION_FILE) : ClassLoader.getSystemResource(CONFIGURATION_FILE);
        File file = new File(resource.getFile());
        System.setProperty(CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME,file.getAbsolutePath());
        ClassCallback.beforeClassCallback();
    }

    @Test
    public void testDeploymentNodeSelector() {
        EJBClientContext clientContext = EJBClientContext.getCurrent();
        DeploymentNodeSelector dns = clientContext.getDeploymentNodeSelector();

        Assert.assertNotNull(String.format("A <%s> is expected, but was <null>", DummyNodeSelector.class.getName()), dns);
        Assert.assertTrue(String.format("Expected an instance of <%s>, got <%s>", DummyNodeSelector.class, dns.getClass().getName()), dns instanceof DummyNodeSelector);
        Assert.assertEquals("Wrong <selectNode> value,", DummyNodeSelector.DEPLOYMENT_NODE_IDENTIFIER, dns.selectNode(null, null, null, null));
    }

    @Test
    public void testClusterNodeSelector() {
        EJBClientContext clientContext = EJBClientContext.getCurrent();
        ClusterNodeSelector cns = clientContext.getClusterNodeSelector();

        Assert.assertNotNull(String.format("A <%s> is expected, but was <null>", DummyNodeSelector.class.getName()), cns);
        Assert.assertTrue(String.format("Expected an instance of <%s>, got <%s>", DummyNodeSelector.class, cns.getClass().getName()), cns instanceof DummyNodeSelector);
        Assert.assertEquals("Wrong <selectNode> value,", DummyNodeSelector.CLUSTER_NODE_IDENTIFIER, cns.selectNode(null, null, null));
    }

    @Test
    public void testMaximumAllowedClusterNodes() {
        EJBClientContext clientContext = EJBClientContext.getCurrent();
        int nodes = clientContext.getMaximumConnectedClusterNodes();

        Assert.assertEquals("Wrong <max-allowed-connected-nodes> value,", 15, nodes);
    }

}
