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

package org.jboss.ejb.client.legacy;

import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.ejb._private.NetworkUtil;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class CommonLegacyConfiguration {
    private CommonLegacyConfiguration() {
    }

    static URI getUri(final JBossEJBProperties.ConnectionConfiguration connectionConfiguration, final OptionMap connectionOptions) {
        final String host = connectionConfiguration.getHost();
        if (host == null) {
            return null;
        }
        final int port = connectionConfiguration.getPort();
        if (port == -1) {
            return null;
        }
        final String scheme;
        final String protocol = connectionConfiguration.getProtocol();
        if (protocol == null) {
            if (connectionOptions.get(Options.SECURE, false) && connectionOptions.get(Options.SSL_ENABLED, false)) {
                scheme = "remote+https";
            } else {
                scheme = "remote+http";
            }
        } else {
            scheme = protocol;
        }
        try {
            return new URI(scheme, null, NetworkUtil.formatPossibleIpv6Address(host), port, null, null, null);
        } catch (URISyntaxException e) {
            return null;
        }

    }
}
