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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.ejb._private.Logs;
import org.kohsuke.MetaInfServices;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.LegacyConfiguration;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.sasl.localuser.LocalUserClient;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.sasl.SaslUtils;

/**
 * The interface to merge EJB properties into the Elytron configuration.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class ElytronLegacyConfiguration implements LegacyConfiguration {

    private static final boolean useQuietAuth = AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> Boolean.valueOf(System.getProperty("jboss.sasl.local-user.quiet-auth", "true"))).booleanValue();
    private static final String[] NO_STRINGS = new String[0];

    public AuthenticationContext getConfiguredAuthenticationContext() {
        AuthenticationContext context = AuthenticationContext.empty();

        final JBossEJBProperties properties = JBossEJBProperties.getCurrent();

        if (properties == null) {
            return context;
        }

        Logs.MAIN.legacyEJBPropertiesSecurityConfigurationInUse();

        for (JBossEJBProperties.ConnectionConfiguration configuration : properties.getConnectionList()) {
            // we don't actually care about the protocol for Elytron configuration
            MatchRule rule = MatchRule.ALL.matchAbstractType("ejb", "jboss");
            AuthenticationConfiguration config = AuthenticationConfiguration.EMPTY;

            final String host = configuration.getHost();
            if (host == null) {
                // skip
                continue;
            }
            rule = rule.matchHost(host);
            final int port = configuration.getPort();
            if (port != -1) {
                rule = rule.matchPort(port);
            }

            config = configureCommon(properties, configuration, config);

            context = context.with(
                rule,
                config
            );
        }

        for (Map.Entry<String, JBossEJBProperties.ClusterConfiguration> entry : properties.getClusterConfigurations().entrySet()) {
            final String clusterName = entry.getKey();
            final JBossEJBProperties.ClusterConfiguration configuration = entry.getValue();

            final List<JBossEJBProperties.ClusterNodeConfiguration> nodeConfigurations = configuration.getNodeConfigurations();
            if (nodeConfigurations != null) {
                for (JBossEJBProperties.ClusterNodeConfiguration nodeConfiguration : nodeConfigurations) {
                    MatchRule rule = MatchRule.ALL.matchAbstractType("ejb", "jboss");
                    AuthenticationConfiguration config = AuthenticationConfiguration.EMPTY;

                    rule = rule.matchProtocol("node");
                    rule = rule.matchUrnName(nodeConfiguration.getNodeName());

                    config = configureCommon(properties, nodeConfiguration, config);

                    context = context.with(
                            rule,
                            config
                    );
                }
            }
        }
        return context;
    }

    private static AuthenticationConfiguration configureCommon(final JBossEJBProperties properties, final JBossEJBProperties.CommonSubconfiguration configuration, AuthenticationConfiguration config) {
        final JBossEJBProperties.AuthenticationConfiguration authenticationConfiguration = configuration.getAuthenticationConfiguration();
        final String userName = authenticationConfiguration.getUserName();
        if (userName != null) config = config.useName(userName);
        final String realm = authenticationConfiguration.getMechanismRealm();
        if (realm != null) config = config.useRealm(realm);
        final ExceptionSupplier<CallbackHandler, ReflectiveOperationException> callbackHandlerSupplier = authenticationConfiguration.getCallbackHandlerSupplier();
        if (callbackHandlerSupplier != null) {
            final CallbackHandler callbackHandler;
            try {
                callbackHandler = callbackHandlerSupplier.get();
            } catch (ReflectiveOperationException e) {
                throw Logs.MAIN.cannotInstantiateCallbackHandler(properties.getDefaultCallbackHandlerClassName(), e);
            }
            config = config.useCallbackHandler(callbackHandler);
        }
        final String password = authenticationConfiguration.getPassword();
        if (password != null) config = config.usePassword(password);

        final OptionMap options = configuration.getConnectionOptions();
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Map<String, String> props = (Map) SaslUtils.createPropertyMap(options, false);
        if (! props.isEmpty()) {
            config = config.useMechanismProperties(props);
        } else {
            config = config.useMechanismProperties(Collections.singletonMap(LocalUserClient.QUIET_AUTH, Boolean.toString(useQuietAuth)));
        }
        if (options.contains(Options.SASL_DISALLOWED_MECHANISMS)) {
            config = config.forbidSaslMechanisms(options.get(Options.SASL_DISALLOWED_MECHANISMS).toArray(NO_STRINGS));
        } else if (options.contains(Options.SASL_MECHANISMS)) {
            config = config.allowSaslMechanisms(options.get(Options.SASL_MECHANISMS).toArray(NO_STRINGS));
        }
        return config;
    }
}
