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

import java.util.List;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb._private.SystemProperties;
import org.kohsuke.MetaInfServices;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.LegacyConfiguration;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.sasl.localuser.LocalUserClient;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;
import org.xnio.sasl.SaslUtils;

/**
 * The interface to merge EJB properties into the Elytron configuration.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class ElytronLegacyConfiguration implements LegacyConfiguration {

    private static final boolean QUIET_AUTH = SystemProperties.getBoolean(SystemProperties.QUIET_AUTH);
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

            MatchRule defaultRule = MatchRule.ALL.matchAbstractType("ejb", "jboss");
            AuthenticationConfiguration defaultConfig = AuthenticationConfiguration.EMPTY;

            defaultRule = defaultRule.matchProtocol("cluster");
            defaultRule = defaultRule.matchUrnName(clusterName);

            defaultConfig = configureCommon(properties, configuration, defaultConfig);

            context = context.with(
                    defaultRule,
                    defaultConfig
            );

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
        final String userName = authenticationConfiguration == null ? null : authenticationConfiguration.getUserName();
        if (userName != null) config = config.useName(userName);
        final String realm = authenticationConfiguration == null ? null : authenticationConfiguration.getMechanismRealm();
        if (realm != null) config = config.useRealm(realm);
        final ExceptionSupplier<CallbackHandler, ReflectiveOperationException> callbackHandlerSupplier = authenticationConfiguration == null ? null : authenticationConfiguration.getCallbackHandlerSupplier();
        CallbackHandler callbackHandler = null;
        if (callbackHandlerSupplier != null) {
            try {
                callbackHandler = callbackHandlerSupplier.get();
            } catch (ReflectiveOperationException e) {
                throw Logs.MAIN.cannotInstantiateCallbackHandler(properties.getDefaultCallbackHandlerClassName(), e);
            }
            config = config.useCallbackHandler(callbackHandler);
        }
        final String password = authenticationConfiguration == null ? null : authenticationConfiguration.getPassword();
        if (password != null) config = config.usePassword(password);

        OptionMap options = configuration.getConnectionOptions();
        options = setQuietLocalAuth(options, QUIET_AUTH);

        @SuppressWarnings({"unchecked", "rawtypes"})
        final Map<String, String> props = (Map) SaslUtils.createPropertyMap(options, false);
        if (! props.isEmpty()) {
            config = config.useMechanismProperties(props);
        }
        if (options.contains(Options.SASL_DISALLOWED_MECHANISMS)) {
            config = config.setSaslMechanismSelector(SaslMechanismSelector.DEFAULT.forbidMechanisms(options.get(Options.SASL_DISALLOWED_MECHANISMS).toArray(NO_STRINGS)));
        } else if (options.contains(Options.SASL_MECHANISMS)) {
            config = config.setSaslMechanismSelector(SaslMechanismSelector.DEFAULT.addMechanisms(options.get(Options.SASL_MECHANISMS).toArray(NO_STRINGS)));
        }
        config = config.useProvidersFromClassLoader(ElytronLegacyConfiguration.class.getClassLoader());
        return config;
    }

    /**
     * Set the quiet local auth property to the given value if the user hasn't already set this property.
     *
     * @param optionMap the option map
     * @param useQuietAuth the value to set the quiet local auth property to
     * @return the option map with the quiet local auth property set to the given value if the user hasn't already set this property
     */
    private static OptionMap setQuietLocalAuth(final OptionMap optionMap, final boolean useQuietAuth) {
        final Sequence<Property> existingSaslProps = optionMap.get(Options.SASL_PROPERTIES);
        if (existingSaslProps != null) {
            for (Property prop : existingSaslProps) {
                final String propKey = prop.getKey();
                if (propKey.equals(LocalUserClient.QUIET_AUTH) || propKey.equals(LocalUserClient.LEGACY_QUIET_AUTH)) {
                    // quiet local auth property was already set, do not override it
                    return optionMap;
                }
            }
            // set the quiet local auth property since it wasn't already set in SASL_PROPERTIES
            existingSaslProps.add(Property.of(LocalUserClient.QUIET_AUTH, Boolean.toString(useQuietAuth)));
            return optionMap;
        }
        // set the quiet local auth property since no SASL_PROPERTIES were set
        final OptionMap.Builder updatedOptionMapBuilder = OptionMap.builder().addAll(optionMap);
        updatedOptionMapBuilder.set(Options.SASL_PROPERTIES, Sequence.of(Property.of(LocalUserClient.QUIET_AUTH, Boolean.toString(useQuietAuth))));
        return updatedOptionMapBuilder.getMap();
    }
}
