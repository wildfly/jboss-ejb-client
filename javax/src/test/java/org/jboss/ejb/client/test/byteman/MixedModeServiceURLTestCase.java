/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client.test.byteman;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.test.AbstractEJBClientTestCase;
import org.jboss.ejb.client.test.ClassCallback;
import org.jboss.ejb.client.test.WildflyClientXMLTestCase;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.server.ClusterTopologyListener;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.discovery.ServiceURL;

/**
 * When receiving topology updates from clusters whose members have client mappings containing literal IP addresses,
 * the generation of ServiceURLs by discovery must be complete, even though entries in the DiscoveredNodeRegistry (DNR)
 * may refer to the same host by both its hostname and its literal IP address (e.g. localhost and 127.0.0.1).
 * <p>
 * This test verifies that the set of ServiceURLs generated is complete for both representations.
 *
 * @author rachmato@redhat.com
 */
@RunWith(BMUnitRunner.class)
@BMUnitConfig(debug = true)
public class MixedModeServiceURLTestCase extends AbstractEJBClientTestCase {

    private static final Logger logger = Logger.getLogger(MixedModeServiceURLTestCase.class);
    private static final String CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME = "wildfly.config.url";
    private static final String CONFIGURATION_FILE = "mixed-mode-wildfly-client.xml";

    // cluster entries which will generate mixed-mode topology updates
    private static final ClusterTopologyListener.NodeInfo MIXED_NODE1 = DummyServer.getNodeInfo(NODE1_NAME, "127.0.0.1", 6999, "0.0.0.0", 0);
    private static final ClusterTopologyListener.NodeInfo MIXED_NODE2 = DummyServer.getNodeInfo(NODE2_NAME, "127.0.0.1", 7099, "0.0.0.0", 0);
    private static final String MIXED_CLUSTER_NAME = "mixed-ejb";
    private static final ClusterTopologyListener.ClusterInfo MIXED_CLUSTER = DummyServer.getClusterInfo(MIXED_CLUSTER_NAME, MIXED_NODE1, MIXED_NODE2);


    /**
     * Do any general setup here
     *
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // make sure the desired configuration file is picked up
        ClassLoader cl = WildflyClientXMLTestCase.class.getClassLoader();
        URL resource = cl != null ? cl.getResource(CONFIGURATION_FILE) : ClassLoader.getSystemResource(CONFIGURATION_FILE);
        File file = new File(resource.getFile());
        System.setProperty(CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME, file.getAbsolutePath());

        // Launch callback if needed
        ClassCallback.beforeClassCallback();
    }

    /**
     * Set up a specific cluster configuration which will generate the mixed mode topology updates
     */
    @Before
    public void beforeTest() throws Exception {
        // start two servers with a simple bean deployment
        for (int i = 0; i < 2; i++) {
            startServer(i, "127.0.0.1", 6999 + (i * 100), false);
            deployStateless(i);
            deployStateful(i);
        }
        // put them in a cluster with specific client mappings
        defineCluster(0, MIXED_CLUSTER);
        defineCluster(1, MIXED_CLUSTER);
    }

    /**
     * Test simple invocation on a cluster where client mappings use literal IP addresses instead of hostnames, generating mixed mode entries in the DNR.
     * A Byteman script will verify whether or not the expected set of ServiceURLs has been correctly generated.
     */
    @BMRules(rules = {
            @BMRule(name = "Set up results linkMap (SETUP)",
                    targetClass = "org.jboss.ejb.protocol.remote.RemotingEJBDiscoveryProvider",
                    targetMethod = "<init>",
                    helper = "org.jboss.ejb.client.test.byteman.MixedModeTestHelper",
                    targetLocation = "EXIT",
                    condition = "debug(\"setting up the map\")",
                    action = "createNodeListMap();"),

            @BMRule(name = "Track calls to getServiceURLCache (COLLECT)",
                    targetClass = "org.jboss.ejb.protocol.remote.NodeInformation",
                    targetMethod = "getServiceURLCache",
                    helper = "org.jboss.ejb.client.test.byteman.MixedModeTestHelper",
                    targetLocation = "EXIT",
                    binding = "node = $0; nodeName = node.getNodeName(); result = $!",
                    condition = "debug(\"NodeInformation.getServiceURLCache() was called\")",
                    action = "addServiceURLCacheToMap(nodeName,result)"),

            @BMRule(name = "Track calls to discover (COLLECT)",
                    targetClass = "org.jboss.ejb.protocol.remote.NodeInformation",
                    targetMethod = "discover",
                    targetLocation = "ENTRY",
                    condition = "true",
                    action = "debug(\"NodeInformation.discover() was called\")"),

            @BMRule(name = "Return test result to test case (RETURN)",
                    targetClass = "org.jboss.ejb.client.test.byteman.MixedModeServiceURLTestCase",
                    targetMethod = "getTestResult",
                    helper = "org.jboss.ejb.client.test.byteman.MixedModeTestHelper",
                    targetLocation = "ENTRY",
                    condition = "debug(\"returning the result\")",
                    action = "return getNodeListMap();")
    })
    @Test
    public void testInvocationOnMixedModeCluster() {

        Affinity expectedStrongAffinity = Affinity.NONE;

        // create a proxy for SLSB
        final StatelessEJBLocator<Echo> statelessEJBLocator = StatelessEJBLocator.create(Echo.class, STATELESS_IDENTIFIER, expectedStrongAffinity);
        Echo proxy = EJBClient.createProxy(statelessEJBLocator);
        Assert.assertNotNull("Received a null proxy", proxy);

        // invoke on the proxy
        logger.info("Invoking on proxy...");
        final String message = "hello!";
        try {
            proxy.echo(message);
        } catch (RuntimeException e) {
            //don't do anything, it is expected
        }

        // get the test results from Byteman
        Map<String, List<ServiceURL>> results = getTestResult();

        // validate the results
        validateResults(results);
    }

