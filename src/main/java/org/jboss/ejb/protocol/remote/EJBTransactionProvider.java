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

import java.io.IOException;

import org.jboss.remoting3.Connection;
import org.kohsuke.MetaInfServices;
import org.wildfly.transaction.client.provider.remoting.RemotingFallbackPeerProvider;
import org.wildfly.transaction.client.provider.remoting.RemotingOperations;

/**
 * The legacy EJB-protocol transaction provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices(RemotingFallbackPeerProvider.class)
public final class EJBTransactionProvider implements RemotingFallbackPeerProvider {

    public EJBTransactionProvider() {
    }

    public RemotingOperations getOperations(final Connection connection) throws IOException {
        return new EJBTransactionOperations(connection);
    }
}
