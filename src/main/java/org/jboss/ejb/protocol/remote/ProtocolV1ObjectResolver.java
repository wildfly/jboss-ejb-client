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

import org.jboss.ejb.client.AbstractEJBMetaData;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBMetaDataImpl;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.marshalling.ObjectResolver;
import org.jboss.remoting3.Connection;
import org.wildfly.common.rpc.RemoteExceptionCause;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ProtocolV1ObjectResolver extends ProtocolObjectResolver implements ObjectResolver {
    private final NodeAffinity peerNodeAffinity;
    private final NodeAffinity selfNodeAffinity;
    private final URIAffinity peerUriAffinity;
    private final boolean preferUri;

    ProtocolV1ObjectResolver(final Connection connection, final boolean preferUri) {
        final String remoteEndpointName = connection.getRemoteEndpointName();
        peerNodeAffinity = remoteEndpointName == null ? null : new NodeAffinity(remoteEndpointName);
        final String localEndpointName = connection.getEndpoint().getName();
        selfNodeAffinity = localEndpointName == null ? null : new NodeAffinity(localEndpointName);
        this.preferUri = preferUri;
        final URI peerURI = connection.getPeerURI();
        peerUriAffinity = peerURI == null ? null : (URIAffinity) Affinity.forUri(peerURI);
    }

    public Object readResolve(final Object replacement) {
        if (replacement instanceof EJBMetaDataImpl) {
            return ((EJBMetaDataImpl) replacement).toAbstractEJBMetaData();
        } else if (replacement instanceof NodeAffinity) {
            if (replacement.equals(selfNodeAffinity)) {
                // Peer sent our node name; make it local
                return Affinity.LOCAL;
            } else if (preferUri && peerUriAffinity != null && replacement.equals(peerNodeAffinity)) {
                // Peer (server) sent their own node name; make it a URI if we can
                return peerUriAffinity;
            }
        } else if(replacement == Affinity.NONE) {
            return peerUriAffinity;
        }
        return replacement;
    }

    public Object writeReplace(final Object original) {
        if (original instanceof URIAffinity) {
            if (peerUriAffinity != null && original.equals(peerUriAffinity) && peerNodeAffinity != null) {
                return peerNodeAffinity;
            }
            return Affinity.NONE;
        } else if (original == Affinity.LOCAL && selfNodeAffinity != null) {
            // Swap a local affinity with a node affinity with the name of this node
            return selfNodeAffinity;
        } else if (original instanceof AbstractEJBMetaData) {
            return new EJBMetaDataImpl((AbstractEJBMetaData<?, ?>) original);
        } else if (original instanceof RemoteExceptionCause) {
            // old clients will not have this class
            return ((RemoteExceptionCause) original).toPlainThrowable();
        }
        return super.writeReplace(original);
    }
}
