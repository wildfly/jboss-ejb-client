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
import org.jboss.ejb.client.URIAffinity;
import org.jboss.marshalling.ObjectResolver;

/**
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
final class ProtocolV3ObjectResolver implements ObjectResolver {
    private final Affinity peerURIAffinity;

    ProtocolV3ObjectResolver(final URI peerURI) {
        peerURIAffinity = Affinity.forUri(peerURI);
    }

    public Object readResolve(final Object replacement) {
        // Swap a local affinity with a URI affinity with the peer's URI
        if (replacement == Affinity.LOCAL) {
            return peerURIAffinity;
        }
        return replacement;
    }

    public Object writeReplace(final Object original) {
        // Swap a URI affinity with the peer's URI with a local affinity
        if ((original instanceof URIAffinity) && original.equals(peerURIAffinity)) {
            return Affinity.LOCAL;
        }
        return original;
    }
}
