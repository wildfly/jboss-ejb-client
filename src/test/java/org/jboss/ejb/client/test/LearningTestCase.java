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

package org.jboss.ejb.client.test;

import javax.naming.NamingException;
import java.net.URI;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.Foo;
import org.jboss.ejb.client.test.common.FooBean;
import org.jboss.ejb.client.test.common.StatefulEchoBean;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;


/**
 * Tests support for a feature where proxies constructed from the same JNDI initial context share iniial values for strong affinity.
 *
 * @author Jason T. Greene
 */
public class LearningTestCase extends AbstractEJBClientTestCase {
    private static final Logger logger = Logger.getLogger(LearningTestCase.class);

    /*
     * Before each test, start a singleton server and deploy two stateful applications.
     */
    @Before
    public void beforeTest() throws Exception {
        // start a server
        startServer(0, 6999);
        // deploy a stateful bean
        deployStateful(0);
        // deploy a different bean
        deployCustomBean(0, APP_NAME, MODULE_NAME, DISTINCT_NAME, FooBean.class.getSimpleName(), new FooBean());
    }

    /*
     * After each test, undeploy the stateful applications and shutdown the server.
     */
    @After
    public void afterTest() {
        undeployStateful(0);
        undeployCustomBean(0, APP_NAME, MODULE_NAME, DISTINCT_NAME, FooBean.class.getName());
        stopServer(0);
    }

    /**
     * A helper method for strong affinity validation of created proxies, which does the following:
     *
     * - create a JNDI context using the properties specified for initializing the context
     * - using the JNDI context, create a first proxy and validate its expected strong affinity; because the bean
     * is stateful, this will result in an interaction with the server which will update the weak affinity
     * - set the strong affinity of the first proxy to cluster affinity and make an invocation
     * - using the JNDI context, create a second proxy and validate its expected strong affinity
     *
     * @param props the properties used to initialize the JNDI conext
     * @param match1 the expected strong affinty value of the first proxy created from the JNDI context
     * @param match2 the expected strong affinity of the second proxy created from the JNDI context
     * @throws NamingException
     */
    private void verifyAffinity(FastHashtable<String, Object> props, Affinity match1, Affinity match2) throws NamingException {
        // initialize the JNDI context
        WildFlyRootContext context = new WildFlyRootContext(props);

        // create a stateful proxy using JNDI lookup, which results in an interaction with the server, possibly updating the weak affinity
        Object echo = context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + StatefulEchoBean.class.getSimpleName() + "!" + Echo.class.getName() + "?stateful");
        Assert.assertEquals(match1, EJBClient.getStrongAffinity(echo));

        // reset the strong affinity of the proxy and make an invocation
        EJBClient.setStrongAffinity(echo, new ClusterAffinity("test"));
        ((Echo)echo).echo("test");

        // create a second stateful proxy using JNDI lookup, which results in an interaction with the server, possibly updating the weak affinity
        Object foo = context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + FooBean.class.getSimpleName() + "!" + Foo.class.getName() + "?stateful");
        Assert.assertEquals(match2, EJBClient.getStrongAffinity(foo));
    }

    /**
     * A helper method for strong affinity validation of created proxies, which uses an existing JNDI context for
     * proxy creation.
     *
     * @param context the JNDI context used to create the proxies
     * @param match1 the expected strong affinty value of the first proxy created from the JNDI context
     * @param match2 the expected strong affinity of the second proxy created from the JNDI context
     * @throws NamingException
     */
    private void verifyAffinity(WildFlyRootContext context, Affinity match1, Affinity match2) throws NamingException {
          Object echo = context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + StatefulEchoBean.class.getSimpleName() + "!" + Echo.class.getName() + "?stateful");
          Assert.assertEquals(match1, EJBClient.getStrongAffinity(echo));

          EJBClient.setStrongAffinity(echo, new ClusterAffinity("test"));
          ((Echo)echo).echo("test");

          Object foo = context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + FooBean.class.getSimpleName() + "!" + Foo.class.getName() + "?stateful");
          Assert.assertEquals(match2, EJBClient.getStrongAffinity(foo));
      }

    /**
     * Verify, using a shared JNDI context, that the created proxies have the following strong affinity values:
     * - the first proxy has an initial strong affinity of type URIAffinity, based on its JNDI context PROVIDER_URL
     * - the second proxy has an initial strong affinity of type ClusterAffinity, "learned" from the shared
     *   JNDI context and the activity of the first proxy
     *
     * @throws Exception
     */
    @Test
     public void testLearning() throws Exception {
         FastHashtable<String, Object> props = new FastHashtable<>();
         props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
         props.put("java.naming.provider.url", "remote://localhost:6999");

         URIAffinity serverURIAffinity = new URIAffinity(new URI("remote://localhost:6999"));
         verifyAffinity(props, serverURIAffinity, new ClusterAffinity("test"));
     }

    /**
     * Verify, using a shared JNDI context with learning disabled, that the created proxies have the following
     * strong affinity values:
     * - the first proxy has an initial strong affinity of type URIAffinity, based on its JNDI context PROVIDER_URL
     * - the second proxy has an initial strong affinity of type URIAffinity, based on its JNDI context PROVIDER_URL
     *   as learning has been disabled
     *
     * @throws Exception
     */
    @Test
    public void testDisabledLearning() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        props.put("java.naming.provider.url", "remote://localhost:6999");
        props.put("jboss.disable-affinity-learning", "true");

        URIAffinity serverURIAffinity = new URIAffinity(new URI("remote://localhost:6999"));
        verifyAffinity(props, serverURIAffinity, new URIAffinity(new URI("remote://localhost:6999")));
    }

    /**
     * Verify, using a shared JNDI context, that the created proxies have the following strong affinity values:
     * - the first proxy has an initial strong affinity of type ClusterAffinity, based on the use of the property
     *   jboss.cluster-affinity to set the default value of strong affinity for the JNDI context
     * - the second proxy has an initial strong affinity of type ClusterAffinity, based on learning
     *
     * @throws Exception
     */
    @Test
    public void testExplicitlyDefined() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        props.put("java.naming.provider.url", "remote://localhost:6999");
        props.put("jboss.cluster-affinity", "bob");

        ClusterAffinity bob = new ClusterAffinity("bob");
        verifyAffinity(props, bob, bob);
    }

    /**
     * THis test does not seem correct!
     * @throws Exception
     */
    @Test
    public void testNoInterference() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        props.put("java.naming.provider.url", "remote://localhost:6999");

        WildFlyRootContext ctx1 = new WildFlyRootContext(props.clone());

        URIAffinity serverURIAffinity = new URIAffinity(new URI("remote://localhost:6999"));
        verifyAffinity(props, serverURIAffinity, new ClusterAffinity("test"));
        verifyAffinity(ctx1, serverURIAffinity, new ClusterAffinity("test"));

        ClusterAffinity bob = new ClusterAffinity("bob");
    }
}
