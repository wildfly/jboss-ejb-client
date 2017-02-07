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

import org.jboss.ejb.client.AbstractEJBMetaData;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBMetaDataImpl;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.marshalling.ObjectResolver;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ProtocolV1ObjectResolver implements ObjectResolver {
    private final NodeAffinity nodeAffinity;

    ProtocolV1ObjectResolver(final String nodeName) {
        nodeAffinity = new NodeAffinity(nodeName);
    }

    public Object readResolve(final Object replacement) {
        if (replacement instanceof EJBMetaDataImpl) {
            return ((EJBMetaDataImpl) replacement).toAbstractEJBMetaData();
        } else if ((replacement instanceof NodeAffinity) && replacement.equals(nodeAffinity)) {
            // Swap a node affinity with the name of this node with a local affinity
            return Affinity.LOCAL;
        }
        return replacement;
    }

    public Object writeReplace(final Object original) {
        if (original instanceof URIAffinity) {
            return Affinity.NONE;
        } else if (original == Affinity.LOCAL) {
            // Swap a local affinity with a node affinity with the name of this node
            return nodeAffinity;
        } else if (original instanceof AbstractEJBMetaData) {
            return new EJBMetaDataImpl((AbstractEJBMetaData<?, ?>) original);
        }
        return original;
    }
}
