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

import java.net.URI;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.marshalling.ObjectResolver;
import org.jboss.remoting3.Connection;

/**
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
final class ProtocolV3ObjectResolver extends ProtocolObjectResolver implements ObjectResolver {
    private final NodeAffinity peerNodeAffinity;
    private final NodeAffinity selfNodeAffinity;
    private final URIAffinity peerUriAffinity;
    private final boolean preferUri;

    ProtocolV3ObjectResolver(final Connection connection, final boolean preferUri) {
        final String remoteEndpointName = connection.getRemoteEndpointName();
        peerNodeAffinity = remoteEndpointName == null ? null : new NodeAffinity(remoteEndpointName);
        final String localEndpointName = connection.getEndpoint().getName();
        selfNodeAffinity = localEndpointName == null ? null : new NodeAffinity(localEndpointName);
        this.preferUri = preferUri;
        final URI peerURI = connection.getPeerURI();
        peerUriAffinity = peerURI == null ? null : (URIAffinity) Affinity.forUri(peerURI);
    }

    public Object readResolve(final Object replacement) {
        if (replacement == Affinity.LOCAL) {
            // This shouldn't be possible.  If it happens though, we will guess that it is the peer talking about itself
            return preferUri && peerUriAffinity != null ? peerUriAffinity : peerNodeAffinity != null ? peerNodeAffinity : Affinity.NONE;
        } else if (replacement instanceof NodeAffinity) {
            if (selfNodeAffinity != null && replacement.equals(selfNodeAffinity)) {
                return Affinity.LOCAL;
            } else if (preferUri && peerUriAffinity != null && peerNodeAffinity != null && replacement.equals(peerNodeAffinity)) {
                // the peer is talking about itself; use the more specific URI if we have one
                return peerUriAffinity;
            }
        }
        return replacement;
    }

    public Object writeReplace(final Object original) {
        if (original == Affinity.LOCAL && selfNodeAffinity != null) {
            // we don't know the peer's view URI of us, if there even is one, so switch it to node affinity and let the peer sort it out
            return selfNodeAffinity;
        } else if (peerUriAffinity != null && original instanceof URIAffinity && original.equals(peerUriAffinity) && peerNodeAffinity != null) {
            // it's the peer node; the peer won't know its own URI though, so send its node affinity instead
            return peerNodeAffinity;
        }
        return super.writeReplace(original);
    }
}
