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
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.internal.jbossatx.jta.jca.XATerminator;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import org.jboss.ejb.client.test.common.Echo;
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
 * Tests transaction stickiness for transaction-scoped invocations.
 * <p>
 * NOTE: Transaction stickiness requirements vary based on three factors:
 * - whether the bean being invoked upon is stateless or stateful
 * - whether the client making the invocation is local (using a LocalTransaction) or remote (using a RemoteTransaction)
 * - any ClientTransaction annotation describing the client-side transaction policy on the client-side interface
 * (the default policy is ClientSideTransactionPolicy.SUPPORTS which supports transaction context propagation)
 *
 * @author Jason T. Greene
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class TransactionTestCase extends AbstractEJBClientTestCase {
    private static final Logger logger = Logger.getLogger(TransactionTestCase.class);
    private static ContextTransactionManager txManager;
    private static ContextTransactionSynchronizationRegistry txSyncRegistry;

    /**
     * Setup a local JTA transaction environment for use with any tests in this class.
     * The key elements of a local transaction context for the EJB client are:
     * - JBossLocalTransactionProvider, which represents an underlying Narayana TransactionManager instance and
     * XATerminator instance
     * - LocalTransactionContext contextual, which makes the LocalTransactionProvider available to the EJB client
     * application itself
     *
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
        final XATerminator xat = new XATerminator();
        final JBossLocalTransactionProvider.Builder builder = JBossLocalTransactionProvider.builder();
        builder.setExtendedJBossXATerminator(xat);
        builder.setTransactionManager(narayanaTm);
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
     * Before each test, start four mock server instances and deploy specific combinations of stateful and stateless
     * beans used in the tests. The final arrangement of nodes and beans is as follows:
     *
     * node1: SFSB, SLSB
     * node2: OSFSB, OSLSB
     * node3: SFSB, SLSB, OSFSB, OSLSB
     * node4: SFSB, SLSB, OSFSB, OSLSB
     *
     * where OSFSL/OSLSB refers to the OtherStateful and OtherStateless beans.
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

    /**
     * Validates transaction stickiness in the case of invocations on stateless beans by a local client (i.e. a client
     * application deployed on a server).
     *
     * @throws Exception
     */
    @Test
    public void testTransactionStickiness() throws Exception {
        verifyStickiness(false, false);
    }

    /**
     * Validates transaction stickiness in the case of invocations on stateless beans by a remote client (i.e. a client
     * application not deployed on a server).
     *
     * @throws Exception
     */
    @Test
    public void testRemoteTransactionStickiness() throws Exception {
        verifyStickiness(false, true);
    }

    /**
     * Validates transaction stickiness in the case of invocations on stateful beans by a remote client (i.e. a client
     * application not deployed on a server).
     *
     * @throws Exception
     */
    @Test
    public void testRemoteStatefulTransactionStickiness() throws Exception {
        verifyStickiness(true, true);
    }

    /**
     * Validates transaction stickiness in the case of invocations on stateful beans by a local client (i.e. a client
     * application deployed on a server).
     *
     * @throws Exception
     */
    @Test
    public void testStatefulTransactionStickiness() throws Exception {
        verifyStickiness(true, false);
    }

    /**
     * Validates transaction stickiness in the case of invocations on stateless beans by a local client (i.e. a client
     * application deployed on a server) where transaction contexts are not propagated.
     * <p>
     * i.e. the method echoNonTx is annotated with @ClientTransaction(ClientTransactionPolicy.NOT_SUPPORTED) to
     * supress transaction context propagation
     * <p>
     * Expected behavior is that such stateless invocations are not sticky to a particular node.
     *
     * @throws Exception
     */
    @Test
    public void testNonPropagatingInvocation() throws Exception {
        verifyNonTxBehavior(false, false);
    }

    /**
     * Validates transaction stickiness in the case of invocations on stateful beans by a local client (i.e. a client
     * application deployed on a server) where transaction contexts are not propagated.
     * <p>
     * i.e. the method echoNonTx is annotated with @ClientTransaction(ClientTransactionPolicy.NOT_SUPPORTED) to
     * supress transaction context propagation
     * <p>
     * Expected behavior is that such stateful invocations are sticky to a particular node due to the session
     * stickiness of the SFSB.
     *
     * @throws Exception
     */
    @Test
    public void testStatefulNonPropagatingInvocation() throws Exception {
        // Session open is sticky, resulting in a weak affinity and therefore stickiness even with
        // non-propagating method calls.
        verifyNonTxBehavior(true, true);
    }

    /**
     * A method which returns a UserTransaction, configuted for use with a remote application client or
     * from a deployment on a server.
     *
     * @param remote if true, returns a RemoteUserTransaction; otherwise, returns a LocalUserTransaction
     * @return the UserTransaction instance
     */
    private UserTransaction getTransaction(boolean remote) {
        if (remote) {
            return RemoteTransactionContext.getInstance().getUserTransaction();
        } else {
            return LocalUserTransaction.getInstance();
        }
    }

    /**
     * Verifies the stickiness properties of invocations scoped by transactions, which constrain which nodes
     * may be targetted by such invocations.
     *
     * @param stateful if true, assumes beans under test are stateful; assumes stateless otherwise
     * @param remote   if true, assumes client application is remote; assumes application is local otherwise
     * @throws Exception
     */
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

            try {
                for (int i = 0; i < 20; i++) {
                    // get the correct bean name and interface depending on case
                    String beanInterface = Echo.class.getName();
                    String beanName = stateful ? StatefulEchoBean.class.getSimpleName() : StatelessEchoBean.class.getSimpleName();

                    Echo echo = (Echo) context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + beanName + "!" + beanInterface + (stateful ? "?stateful" : ""));
                    id = echo.echo("someMsg").getNode();
                    Integer existing = replies.get(id);
                    replies.put(id, existing == null ? 1 : existing + 1);
                }
            } catch (Exception e) {
                try {
                    transaction.rollback();
                } catch (Exception exceptionFromRollback) {
                    // ignore
                }
                throw e;
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

    /**
     * Verifies the stickiness properties of invocations scoped by transactions, which constrain which nodes
     * may be targetted by such invocations.
     *
     *  i.e. the method echoNonTx is annotated with @ClientTransaction(ClientTransactionPolicy.NOT_SUPPORTED) to
     *  supress transaction context propagation
     *
     * @param stateful if true, assumes beans under test are stateful; assumes stateless otherwise
     * @param sticky if true, indicates that session stickiness will be present for invocations
     * @throws Exception
     */
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

            try {
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
            } catch (Exception e) {
                try {
                    txManager.rollback();
                } catch (Exception exceptionFromRollback) {
                    // ignore
                }
                throw e;
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


    /**
     *
     * @throws Exception
     */
    @Test
    public void testTransactionPreference() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();

        // this context is not used ..
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

            // invoke on APP_NAME deployments on node1, node3 and node4
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

            // invoke on OTHER_APP deployments on node2, node3 and node4
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

            // 1. if invocations on APP_NAME land on node1, invocations on OTHER_APP must land on another node
            // due to the way the deployments are distributed on servers
            // 2. otherwise, invocations on both apps should land on the same nodes, due to ...???
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
     * After each test, undeploy the beans and stop the mock servers.
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
}
