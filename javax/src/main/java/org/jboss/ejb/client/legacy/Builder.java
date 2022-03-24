package org.jboss.ejb.client.legacy;

import org.jboss.ejb.client.DeploymentNodeSelector;
import org.wildfly.common.function.ExceptionSupplier;
import org.xnio.OptionMap;

import java.util.List;
import java.util.Map;

public final class Builder extends JBossEJBProperties.CommonSubconfiguration.Builder {
    String endpointName;

    OptionMap endpointCreationOptions;
    OptionMap remoteConnectionProviderCreationOptions;
    List<JBossEJBProperties.ConnectionConfiguration> connectionList;
    List<JBossEJBProperties.HttpConnectionConfiguration> httpConnectionList;
    Map<String, JBossEJBProperties.ClusterConfiguration> clusterConfigurations;
    long invocationTimeout;
    long reconnectTimeout;
    String deploymentNodeSelectorClassName;
    int defaultCompression;
    ExceptionSupplier<DeploymentNodeSelector, ReflectiveOperationException> deploymentNodeSelectorSupplier;

    Builder() {
    }

    Builder setEndpointName(final String endpointName) {
        this.endpointName = endpointName;
        return this;
    }

    Builder setEndpointCreationOptions(final OptionMap endpointCreationOptions) {
        this.endpointCreationOptions = endpointCreationOptions;
        return this;
    }

    Builder setRemoteConnectionProviderCreationOptions(final OptionMap remoteConnectionProviderCreationOptions) {
        this.remoteConnectionProviderCreationOptions = remoteConnectionProviderCreationOptions;
        return this;
    }

    Builder setConnectionList(final List<JBossEJBProperties.ConnectionConfiguration> connectionList) {
        this.connectionList = connectionList;
        return this;
    }

    Builder setClusterConfigurations(final Map<String, JBossEJBProperties.ClusterConfiguration> clusterConfigurations) {
        this.clusterConfigurations = clusterConfigurations;
        return this;
    }

    Builder setInvocationTimeout(final long invocationTimeout) {
        this.invocationTimeout = invocationTimeout;
        return this;
    }

    Builder setReconnectTimeout(final long reconnectTimeout) {
        this.reconnectTimeout = reconnectTimeout;
        return this;
    }

    Builder setDeploymentNodeSelectorClassName(final String deploymentNodeSelectorClassName) {
        this.deploymentNodeSelectorClassName = deploymentNodeSelectorClassName;
        return this;
    }

    Builder setDeploymentNodeSelectorSupplier(final ExceptionSupplier<DeploymentNodeSelector, ReflectiveOperationException> deploymentNodeSelectorSupplier) {
        this.deploymentNodeSelectorSupplier = deploymentNodeSelectorSupplier;
        return this;
    }

    Builder setHttpConnectionList(final List<JBossEJBProperties.HttpConnectionConfiguration> httpConnectionList) {
        this.httpConnectionList = httpConnectionList;
        return this;
    }

    Builder setDefaultCompression(final int defaultCompression) {
        this.defaultCompression = defaultCompression;
        return this;
    }

    Builder initializer(String propertiesEndpoint, String defaultCallbackProp, OptionMap creationOptionProp, OptionMap remoteConnProp, Long timeoutInvoProp, Long timeoutRecoProp, int defaultCompProp){
        setEndpointName(propertiesEndpoint);
        setCallbackHandlerClassName(defaultCallbackProp);
        setEndpointCreationOptions(creationOptionProp);
        setRemoteConnectionProviderCreationOptions(remoteConnProp);
        setInvocationTimeout(timeoutInvoProp);
        setReconnectTimeout(timeoutRecoProp);
        setDefaultCompression(defaultCompProp);
        return this;
    }
}