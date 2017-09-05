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

import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.EJBModuleIdentifier;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.StatefulEchoBean;
import org.jboss.ejb.client.test.common.StatelessEchoBean;
import org.jboss.ejb.server.ClusterTopologyListener;
import org.jboss.logging.Logger;

/**
 * A base class for EJB client test cases.
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class AbstractEJBClientTestCase {

    private static final Logger logger = Logger.getLogger(AbstractEJBClientTestCase.class);

    // server names; these are logical names (c.f. jboss.node.name) and not DNS resolvable hostnames
    public static final String SERVER1_NAME = "node1";
    public static final String SERVER2_NAME = "node2";
    public static final String SERVER3_NAME = "node3";
    public static final String SERVER4_NAME = "node4";

    public static String[] serverNames = {SERVER1_NAME, SERVER2_NAME, SERVER3_NAME, SERVER4_NAME};
    public static final int NUM_SERVERS = 4;
    public DummyServer[] servers = new DummyServer[NUM_SERVERS];
    public static boolean[] serversStarted = new boolean[NUM_SERVERS] ;

    // module
    public static final String APP_NAME = "my-foo-app";
    public static final String OTHER_APP = "my-other-app";
    public static final String MODULE_NAME = "my-bar-module";
    public static final String DISTINCT_NAME = "";

    // cluster
    // note: logical node names and server names should match!
    public static final String CLUSTER_NAME = "ejb";
    public static final String NODE1_NAME = "node1";
    public static final String NODE2_NAME = "node2";
    public static final String NODE3_NAME = "node3";
    public static final String NODE4_NAME = "node4";

    public static final ClusterTopologyListener.NodeInfo NODE1 = DummyServer.getNodeInfo(NODE1_NAME, "localhost",6999,"0.0.0.0",0);
    public static final ClusterTopologyListener.NodeInfo NODE2 = DummyServer.getNodeInfo(NODE2_NAME, "localhost",7099,"0.0.0.0",0);
    public static final ClusterTopologyListener.NodeInfo NODE3 = DummyServer.getNodeInfo(NODE3_NAME, "localhost",7199,"0.0.0.0",0);
    public static final ClusterTopologyListener.NodeInfo NODE4 = DummyServer.getNodeInfo(NODE4_NAME, "localhost",7299,"0.0.0.0",0);
    public static final ClusterTopologyListener.ClusterInfo CLUSTER = DummyServer.getClusterInfo(CLUSTER_NAME, NODE1, NODE2);

    // convenience
    public final EJBModuleIdentifier MODULE_IDENTIFIER = new EJBModuleIdentifier(APP_NAME, MODULE_NAME, DISTINCT_NAME);
    public final EJBModuleIdentifier OTHER_MODULE_IDENTIFIER = new EJBModuleIdentifier(OTHER_APP, MODULE_NAME, DISTINCT_NAME);
    public final EJBIdentifier STATELESS_IDENTIFIER = new EJBIdentifier(MODULE_IDENTIFIER,StatelessEchoBean.class.getSimpleName());
    public final EJBIdentifier STATEFUL_IDENTIFIER = new EJBIdentifier(MODULE_IDENTIFIER,StatefulEchoBean.class.getSimpleName());


    /* start a server with hostname = localhost" and Remoting Transaction service enabled*/
    public void startServer(int index, int port) throws Exception {
        startServer(index, port, false);
    }

    /* start a server with hostname = localhost" */
    public void startServer(int index, int port, boolean startTxService) throws Exception {
        startServer(index, "localhost", port, startTxService);
    }

    public void startServer(int index, String hostname, int port, boolean startTxService) throws Exception {
        servers[index] = new DummyServer(hostname, port, serverNames[index], startTxService);
        servers[index].start();
        serversStarted[index] = true;
        logger.info("Started server " + serverNames[index] + (startTxService ? " with transaction service" : ""));
    }

    public void stopServer(int index) {
        if (isServerStarted(index)) {
            try {
                this.servers[index].stop();
            } catch (Throwable t) {
                logger.info("Could not stop server " + serverNames[index], t);
            } finally {
                serversStarted[index] = false;
            }
        }
        logger.info("Stopped server " + serverNames[index]);
    }

    public void crashServer(int server) {
        if (serversStarted[server]) {
            try {
                this.servers[server].stop();
                logger.info("Crashed server " + serverNames[server]);
            } catch (Throwable t) {
                logger.info("Could not crash server", t);
            } finally {
                serversStarted[server] = false;
            }
        }
    }

    public static boolean isServerStarted(int index) {
        return serversStarted[index];
    }

    /*
     * bean deployment helpers for generic beans StatefulEchoBean, StatelessEchoBean in module "my-foo-app"/"my-bar-module"
     */

    public void deployStateless(int index) {
        servers[index].register(APP_NAME, MODULE_NAME, DISTINCT_NAME, StatelessEchoBean.class.getSimpleName(), new StatelessEchoBean(serverNames[index]));
        logger.info("Registered SLSB module " + MODULE_IDENTIFIER.toString()  + " on server " + serverNames[index]);
    }

    public void undeployStateless(int index) {
        servers[index].unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, StatelessEchoBean.class.getSimpleName());
        logger.info("Unregistered SLSB module " + MODULE_IDENTIFIER.toString()  + " on server " + serverNames[index]);
    }

    public void deployStateful(int index) {
        servers[index].register(APP_NAME, MODULE_NAME, DISTINCT_NAME, StatefulEchoBean.class.getSimpleName(), new StatefulEchoBean(serverNames[index]));
        logger.info("Registered SFSB module " + MODULE_IDENTIFIER.toString()  + " on server " + serverNames[index]);
    }

    public void undeployStateful(int index) {
        servers[index].unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, StatefulEchoBean.class.getSimpleName());
        logger.info("Unregistered SFSB module " + MODULE_IDENTIFIER.toString()  + " on server " + serverNames[index]);
    }


    public void defineCluster(int index, ClusterTopologyListener.ClusterInfo cluster) {
        servers[index].addCluster(cluster);
        logger.info("Added node to cluster " + cluster + ": server " + servers[index]);
    }

    public void addClusterNodes(int index, ClusterTopologyListener.ClusterInfo cluster) {
        servers[index].addClusterNodes(cluster);
        logger.info("Added node(s) to cluster " + cluster + ":" + cluster.getNodeInfoList());
    }

    public void removeClusterNodes(int index, ClusterTopologyListener.ClusterRemovalInfo cluster) {
        servers[index].removeClusterNodes(cluster);
        logger.info("Removed node(s) from cluster " + cluster + ":" + cluster.getNodeNames());
    }

    public void removeCluster(int index, String clusterName) {
        servers[index].removeCluster(clusterName);
        logger.info("Removed cluster " + clusterName + " from node: server " + servers[index]);
    }

    public void deployOtherStateless(int index) {
        servers[index].register(OTHER_APP, MODULE_NAME, DISTINCT_NAME, StatelessEchoBean.class.getSimpleName(), new StatelessEchoBean(serverNames[index]));
        logger.info("Registered other SLSB module " + MODULE_IDENTIFIER.toString()  + " on server " + serverNames[index]);
    }

    public void undeployOtherStateless(int index) {
        servers[index].unregister(OTHER_APP, MODULE_NAME, DISTINCT_NAME, StatelessEchoBean.class.getSimpleName());
        logger.info("Unregistered other SLSB module " + MODULE_IDENTIFIER.toString()  + " on server " + serverNames[index]);
    }

    public void deployOtherStateful(int index) {
        servers[index].register(OTHER_APP, MODULE_NAME, DISTINCT_NAME, StatefulEchoBean.class.getSimpleName(), new StatefulEchoBean(serverNames[index]));
        logger.info("Registered other SFSB module " + OTHER_MODULE_IDENTIFIER.toString()  + " on server " + serverNames[index]);
    }

    public void undeployOtherStateful(int index) {
        servers[index].unregister(OTHER_APP, MODULE_NAME, DISTINCT_NAME, StatefulEchoBean.class.getSimpleName());
        logger.info("Unregistered other SFSB module " + OTHER_MODULE_IDENTIFIER.toString()  + " on server " + serverNames[index]);
    }

    public void deployCustomBean(int index, String app, String module, String distinct, String beanName, Object beanInstance) {
        servers[index].register(app, module, distinct, beanName, beanInstance);
        logger.info("Registered custom bean " + (new EJBModuleIdentifier(app, module, distinct)).toString()  + " on server " + serverNames[index]);
    }

    public void undeployCustomBean(int index, String app, String module, String distinct, String beanName) {
        servers[index].unregister(app, module, distinct, beanName);
        logger.info("Unregistered custom bean " + (new EJBModuleIdentifier(app, module, distinct)).toString()  + " on server " + serverNames[index]);
    }

}
