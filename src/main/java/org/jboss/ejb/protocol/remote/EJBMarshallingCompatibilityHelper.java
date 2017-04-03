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

import org.jboss.marshalling.ObjectResolver;
import org.kohsuke.MetaInfServices;
import org.wildfly.naming.client.MarshallingCompatibilityHelper;
import org.wildfly.naming.client.Transport;
import org.wildfly.naming.client.remote.RemoteTransport;

/**
 * The naming marshalling helper for EJB types.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class EJBMarshallingCompatibilityHelper implements MarshallingCompatibilityHelper {
    public ObjectResolver getObjectResolver(final Transport transport, final boolean request) {
        if (transport instanceof RemoteTransport) {
            final RemoteTransport remoteTransport = (RemoteTransport) transport;
            if (remoteTransport.getVersion() == 1) {
                // naming version is 1, EJB version is 1 or 2 (same resolver either way)
                return new ProtocolV1ObjectResolver(remoteTransport.getConnection(), true);
            } else if (remoteTransport.getVersion() == 2) { // this refers to the naming version, not the EJB version
                // naming version is 2, EJB version is 3
                return new ProtocolV3ObjectResolver(remoteTransport.getConnection(), true);
            }
        }
        return null;
    }
}
