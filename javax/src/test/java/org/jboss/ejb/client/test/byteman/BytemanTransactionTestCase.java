/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.internal.jbossatx.jta.jca.XATerminator;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.ejb.client.test.AbstractEJBClientTestCase;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.StatelessEchoBean;
import org.jboss.logging.Logger;
import org.jboss.tm.XAResourceRecovery;
import org.jboss.tm.XAResourceRecoveryRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.transaction.client.*;
import org.wildfly.transaction.client.provider.jboss.JBossLocalTransactionProvider;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

@RunWith(BMUnitRunner.class)
@BMScript(dir="target/test-classes")
public class BytemanTransactionTestCase extends AbstractEJBClientTestCase {
    private static final Logger logger = Logger.getLogger(BytemanTransactionTestCase.class);
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
        startServer(0, true);
        deployStateless(0);

    }

    @Test
    public void testCacheCleaning() throws Exception {
        verifyCacheCleaning();
    }

    private void verifyCacheCleaning() throws Exception {
        UserTransaction transaction = RemoteTransactionContext.getInstance().getUserTransaction();
        FastHashtable<String, Object> props = new FastHashtable<>();

        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", "remote://localhost:6999");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        HashSet<String> ids = new HashSet<>();

        HashMap<String, Integer> replies = new HashMap<>();
        String id = null;
        for (int i = 0; i < 20; i++) {
            transaction.begin();
            String beanInterface = Echo.class.getName();
            String beanName = StatelessEchoBean.class.getSimpleName();

            Echo echo = (Echo) context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + beanName + "!" + beanInterface);
            echo.echo("someMsg");
            transaction.commit();
        }



        Assert.assertEquals(0, Integer.parseInt(System.getProperty("peerMapSize")));
    }

    @After
    public void afterTest() {

        undeployStateless(0);
        stopServer(0);
    }

}
