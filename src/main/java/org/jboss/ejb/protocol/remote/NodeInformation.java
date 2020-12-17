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

package org.jboss.ejb.protocol.remote;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBModuleIdentifier;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.wildfly.common.Assert;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.CidrAddressTable;
import org.wildfly.common.net.Inet;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceType;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.spi.DiscoveryResult;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class NodeInformation {
    private final String nodeName;

    private final ConcurrentMap<EJBClientChannel, Set<EJBModuleIdentifier>> modulesByConnection = new ConcurrentHashMap<>(1);
    private final ConcurrentMap<String, ClusterNodeInformation> clustersByName = new ConcurrentHashMap<>(1);
    private final ConcurrentMap<EJBClientChannel, InetSocketAddress> addressesByConnection = new ConcurrentHashMap<>(1);

    private volatile List<ServiceURL> serviceURLCache;

    private volatile boolean invalid;

    NodeInformation(final String nodeName) {
        this.nodeName = nodeName;
    }

    String getNodeName() {
        return nodeName;
    }

    boolean discover(ServiceType serviceType, FilterSpec filterSpec, DiscoveryResult discoveryResult) {
        if (invalid) return false;
        boolean found = false;
        List<ServiceURL> serviceURLCache = getServiceURLCache();
        for (ServiceURL serviceURL : serviceURLCache) {
            if (serviceURL.satisfies(filterSpec) && serviceType.implies(serviceURL)) {
                found = true;
                discoveryResult.addMatch(serviceURL);
            }
        }
        return found;
    }

    ConcurrentMap<String, ClusterNodeInformation> getClustersByName() {
        return clustersByName;
    }

    // this structure mashes together the cluster topo reports from various nodes, and the mod avail report from this node
    static class TempInfo {
        Map<String, CidrAddress> clusters;
        Set<EJBModuleIdentifier> modules;
        URI destination;

        TempInfo(URI destination) {
            this.destination = destination;
        }
    }

    private List<ServiceURL> getServiceURLCache() {
        List<ServiceURL> serviceURLCache = this.serviceURLCache;
        if (serviceURLCache == null) {
            synchronized (this) {
                serviceURLCache = this.serviceURLCache;
                if (serviceURLCache == null) {
                    // the final list to store
                    serviceURLCache = new ArrayList<>();
                    HashMap<URI, TempInfo> map = new HashMap<>();
                    // hostname representations encountered - these can vary
                    Set<String> hostReps = new HashSet<String>();
                    // populate standalone nodes
                    for (Map.Entry<EJBClientChannel, InetSocketAddress> entry : addressesByConnection.entrySet()) {
                        final EJBClientChannel channel = entry.getKey();
                        final Connection peerConnection = channel.getChannel().getConnection();
                        final URI peerURI = buildURI(peerConnection.getProtocol(), peerConnection.getPeerAddress(InetSocketAddress.class));
                        // keep track of equivalent hostnames
                        hostReps.add(peerURI.getHost());
                        map.computeIfAbsent(peerURI, TempInfo::new);
                    }
                    // populate the modules
                    for (Map.Entry<EJBClientChannel, Set<EJBModuleIdentifier>> entry : modulesByConnection.entrySet()) {
                        final EJBClientChannel channel = entry.getKey();
                        final Connection peerConnection = channel.getChannel().getConnection();
                        final URI peerURI = buildURI(peerConnection.getProtocol(), peerConnection.getPeerAddress(InetSocketAddress.class));
                        // keep track of equivalent hostnames
                        hostReps.add(peerURI.getHost());
                        TempInfo tempInfo = new TempInfo(peerURI);
                        tempInfo.modules = entry.getValue();
                        map.put(tempInfo.destination, tempInfo);
                    }
                    // populate the clusters next
                    for (final Map.Entry<String, ClusterNodeInformation> entry : clustersByName.entrySet()) {
                        final String clusterName = entry.getKey();
                        final ClusterNodeInformation clusterNodeInformation = entry.getValue();
                        for (Map.Entry<String, CidrAddressTable<InetSocketAddress>> entry1 : clusterNodeInformation.getAddressTablesByProtocol().entrySet()) {
                            final String protocol = entry1.getKey();
                            final CidrAddressTable<InetSocketAddress> table = entry1.getValue();
                            for (CidrAddressTable.Mapping<InetSocketAddress> mapping : table) {
                                try {
                                    CidrAddress cidrAddress = mapping.getRange();
                                    final InetSocketAddress address = Inet.getResolved(mapping.getValue());
                                    final URI uri = buildURI(protocol, address);
                                    // keep track of equivalent hostnames
                                    hostReps.add(uri.getHost());
                                    TempInfo tempInfo = map.computeIfAbsent(uri, TempInfo::new);
                                    if (tempInfo.clusters == null) {
                                        tempInfo.clusters = new HashMap<>();
                                    }
                                    tempInfo.clusters.put(clusterName, cidrAddress);
                                } catch (UnknownHostException e) {
                                    Logs.MAIN.logf(Logger.Level.DEBUG, "Cannot resolve %s host during discovery attempt, skipping", mapping.getValue());
                                }
                            }
                        }
                    }

                    // merge information from equivalent URIs, if any (EJBCLIENT-349)
                    // due to mixed-mode hostnames, we can have module information and cluster information for "the same" URLs spread across multiple TempInfo structures
                    // here, we make sure that for each equivalent URL, they hold the same module and cluster information
                    for (URI destination : map.keySet()) {
                        Set<URI> equivalentURIs = getEquivalentURIs(destination, hostReps);
                        for (URI equivalentURI: equivalentURIs) {
                            // for each equivalent URI, check if we need to merge modules and clusters
                            TempInfo originalTempInfo = map.get(destination);
                            TempInfo equivalentTempInfo = map.get(equivalentURI);
                            if (originalTempInfo == null || equivalentTempInfo == null) {
                                continue;
                            }
                            // take information in each and combine
                            mergeEquivalentTempInfoStructures(originalTempInfo, equivalentTempInfo);
                        }
                    }

                    // populate the service URLs from the cross product (!) of clusters and modules
                    final AttributeValue nodeNameValue = AttributeValue.fromString(nodeName);
                    for (TempInfo info : map.values()) {
                        final ServiceURL.Builder builder = new ServiceURL.Builder();
                        builder.setUri(info.destination);
                        builder.setAbstractType(EJBClientContext.EJB_SERVICE_TYPE.getAbstractType());
                        builder.setAbstractTypeAuthority(EJBClientContext.EJB_SERVICE_TYPE.getAbstractTypeAuthority());
                        builder.addAttribute(EJBClientContext.FILTER_ATTR_NODE, nodeNameValue);
                        if (info.modules != null) for (EJBModuleIdentifier moduleIdentifier : info.modules) {
                            final String appName = moduleIdentifier.getAppName();
                            final String moduleName = moduleIdentifier.getModuleName();
                            final String distinctName = moduleIdentifier.getDistinctName();
                            if (distinctName.isEmpty()) {
                                if (appName.isEmpty()) {
                                    builder.addAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE, AttributeValue.fromString(moduleName));
                                } else {
                                    builder.addAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE, AttributeValue.fromString(appName + "/" + moduleName));
                                }
                            } else {
                                if (appName.isEmpty()) {
                                    builder.addAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE_DISTINCT, AttributeValue.fromString(moduleName + "/" + distinctName));
                                } else {
                                    builder.addAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE_DISTINCT, AttributeValue.fromString(appName + "/" + moduleName + "/" + distinctName));
                                }
                            }
                        }
                        // create a no-cluster mapping
                        serviceURLCache.add(builder.create());
                        if (info.clusters != null) for (Map.Entry<String, CidrAddress> entry : info.clusters.entrySet()) {
                            final String clusterName = entry.getKey();
                            builder.addAttribute(EJBClientContext.FILTER_ATTR_CLUSTER, AttributeValue.fromString(clusterName));
                            final CidrAddress cidrAddress = entry.getValue();
                            if (cidrAddress.getNetmaskBits() == 0) {
                                // historically we treat IPv4 and IPv6 any addresses as any
                                builder.removeAttribute(EJBClientContext.FILTER_ATTR_SOURCE_IP);
                            } else {
                                final AttributeValue value = AttributeValue.fromString(cidrAddress.toString());
                                builder.addAttribute(EJBClientContext.FILTER_ATTR_SOURCE_IP, value);
                            }
                            serviceURLCache.add(builder.create());
                        }
                    }

                    this.serviceURLCache = serviceURLCache;
                }
            }
        }
        return serviceURLCache;
    }


    /*
     * Take two TempInfo structures which are equivalent and merge their understanding of modules and clusters
     * Due to the way TempInfo structures are populated, these are the only combinations possible.
     */
    private void mergeEquivalentTempInfoStructures(TempInfo original, TempInfo equivalent) {
        if ((original.modules != null && equivalent.modules == null) && (original.clusters == null && equivalent.clusters != null)) {
            equivalent.modules = original.modules;
            original.clusters = equivalent.clusters;
        } else if ((original.modules == null && equivalent.modules != null) && (original.clusters != null && equivalent.clusters == null)) {
            original.modules = equivalent.modules;
            equivalent.clusters = original.clusters;
        }
    }

    /*
     * Given a URI and a set of equivalent hostnames, build a set of equivalent URIs
     */
    private Set<URI> getEquivalentURIs(URI uri, Set<String> hostReps) {
        Set<URI> equivalentURIs = new HashSet<URI>();
        if (hostReps.size() == 1)
            return equivalentURIs;
        Set<String> equivalentReps = new HashSet<String>(hostReps);
        equivalentReps.remove(uri.getHost());
        for (String hostRep : equivalentReps) {
            URI equivalentURI = buildURI(uri.getScheme(), new InetSocketAddress(hostRep, uri.getPort()));
            equivalentURIs.add(equivalentURI);
        }
        return equivalentURIs;
    }

    private URI buildURI(final String protocol, final InetSocketAddress socketAddress) {
        final InetAddress address = socketAddress.getAddress();
        String hostName = Inet.getHostNameIfResolved(socketAddress);
        if (hostName == null) {
            if (address instanceof Inet6Address) {
                hostName = '[' + Inet.toOptimalString(address) + ']';
            } else {
                hostName = Inet.toOptimalString(address);
            }
        }
        try {
            return new URI(protocol, null, hostName, socketAddress.getPort(), null, null, null);
        } catch (URISyntaxException e) {
            Assert.unreachableCode();
            return null;
        }
    }

    boolean isInvalid() {
        return invalid;
    }

    void setInvalid(final boolean invalid) {
        this.invalid = invalid;
    }

    void addAddress(final String protocol, final String clusterName, final CidrAddress block, final InetSocketAddress destination) {
        synchronized (this) {
            serviceURLCache = null;
            clustersByName.computeIfAbsent(clusterName, name -> new ClusterNodeInformation())
                .getAddressTablesByProtocol()
                .computeIfAbsent(protocol, ignored -> new CidrAddressTable<>())
                .put(block, destination);
        }
    }


    void removeCluster(final String clusterName) {
        synchronized (this) {
            serviceURLCache = null;
            clustersByName.remove(clusterName);
        }
    }

    void addModules(final EJBClientChannel clientChannel, final EJBModuleIdentifier[] moduleList) {
        synchronized (this) {
            serviceURLCache = null;
            Collections.addAll(modulesByConnection.computeIfAbsent(clientChannel, ignored -> new HashSet<>()), moduleList);
        }
    }

    void removeModules(final EJBClientChannel clientChannel, final HashSet<EJBModuleIdentifier> toRemove) {
        synchronized (this) {
            serviceURLCache = null;
            final Set<EJBModuleIdentifier> set = modulesByConnection.get(clientChannel);
            if (set != null) {
                set.removeAll(toRemove);
            }
        }
    }

    void removeModule(final EJBClientChannel clientChannel, final EJBModuleIdentifier toRemove) {
        synchronized (this) {
            serviceURLCache = null;
            final Set<EJBModuleIdentifier> set = modulesByConnection.get(clientChannel);
            if (set != null) {
                set.remove(toRemove);
            }
        }
    }

    void addAddress(EJBClientChannel clientChannel) {
        synchronized (this) {
            addressesByConnection.put(clientChannel, (InetSocketAddress) clientChannel.getChannel().getConnection().getPeerAddress());
            serviceURLCache = null;
        }
    }

    void removeConnection(EJBClientChannel clientChannel) {
        synchronized (this) {
            boolean moduleRemoved = modulesByConnection.remove(clientChannel) != null;
            boolean addressRemoved = addressesByConnection.remove(clientChannel) != null;
            if (moduleRemoved || addressRemoved) {
                serviceURLCache = null;
            }
        }
    }

    static final class ClusterNodeInformation {
        private final Map<String, CidrAddressTable<InetSocketAddress>> addressTablesByProtocol = new HashMap<>();

        ClusterNodeInformation() {
        }

        Map<String, CidrAddressTable<InetSocketAddress>> getAddressTablesByProtocol() {
            return addressTablesByProtocol;
        }
    }
}
