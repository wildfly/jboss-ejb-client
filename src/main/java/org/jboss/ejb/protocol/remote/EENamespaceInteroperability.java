/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import org.jboss.marshalling.ClassNameTransformer;
import org.jboss.marshalling.MarshallingConfiguration;

/**
 * EE namespace interoperability implementation for allowing Jakarta EE namespace servers and clients communication with
 * Javax EE namespace endpoints.
 *
 * @author Flavia Rainone
 * @author Richard Opalka
 */
final class EENamespaceInteroperability {
    /**
     * Indicates if EE namespace interoperable mode is enabled.
     */
    static final boolean EE_NAMESPACE_INTEROPERABLE_MODE = Boolean.parseBoolean(
            org.wildfly.security.manager.WildFlySecurityManager.getPropertyPrivileged("org.wildfly.ee.namespace.interop", "false"));

    static {
        if (EE_NAMESPACE_INTEROPERABLE_MODE) {
            org.jboss.ejb._private.Logs.REMOTING.javaeeToJakartaeeBackwardCompatibilityLayerInstalled();
        }
    }

    private EENamespaceInteroperability() {}

    /**
     * Handles EE namespace interoperability for endpoint creation, updating the @{code marshallingConfiguration} to
     * transform Javax EE <-> Jakarta EE namespace classes if needed.
     *
     * @param marshallingConfiguration the marshalling configuration that will be used by the endpoint
     * @param channelProtocolVersion the channel protocol version used by the endpoint
     */
    static void handleInteroperability(MarshallingConfiguration marshallingConfiguration, int channelProtocolVersion) {
        if (EE_NAMESPACE_INTEROPERABLE_MODE && channelProtocolVersion < Protocol.JAKARTAEE_PROTOCOL_VERSION &&
                Protocol.LATEST_VERSION >= Protocol.JAKARTAEE_PROTOCOL_VERSION) {
            // current protocol version is version 4 or above, but the remote counterpart uses EJB PROTOCOL version 3 or below
            // so in this case we need to translate classes from Javax EE API to Jakarta EE API and vice versa
            marshallingConfiguration.setClassNameTransformer(ClassNameTransformer.JAVAEE_TO_JAKARTAEE);
        }
    }
}