    /*
     * Dummy method to allow returning Rule-collected results back to the test case for validation.
     * Byteman will populate the return value when the method is called.
     */
    private Map<String, List<ServiceURL>> getTestResult() {
        // injected code from Byteman will return the actual result
        return null;
    }


    /*
     * Check that the list of ServiceURLs returned by discovery is complete for the server environment we have set up.
     * Namely:
     * cluster "mixed-ejb" = {"127.0.0.1":6999, "127.0.0.1":7099}
     * deployments of a module on both nodes: {"my-foo-app/my-bar-module"}
     * logical node names (JBOSS_NODE_NAME values): {"node1", "node2"}
     */
    private void validateResults(Map<String, List<ServiceURL>> results) {

        // the complete set of ServiceURLs for the above configuration (i.e. same information and attributes for each host representation)
        final Set<String> EXPECTED_NODE1_URLS = new HashSet<String>(Arrays.asList(
                "service:ejb.jboss:remote://127.0.0.1:6999;node=node1;ejb-module=my-foo-app/my-bar-module",
                "service:ejb.jboss:remote://127.0.0.1:6999;cluster=mixed-ejb;node=node1;ejb-module=my-foo-app/my-bar-module",
                "service:ejb.jboss:remote://localhost:6999;node=node1;ejb-module=my-foo-app/my-bar-module",
                "service:ejb.jboss:remote://localhost:6999;cluster=mixed-ejb;node=node1;ejb-module=my-foo-app/my-bar-module"
        ));

        final Set<String> EXPECTED_NODE2_URLS = new HashSet<String>(Arrays.asList(
                "service:ejb.jboss:remote://127.0.0.1:7099;node=node2;ejb-module=my-foo-app/my-bar-module",
                "service:ejb.jboss:remote://127.0.0.1:7099;cluster=mixed-ejb;node=node2;ejb-module=my-foo-app/my-bar-module",
                "service:ejb.jboss:remote://localhost:7099;node=node2;ejb-module=my-foo-app/my-bar-module",
                "service:ejb.jboss:remote://localhost:7099;cluster=mixed-ejb;node=node2;ejb-module=my-foo-app/my-bar-module"
        ));

        Set<String> actual_node1_urls = new HashSet<String>();
        Set<String> actual_node2_urls = new HashSet<String>();

        // validate the actual ServiceURLs agains expected ServiceURLs
        for (Map.Entry<String, List<ServiceURL>> entry : results.entrySet()) {
            String node = entry.getKey();
            List<ServiceURL> listServiceURLs = (List<ServiceURL>) entry.getValue();
            if (node.equals("node1")) {
                for (ServiceURL serviceURL : listServiceURLs) {
                    actual_node1_urls.add(serviceURL.toString());
                }
            } else if (node.equals("node2")) {
                for (ServiceURL serviceURL : listServiceURLs) {
                    actual_node2_urls.add(serviceURL.toString());
                }
            }
        }
        Assert.assertEquals("ServiceURLs do not match for node1", actual_node1_urls, EXPECTED_NODE1_URLS);
        Assert.assertEquals("ServiceURLs do not match for node2", actual_node2_urls, EXPECTED_NODE2_URLS);
    }

    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {

        // wipe cluster
        removeCluster(0, MIXED_CLUSTER_NAME);
        removeCluster(1, MIXED_CLUSTER_NAME);

        // undeploy servers
        for (int i = 0; i < 2; i++) {
            undeployStateful(i);
            undeployStateless(i);
            stopServer(i);
        }
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
    }

}
