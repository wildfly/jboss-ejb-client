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
final class ProtocolV1ObjectResolver implements ObjectResolver {

    private static final boolean DISABLE_V1_AFFINITY_REWRITE = Boolean.getBoolean("org.jboss.ejb.client.disable-v1-affinity-rewrite");

    private final Affinity peerURIAffinity;
    private final Connection connection;
    private final NodeAffinity thisNodeAffinity;

    ProtocolV1ObjectResolver(final Connection connection, URI peerURI) {
        this.connection = connection;
        this.peerURIAffinity = Affinity.forUri(peerURI);
        this.thisNodeAffinity = new NodeAffinity(connection.getEndpoint().getName());
    }

    public Object readResolve(final Object replacement) {
        if (replacement instanceof EJBMetaDataImpl) {
            return ((EJBMetaDataImpl) replacement).toAbstractEJBMetaData();
        } else if ((replacement instanceof NodeAffinity) && ((NodeAffinity)replacement).getNodeName().equals(connection.getRemoteEndpointName())) {
            // Swap a node affinity with the name of the remote peer for a URI affinity
            return peerURIAffinity;
        } else if(replacement == Affinity.NONE && !DISABLE_V1_AFFINITY_REWRITE) {
            return peerURIAffinity;
        }
        return replacement;
    }

    public Object writeReplace(final Object original) {
        if (original instanceof URIAffinity) {
            return Affinity.NONE;
        } else if (original == Affinity.LOCAL) {
            // Swap a local affinity with a node affinity with the name of this node
            return thisNodeAffinity;
        } else if (original instanceof AbstractEJBMetaData) {
            return new EJBMetaDataImpl((AbstractEJBMetaData<?, ?>) original);
        } else if (original instanceof RemoteExceptionCause) {
            // old clients will not have this class
            return ((RemoteExceptionCause) original).toPlainThrowable();
        }
        return original;
    }
}
