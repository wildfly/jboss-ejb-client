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
package org.jboss.ejb.client.test.common;

import org.jboss.ejb.client.EJBModuleIdentifier;
import org.jboss.ejb.protocol.remote.RemoteEJBService;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.ClusterTopologyListener.ClusterInfo;
import org.jboss.ejb.server.ClusterTopologyListener.ClusterRemovalInfo;
import org.jboss.ejb.server.ClusterTopologyListener.MappingInfo;
import org.jboss.ejb.server.ClusterTopologyListener.NodeInfo;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointBuilder;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.ServiceRegistrationException;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.sasl.util.SaslFactories;
import org.wildfly.transaction.client.LocalTransactionContext;
import org.wildfly.transaction.client.provider.remoting.RemotingTransactionService;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class DummyServer implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(DummyServer.class);
    /*
     * Reject unmarshalling an instance of IAE, as a kind of 'blacklist'.
     * In normal tests this type would never be sent, which is analogous to
     * how blacklisted classes are normally not sent. And then we can
     * deliberately send an IAE in tests to confirm it is rejected.
     */
    private static final Function<String, Boolean> DEFAULT_CLASS_FILTER = cName -> !cName.equals(IllegalArgumentException.class.getName());

    private Endpoint endpoint;
    private final int port;
    private final String host;
    private final String endpointName;
    private final boolean startTxServer;


    private Registration registration;
    private AcceptingChannel<org.xnio.StreamConnection> server;
    private EJBDeploymentRepository deploymentRepository = new EJBDeploymentRepository();
    private EJBClusterRegistry clusterRegistry = new EJBClusterRegistry();

    final Set<Channel> currentConnections = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public DummyServer(final String host, final int port) {
        this(host, port, "default-dummy-server-endpoint");
    }

    public DummyServer(final String host, final int port, final String endpointName) {
        this(host, port, endpointName, false);
    }

    public DummyServer(final String host, final int port, final String endpointName, boolean startTxService)  {
        this.host = host;
        this.port = port;
        this.endpointName = endpointName;
        this.startTxServer = startTxService;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void start() throws Exception {
        logger.info("Starting " + this);

        // create a Remoting endpoint
        final OptionMap options = OptionMap.EMPTY;
        EndpointBuilder endpointBuilder = Endpoint.builder();
        endpointBuilder.setEndpointName(this.endpointName);
        endpointBuilder.buildXnioWorker(Xnio.getInstance()).populateFromOptions(options);
        endpoint = endpointBuilder.build();


        if (startTxServer) {
            final RemotingTransactionService remotingTransactionService = RemotingTransactionService.builder().setEndpoint(endpoint)
                    .setTransactionContext(LocalTransactionContext.getCurrent()).build();

            remotingTransactionService.register();
        }


        // add a connection provider factory for the URI scheme "remote"
        // endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        final NetworkServerProvider serverProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);

        // set up a security realm called default with a user called test
        final SimpleMapBackedSecurityRealm realm = new SimpleMapBackedSecurityRealm();
        realm.setPasswordMap("test", ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, "test".toCharArray()));

        // set up a security domain which has realm "default"
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        domainBuilder.addRealm("default", realm).build();                                  // add the security realm called "default" to the security domain
        domainBuilder.setDefaultRealmName("default");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        SecurityDomain testDomain = domainBuilder.build();

        // set up a SaslAuthenticationFactory (i.e. a SaslServerFactory)
        SaslAuthenticationFactory saslAuthenticationFactory = SaslAuthenticationFactory.builder()
                .setSecurityDomain(testDomain)
                .setMechanismConfigurationSelector(mechanismInformation -> {
                    switch (mechanismInformation.getMechanismName()) {
                        case "ANONYMOUS":
                        case "PLAIN": {
                            return MechanismConfiguration.EMPTY;
                        }
                        default: return null;
                    }
                })
                .setFactory(SaslFactories.getElytronSaslServerFactory())
                .build();

        final OptionMap serverOptions = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("ANONYMOUS"), Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        final SocketAddress bindAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        this.server = serverProvider.createServer(bindAddress, serverOptions, saslAuthenticationFactory, null);

        // set up an association to handle invocations, session creations and module/toopology listensrs
        // the association makes use of a module deployment repository as well a sa  cluster registry
        Association dummyAssociation = new DummyAssociationImpl(this, deploymentRepository, clusterRegistry);

        // set up a remoting transaction service
        RemotingTransactionService.Builder txnServiceBuilder = RemotingTransactionService.builder();
        txnServiceBuilder.setEndpoint(endpoint);
        txnServiceBuilder.setTransactionContext(LocalTransactionContext.getCurrent());
        RemotingTransactionService transactionService = txnServiceBuilder.build();

        // setup remote EJB service
        RemoteEJBService remoteEJBService = RemoteEJBService.create(dummyAssociation,transactionService, DEFAULT_CLASS_FILTER);
        remoteEJBService.serverUp();

        // Register an EJB channel open listener
        OpenListener channelOpenListener = remoteEJBService.getOpenListener();
        try {
            registration = endpoint.registerService("jboss.ejb", new OpenListener() {
                @Override
                public void channelOpened(Channel channel) {
                    currentConnections.add(channel);
                    channelOpenListener.channelOpened(channel);
                }

                @Override
                public void registrationTerminated() {

                }
            }, OptionMap.EMPTY);
        } catch (ServiceRegistrationException e) {
            throw new Exception(e);
        }
    }

    public void stop() throws Exception {
        if (server !=  null) {
            this.server.close();
            this.server = null;
            IoUtils.safeClose(this.endpoint);
        }
    }

    // module deployment interface
    public void register(final String appName, final String moduleName, final String distinctName, final String beanName, final Object instance) {
        deploymentRepository.register(appName, moduleName, distinctName, beanName, instance);
    }

    public void unregister(final String appName, final String moduleName, final String distinctName, final String beanName) {
        deploymentRepository.unregister(appName, moduleName, distinctName, beanName);
    }

    // clustering registry interface
    public void addCluster(ClusterInfo clusterInfo) {
        clusterRegistry.addCluster(clusterInfo);
    }

    public void removeCluster(String clusterName) {
        clusterRegistry.removeCluster(clusterName);
    }

    public void addClusterNodes(ClusterInfo newClusterInfo) {
        clusterRegistry.addClusterNodes(newClusterInfo);
    }

    public void removeClusterNodes(ClusterRemovalInfo clusterRemovalInfo) {
        clusterRegistry.removeClusterNodes(clusterRemovalInfo);
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    public void hardKill() throws IOException {
        for (Channel i : currentConnections) {
            try {
                i.close();
            } catch (IOException e) {
                logger.error("failed to close", e);
            }
        }
        server.close();
        server = null;
        endpoint.close();
    }

    public interface EJBDeploymentRepositoryListener {
        void moduleAvailable(List<EJBModuleIdentifier> modules);
        void moduleUnavailable(List<EJBModuleIdentifier> modules);
    }

    /**
     * Allows keeping track of which modules are deployed on this server.
     */
    public class EJBDeploymentRepository {
        Map<EJBModuleIdentifier, Map<String,Object>> registeredEJBs = new HashMap<>();
        List<EJBDeploymentRepositoryListener> listeners = new ArrayList<>();

        void register(final String appName, final String moduleName, final String distinctName, final String beanName, final Object instance) {
            final EJBModuleIdentifier moduleID = new EJBModuleIdentifier(appName, moduleName, distinctName);
            Map<String, Object> ejbs = this.registeredEJBs.get(moduleID);
            if (ejbs == null) {
                ejbs = new HashMap<String, Object>();
                this.registeredEJBs.put(moduleID, ejbs);
            }
            ejbs.put(beanName, instance);

            // notify listeners
            List<EJBModuleIdentifier> availableModules = new ArrayList<>();
            availableModules.add(moduleID);
            for (EJBDeploymentRepositoryListener listener: listeners) {
                listener.moduleAvailable(availableModules);
            }
        }

        void unregister(final String appName, final String moduleName, final String distinctName, final String beanName) {
            final EJBModuleIdentifier moduleID = new EJBModuleIdentifier(appName, moduleName, distinctName);
            Map<String, Object> ejbs = this.registeredEJBs.get(moduleID);
            if (ejbs != null) {
                ejbs.remove(beanName);
            }
            // notify listeners
            List<EJBModuleIdentifier> unavailableModules = new ArrayList<>();
            unavailableModules.add(moduleID);
            for (EJBDeploymentRepositoryListener listener: listeners) {
                listener.moduleUnavailable(unavailableModules);
            }
        }

        Object findEJB(EJBModuleIdentifier module, String beanName) {
            final Map<String, Object> ejbs = this.registeredEJBs.getOrDefault(module, Collections.emptyMap());
            final Object instance = ejbs.get(beanName);
            if (instance == null) {
                // any exception will be handled by the caller on seeing null
                return null;
            }
            return instance ;
        }

        void addListener(EJBDeploymentRepositoryListener listener) {
            listeners.add(listener);

            // EJBClientChannel depends on an initial module availability report to be sent out
            List<EJBModuleIdentifier> availableModules = new ArrayList<>();
            availableModules.addAll(this.registeredEJBs.keySet());

            listener.moduleAvailable(availableModules);
        }

        void removeListener(EJBDeploymentRepositoryListener listener) {
            listeners.remove(listener);
        }

        void dumpRegistry() {
            System.out.println("\n");
            System.out.println("Dumping registered EJBs");
            System.out.println(registeredEJBs.toString());
            System.out.println("\n");
        }
    }


    public interface EJBClusterRegistryListener {
        void clusterTopology(List<ClusterInfo> clusterList);
        void clusterRemoval(List<String> clusterNames);
        void clusterNewNodesAdded(ClusterInfo cluster);
        void clusterNodesRemoved(List<ClusterRemovalInfo> clusterRemovals);
    }

    /**
     * Allows keeping track of which clusters this server has joined and their membership.
     *
     * To keep things simple, this is a direct mapping to the server-side cluster information used; namely,
     * the classes ClusterInfo, NodeInfo and MappingInfo used by .
     *
     * The server does not need to store the current state of the clusters.
     */
    public class EJBClusterRegistry {
        Map<String, ClusterInfo> joinedClusters = new HashMap<String, ClusterInfo>();
        List<EJBClusterRegistryListener> listeners = new ArrayList<EJBClusterRegistryListener>();

        EJBClusterRegistry() {
        }

        boolean isClusterMember(String clusterName) {
            return joinedClusters.keySet().contains(clusterName);
        }

        void addCluster(ClusterInfo cluster) {
            // add the cluster if they are not already present
            if (joinedClusters.keySet().contains(cluster.getClusterName())) {
                logger.warn("Cluster " + cluster.getClusterName() + " already exists; skipping add operation");
                return;
            }
            ClusterInfo absent = joinedClusters.put(cluster.getClusterName(), cluster);

            // notify listeners
            List<ClusterInfo> additions = new ArrayList<ClusterInfo>();
            additions.add(cluster);
            for (EJBClusterRegistryListener listener: listeners) {
                listener.clusterTopology(additions);
            }
        }

        void removeCluster(String clusterName) {
            // update the registry
            ClusterInfo removed = joinedClusters.remove(clusterName);
            if (removed == null) {
                logger.warn("Could not remove non-existent cluster");
            }
            // notify listeners
            List<String> removals = new ArrayList<String>();
            removals.add(clusterName);
            for (EJBClusterRegistryListener listener: listeners) {
                listener.clusterRemoval(removals);
            }
        }

        void addClusterNodes(ClusterInfo newClusterInfo) {
            // update the registry
            ClusterInfo oldClusterInfo = joinedClusters.get(newClusterInfo.getClusterName());
            if (oldClusterInfo == null) {
                joinedClusters.put(newClusterInfo.getClusterName(), newClusterInfo);
                logger.warn("new nodes cannot be added to existing cluster; creating new cluster entry");
            } else {
                List<NodeInfo> additions = new ArrayList<NodeInfo>();
                for (NodeInfo newNodeInfo : newClusterInfo.getNodeInfoList()) {
                    for (NodeInfo oldNodeInfo : oldClusterInfo.getNodeInfoList()) {
                        if (oldNodeInfo.getNodeName().equals(newNodeInfo.getNodeName())) {
                            additions.add(newNodeInfo);
                            logger.info("Added node " + newNodeInfo.getNodeName() + " to cluster " + oldClusterInfo.getClusterName());
                        }
                    }
                }
                oldClusterInfo.getNodeInfoList().addAll(additions);
            }
            // notify listeners
            for (EJBClusterRegistryListener listener: listeners) {
                listener.clusterNewNodesAdded(newClusterInfo);
            }
        }

        /**
         * Update the registry to remove all nodes from the named cluster
         *
         * @param clusterRemovalInfo nodes to remove
         */
        void removeClusterNodes(ClusterRemovalInfo clusterRemovalInfo) {
            // check to see if the cluster is in the registry
            if (!joinedClusters.keySet().contains(clusterRemovalInfo.getClusterName())) {
                logger.warn("Cluster " + clusterRemovalInfo.getClusterName() + " not present in registry; skipping removal");
                return;
            } else {
                // its in the registry, now remove any listed nodes
                ClusterInfo oldClusterInfo = joinedClusters.get(clusterRemovalInfo.getClusterName());
                List<NodeInfo> removals = new ArrayList<NodeInfo>();
                for (String nodeToRemove : clusterRemovalInfo.getNodeNames()) {
                    for (NodeInfo oldNodeInfo : oldClusterInfo.getNodeInfoList()) {
                        if (oldNodeInfo.getNodeName().equals(nodeToRemove)) {
                            logger.warn("Removing node " + nodeToRemove + " from cluster " + oldClusterInfo.getClusterName());
                            removals.add(oldNodeInfo);
                        }
                    }
                }
                // just do the update in place
                oldClusterInfo.getNodeInfoList().removeAll(removals);
            }
            // notify listeners
            List<ClusterRemovalInfo> removals = new ArrayList<ClusterRemovalInfo>();
            removals.add(clusterRemovalInfo);
            for (EJBClusterRegistryListener listener: listeners) {
                listener.clusterNodesRemoved(removals);
            }
        }

        void addListener(EJBClusterRegistryListener listener) {
            listeners.add(listener);

            // EJBClientChannel depends on an initial module availability report to be sent out
            List<ClusterInfo> availableClusters = new ArrayList<ClusterInfo>();
            availableClusters.addAll(this.joinedClusters.values());

            listener.clusterTopology(availableClusters);
        }

        void removeListener(EJBClusterRegistryListener listener) {
            listeners.remove(listener);
        }
    }

    public static final ClusterInfo getClusterInfo(String name, NodeInfo... nodes) {
        List<NodeInfo> nodeList = new ArrayList<NodeInfo>();
        for (NodeInfo node : nodes) {
            nodeList.add(node);
        }
        return new ClusterInfo(name, nodeList);
    }

    public static NodeInfo getNodeInfo(String name, String destHost, int destPort, String sourceIp, int bytes) {
        InetAddress srcIpAddress = null;
        try {
            srcIpAddress = InetAddress.getByName(sourceIp);
        }
        catch(UnknownHostException e) {
        }
        List<MappingInfo> mappingList = new ArrayList<MappingInfo>();
        mappingList.add(new MappingInfo(destHost, destPort, srcIpAddress, bytes));
        return new NodeInfo(name, mappingList);
    }

    public static final ClusterRemovalInfo getClusterRemovalInfo(String name, NodeInfo... nodes) {
        List<String> nodeList = new ArrayList<String>();
        for (NodeInfo node : nodes) {
            nodeList.add(node.getNodeName());
        }
        return new ClusterRemovalInfo(name, nodeList);
    }


}
