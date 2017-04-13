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

import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.wildfly.discovery.Discovery;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceType;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.ServicesQueue;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryRequest;
import org.wildfly.discovery.spi.DiscoveryResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.security.AccessController.doPrivileged;

/**
 * Provides discovery service based on persistent cluster service registry entries.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemotingClusteredEJBDiscoveryProvider implements DiscoveryProvider {
    static final RemotingClusteredEJBDiscoveryProvider INSTANCE = new RemotingClusteredEJBDiscoveryProvider();

    private RemotingClusteredEJBDiscoveryProvider() {
        Endpoint.getCurrent(); //this will blow up if remoting is not present, preventing this from being registered
    }

    public DiscoveryRequest discover(final ServiceType serviceType, final FilterSpec filterSpec, final DiscoveryResult result) {
        if (!serviceType.implies(ServiceType.of("ejb", "jboss"))) {
            // only respond to requests for JBoss EJB services
            result.complete();
            return DiscoveryRequest.NULL;
        }
        final EJBClientContext ejbClientContext = EJBClientContext.getCurrent();
        final RemoteEJBReceiver ejbReceiver = ejbClientContext.getAttachment(RemoteTransportProvider.ATTACHMENT_KEY);
        if (ejbReceiver == null) {
            // ???
            result.complete();
            return DiscoveryRequest.NULL;
        }

        // search for any cluster related ServiceURLs for this filterspec
        try (final ServicesQueue servicesQueue = Discovery.create(ejbReceiver.getRemoteTransportProvider().getClusterDiscoveryProvider()).discover(serviceType, filterSpec)) {
            URI serviceURI = servicesQueue.take();
            // add matches if there are results
            if (serviceURI != null) {
                while (serviceURI != null) {
                    result.addMatch(serviceURI);
                    serviceURI = servicesQueue.take();
                }
                result.complete();
                return DiscoveryRequest.NULL;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }

        result.complete();
        return DiscoveryRequest.NULL;
    }
}