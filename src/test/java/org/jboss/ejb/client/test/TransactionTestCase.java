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

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.internal.jbossatx.jta.jca.XATerminator;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.StatefulEchoBean;
import org.jboss.ejb.client.test.common.StatelessEchoBean;
import org.jboss.logging.Logger;
import org.jboss.tm.XAResourceRecovery;
import org.jboss.tm.XAResourceRecoveryRegistry;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
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
 * Tests transaction stickiness
 *
 * @author Jason T. Greene
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class TransactionTestCase extends AbstractEJBClientTestCase {
    private static final Logger logger = Logger.getLogger(TransactionTestCase.class);
    private static ContextTransactionManager txManager;
    private static ContextTransactionSynchronizationRegistry txSyncRegistry;

    /**
     * Do any general setup here
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, null)
                .setObjectStoreDir("target/tx-object-store");
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore")
                .setObjectStoreDir("target/tx-object-store");
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore")
                .setObjectStoreDir("target/tx-object-store");


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
        builder.setXAResourceRecoveryRegistry(new XAResourceRecoveryRegistry() {
            @Override
            public void addXAResourceRecovery(XAResourceRecovery xaResourceRecovery) {
            }

            @Override
            public void removeXAResourceRecovery(XAResourceRecovery xaResourceRecovery) {
            }
        });
        builder.setXARecoveryLogDirRelativeToPath(new File("target/tx-object-store").toPath());
        builder.build();
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
        for (int i = 0; i < 4; i++) {
            startServer(i, true);
        }

        deployStateless(0);
        deployStateful(0);
        // don't deploy on server 2 (index 1)
        deployStateless(2);
        deployStateful(2);
        deployStateless(3);
        deployStateful(3);

        // don't deploy on server 1 (index 0)
        deployOtherStateful(1);
        deployOtherStateless(1);
        deployOtherStateful(2);
        deployOtherStateless(2);
        deployOtherStateful(3);
        deployOtherStateless(3);
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
        // stickiness even with non-propagating method calls.
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
        props.put("java.naming.provider.url", "remote://localhost:6999, remote://localhost:7099, remote://localhost:7199, remote://localhost:7299");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        HashSet<String> ids = new HashSet<>();

        for (int attempts = 0; attempts < 40; attempts++) {
            System.out.println("\n *** Starting transaction # " + attempts + " ***\n");
            transaction.begin();
            HashMap<String, Integer> replies = new HashMap<>();
            String id = null;
            for (int i = 0; i < 20; i++) {
                // get the correct bean name and interface depending on case
                String beanInterface = Echo.class.getName();
                String beanName = stateful ? StatefulEchoBean.class.getSimpleName() : StatelessEchoBean.class.getSimpleName();

                Echo echo = (Echo) context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + beanName + "!" + beanInterface + (stateful ? "?stateful" : ""));
                id = echo.echo("someMsg").getNode();
                Integer existing = replies.get(id);
                replies.put(id, existing == null ? 1 : existing + 1);
            }

            // Everything should clump to one node under this transaction
            Assert.assertEquals(1, replies.size());
            Assert.assertEquals(20, replies.values().iterator().next().intValue());
            ids.add(id);
            System.out.println("\n*** Committing transaction # " + attempts + " ***\n");
            transaction.commit();
        }

        // After 20 tries, we should have hit ever server but server2, which is missing the bean
        Assert.assertEquals(Stream.of("node1", "node3", "node4").collect(Collectors.toSet()), ids);
    }

    private void verifyNonTxBehavior(boolean stateful, boolean sticky) throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();

        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", "remote://localhost:6999, remote://localhost:7099, remote://localhost:7199, remote://localhost:7299");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        HashSet<String> ids = new HashSet<>();

        for (int attempts = 0; attempts < 40; attempts++) {
            txManager.begin();
            HashMap<String, Integer> replies = new HashMap<>();
            String id = null;
            for (int i = 0; i < 30; i++) {
                // get the correct bean name and interface depending on case
                String beanInterface = Echo.class.getName();
                String beanName = stateful ? StatefulEchoBean.class.getSimpleName() : StatelessEchoBean.class.getSimpleName();

                Echo echo = (Echo) context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + beanName + "!" + beanInterface + (stateful ? "?stateful" : ""));
                // invoke the non-transactional version of echo
                id = echo.echoNonTx("someMsg").getNode();
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
        Assert.assertEquals(Stream.of("node1", "node3", "node4").collect(Collectors.toSet()), ids);
    }


    @Test
    public void testTransactionPreference() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.provider.url", "remote://localhost:7199");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context2 = new WildFlyRootContext(props);

        props = new FastHashtable<>();
        props.put("java.naming.provider.url", "remote://localhost:6999, remote://localhost:7099, remote://localhost:7199, remote://localhost:7299");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context1 = new WildFlyRootContext(props);

        HashSet<String> id1s = new HashSet<>();
        HashSet<String> id2s = new HashSet<>();
        for (int attempts = 0; attempts < 80; attempts++) {
            txManager.begin();
            HashMap<String, Integer> replies = new HashMap<>();
            String id1 = null;
            for (int i = 0; i < 20; i++) {

                Echo echo = (Echo) context1.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + StatefulEchoBean.class.getSimpleName() + "!" + Echo.class.getName());
                id1 = echo.echo("someMsg").getNode();
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

                Echo echo = (Echo) context1.lookup("ejb:" + OTHER_APP + "/" + MODULE_NAME + "/" + StatefulEchoBean.class.getSimpleName() + "!" + Echo.class.getName());
                id2 = echo.echo("someMsg").getNode();
                Integer existing = replies.get(id1);
                replies.put(id1, existing == null ? 1 : existing + 1);
            }

            // Everything should clump to one node under this transaction
            Assert.assertEquals(1, replies.size());
            Assert.assertEquals(20, replies.values().iterator().next().intValue());

            System.out.println(id1 + ":" + id2);
            if (id1.equals("node1")) {
                Assert.assertTrue(Stream.of("node2", "node3", "node4").collect(Collectors.toSet()).contains(id2));
            } else {
                Assert.assertEquals(id1, id2);
            }

            id2s.add(id2);
            txManager.commit();
        }

        // After 80 tries, we should have hit every server for each app
        Assert.assertEquals(Stream.of("node1", "node3", "node4").collect(Collectors.toSet()), id1s);
        Assert.assertEquals(Stream.of("node2", "node3", "node4").collect(Collectors.toSet()), id2s);
    }

    /**
     * Do any test-specific tear down here.
     */
    @After
    public void afterTest() {

        undeployStateless(0);
        undeployStateful(0);
        undeployStateless(2);
        undeployStateful(2);
        undeployStateless(3);
        undeployStateful(3);

        undeployOtherStateful(1);
        undeployOtherStateless(1);
        undeployOtherStateful(2);
        undeployOtherStateless(2);
        undeployOtherStateful(3);
        undeployOtherStateless(3);

        for (int i = 0; i < 4; i++) {
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
