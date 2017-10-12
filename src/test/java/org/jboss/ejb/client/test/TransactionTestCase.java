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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import com.arjuna.ats.internal.jbossatx.jta.jca.XATerminator;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.transaction.client.ContextTransactionManager;
import org.wildfly.transaction.client.ContextTransactionSynchronizationRegistry;
import org.wildfly.transaction.client.LocalTransactionContext;
import org.wildfly.transaction.client.LocalUserTransaction;
import org.wildfly.transaction.client.RemoteTransactionContext;
import org.wildfly.transaction.client.provider.jboss.JBossLocalTransactionProvider;

/**
 * Tests basic invocation of a bean deployed on a single server node.
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class TransactionTestCase {
    private static final Logger logger = Logger.getLogger(TransactionTestCase.class);
    private static ContextTransactionManager txManager;
    private static ContextTransactionSynchronizationRegistry txSyncRegistry;

    private DummyServer server1, server2, server3, server4;
    private boolean serverStarted = false;

    // module
    private static final String APP_NAME = "my-foo-app";
    private static final String OTHER_APP = "my-other-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private static final String SERVER1_NAME = "server1";
    private static final String SERVER2_NAME = "server2";
    private static final String SERVER3_NAME = "server3";
    private static final String SERVER4_NAME = "server4";

    /**
     * Do any general setup here
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        final JTAEnvironmentBean jtaEnvironmentBean = jtaPropertyManager.getJTAEnvironmentBean();
        jtaEnvironmentBean.setTransactionManagerClassName(TransactionManagerImple.class.getName());
        jtaEnvironmentBean.setTransactionSynchronizationRegistryClassName(TransactionSynchronizationRegistryImple.class.getName());
        final TransactionManager narayanaTm = jtaEnvironmentBean.getTransactionManager();
        final TransactionSynchronizationRegistry narayanaTsr = jtaEnvironmentBean.getTransactionSynchronizationRegistry();
        final XATerminator xat = new XATerminator();
        final JBossLocalTransactionProvider.Builder builder = JBossLocalTransactionProvider.builder();
        builder.setXATerminator(xat).setExtendedJBossXATerminator(xat);
        builder.setTransactionManager(narayanaTm);
        builder.setTransactionSynchronizationRegistry(narayanaTsr);
        LocalTransactionContext.getContextManager().setGlobalDefault(new LocalTransactionContext(builder.build()));
        txManager = ContextTransactionManager.getInstance();
        txSyncRegistry = ContextTransactionSynchronizationRegistry.getInstance();
    }

    /**
     * Do any test specific setup here
     */
    @Before
    public void beforeTest() throws Exception {
        // start a server
        server1 = new DummyServer("localhost", 6999, SERVER1_NAME, true);
        server2 = new DummyServer("localhost", 7999, SERVER2_NAME, true);
        server3 = new DummyServer("localhost", 8999, SERVER3_NAME, true);
        server4 = new DummyServer("localhost", 9999, SERVER4_NAME, true);
        server1.start();
        server2.start();
        server3.start();
        server4.start();
        serverStarted = true;
        logger.info("Started servers ...");

        server1.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(SERVER1_NAME));
        server3.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(SERVER3_NAME));
        server4.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(SERVER4_NAME));

        server2.register(OTHER_APP, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(SERVER2_NAME));
        server3.register(OTHER_APP, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(SERVER3_NAME));
        server4.register(OTHER_APP, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(SERVER4_NAME));
    }

    @Test
    public void testTransactionStickiness() throws Exception {
        verifyStickiness(false, false);
    }

    @Test
    public void testRemoteTransactionStickiness() throws Exception {
        verifyStickiness(false, true);
    }

    @Test
    public void testRemoteStatefulTransactionStickiness() throws Exception {
        verifyStickiness(true, true);
    }

    @Test
    public void testStatefulTransactionStickiness() throws Exception {
        verifyStickiness(true, false);
    }

    @Test
    public void testNonPropagatingInvocation() throws Exception {
        verifyNonTxBehavior(false, false);
    }

    @Test
    public void testStatefulNonPropagatingInvocation() throws Exception {
        // Session open is sticky, resulting in a weak affinity and therefore
        // sticykness even with non-propogating method calls.
        verifyNonTxBehavior(true, true);
    }

    private UserTransaction getTransaction(boolean remote) {
        if (remote) {
            return RemoteTransactionContext.getInstance().getUserTransaction();
        } else {
            return LocalUserTransaction.getInstance();
        }

    }

    private void verifyStickiness(boolean stateful, boolean remote) throws Exception {
        UserTransaction transaction = getTransaction(remote);
        FastHashtable<String, Object> props = new FastHashtable<>();

        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", "remote://localhost:6999, remote://localhost:7999, remote://localhost:8999, remote://localhost:9999");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        HashSet<String> ids = new HashSet<>();

        for (int attempts = 0; attempts < 40; attempts++) {
            transaction.begin();
            HashMap<String, Integer> replies = new HashMap<>();
            String id = null;
            for (int i = 0; i < 20; i++) {

                Echo echo = (Echo) context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + EchoBean.class.getSimpleName() + "!"
                        + Echo.class.getName() + (stateful ? "?stateful" : ""));
                id = echo.whoAreYou();
                Integer existing = replies.get(id);
                replies.put(id, existing == null ? 1 : existing + 1);
            }

            // Everything should clump to one node under this transaction
            Assert.assertEquals(1, replies.size());
            Assert.assertEquals(20, replies.values().iterator().next().intValue());
            ids.add(id);
            transaction.commit();
        }

        // After 20 tries, we should have hit ever server but server2, which is missing the bean
        Assert.assertEquals(Stream.of("server1", "server3", "server4").collect(Collectors.toSet()), ids);
    }
    private void verifyNonTxBehavior(boolean stateful, boolean sticky) throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();

        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", "remote://localhost:6999, remote://localhost:7999, remote://localhost:8999, remote://localhost:9999");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        HashSet<String> ids = new HashSet<>();

        for (int attempts = 0; attempts < 40; attempts++) {
            txManager.begin();
            HashMap<String, Integer> replies = new HashMap<>();
            String id = null;
            for (int i = 0; i < 30; i++) {

                Echo echo = (Echo) context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + EchoBean.class.getSimpleName() + "!"
                        + Echo.class.getName() + (stateful ? "?stateful" : ""));
                id = echo.whoAreYouNonTX();
                Integer existing = replies.get(id);
                replies.put(id, existing == null ? 1 : existing + 1);
            }

            // Everything should clump to one node under this transaction
            Assert.assertEquals(sticky ? 1 : 3, replies.size());
            if (sticky) {
                Assert.assertEquals(30, replies.values().iterator().next().intValue());
            }
            ids.add(id);
            txManager.commit();
        }

        // After 20 tries, we should have hit ever server but server2, which is missing the bean
        Assert.assertEquals(Stream.of("server1", "server3", "server4").collect(Collectors.toSet()), ids);
    }


    @Test
    public void testTransactionPreference() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.provider.url", "remote://localhost:8999");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context2 = new WildFlyRootContext(props);

        props = new FastHashtable<>();
        props.put("java.naming.provider.url", "remote://localhost:6999, remote://localhost:7999, remote://localhost:8999, remote://localhost:9999");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context1 = new WildFlyRootContext(props);

        HashSet<String> id1s = new HashSet<>();
        HashSet<String> id2s = new HashSet<>();
        for (int attempts = 0; attempts < 80; attempts++) {
            txManager.begin();
            HashMap<String, Integer> replies = new HashMap<>();
            String id1 = null;
            for (int i = 0; i < 20; i++) {

                Echo echo = (Echo) context1.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + EchoBean.class.getSimpleName() + "!" + Echo.class.getName());
                id1 = echo.whoAreYou();
                Integer existing = replies.get(id1);
                replies.put(id1, existing == null ? 1 : existing + 1);
            }

            // Everything should clump to one node under this transaction
            Assert.assertEquals(1, replies.size());
            Assert.assertEquals(20, replies.values().iterator().next().intValue());
            id1s.add(id1);

            replies.clear();
            String id2 = null;
            for (int i = 0; i < 20; i++) {

                Echo echo = (Echo) context1.lookup("ejb:" + OTHER_APP + "/" + MODULE_NAME + "/" + EchoBean.class.getSimpleName() + "!" + Echo.class.getName());
                id2 = echo.whoAreYou();
                Integer existing = replies.get(id1);
                replies.put(id1, existing == null ? 1 : existing + 1);
            }

            // Everything should clump to one node under this transaction
            Assert.assertEquals(1, replies.size());
            Assert.assertEquals(20, replies.values().iterator().next().intValue());

            System.out.println(id1 + ":" + id2);
            if (id1.equals("server1")) {
                Assert.assertTrue(Stream.of("server2", "server3", "server4").collect(Collectors.toSet()).contains(id2));
            } else {
                Assert.assertEquals(id1, id2);
            }


            id2s.add(id2);
            txManager.commit();
        }

        // After 80 tries, we should have hit every server for each app
        Assert.assertEquals(Stream.of("server1", "server3", "server4").collect(Collectors.toSet()), id1s);
        Assert.assertEquals(Stream.of("server2", "server3", "server4").collect(Collectors.toSet()), id2s);
    }

    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {
        server1.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        server2.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        server3.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        server4.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        server1.unregister(OTHER_APP, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        server2.unregister(OTHER_APP, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        server3.unregister(OTHER_APP, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        server4.unregister(OTHER_APP, MODULE_NAME, DISTINCT_NAME, Echo.class.getName());
        logger.info("Unregistered module ...");

        if (serverStarted) {
            try {
                this.server1.stop();
                this.server2.stop();
                this.server3.stop();
                this.server4.stop();
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            }
        }
        logger.info("Stopped server ...");
    }

    /**
     * Do any general tear down here.
     */
    @AfterClass
    public static void afterClass() {
    }

}
