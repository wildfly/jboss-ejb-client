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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBModuleIdentifier;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.CidrAddressTable;
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
                    // populate the modules first
                    for (Map.Entry<EJBClientChannel, Set<EJBModuleIdentifier>> entry : modulesByConnection.entrySet()) {
                        final EJBClientChannel channel = entry.getKey();
                        final URI peerURI = channel.getChannel().getConnection().getPeerURI();
                        TempInfo tempInfo = new TempInfo(peerURI);
                        tempInfo.modules = entry.getValue();
                        map.put(tempInfo.destination, tempInfo);
                    }
                    // populate standalone nodes next (these will most likely be duplicates of above)
                    for (Map.Entry<EJBClientChannel, InetSocketAddress> entry : addressesByConnection.entrySet()) {
                        final EJBClientChannel channel = entry.getKey();
                        final URI peerURI = channel.getChannel().getConnection().getPeerURI();
                        map.computeIfAbsent(peerURI, TempInfo::new);
                    }
                    // populate the clusters next
                    for (final Map.Entry<String, ClusterNodeInformation> entry : clustersByName.entrySet()) {
                        final String clusterName = entry.getKey();
                        final ClusterNodeInformation clusterNodeInformation = entry.getValue();
                        for (Map.Entry<String, CidrAddressTable<InetSocketAddress>> entry1 : clusterNodeInformation.getAddressTablesByProtocol().entrySet()) {
                            final String protocol = entry1.getKey();
                            final CidrAddressTable<InetSocketAddress> table = entry1.getValue();
                            for (CidrAddressTable.Mapping<InetSocketAddress> mapping : table) {
                                CidrAddress cidrAddress = mapping.getRange();
                                final InetSocketAddress address = mapping.getValue();
                                URI uri;
                                try {
                                    uri = new URI(protocol, null, address.getHostString(), address.getPort(), null, null, null);
                                } catch (URISyntaxException e) {
                                    continue;
                                }
                                TempInfo tempInfo = map.computeIfAbsent(uri, TempInfo::new);
                                if (tempInfo.clusters == null) {
                                    tempInfo.clusters = new HashMap<>();
                                }
                                tempInfo.clusters.put(clusterName, cidrAddress);
                            }
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
