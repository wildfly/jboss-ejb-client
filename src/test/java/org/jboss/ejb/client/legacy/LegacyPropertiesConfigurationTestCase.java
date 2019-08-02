package org.jboss.ejb.client.legacy;

import org.jboss.ejb.client.EJBClientContext;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class LegacyPropertiesConfigurationTestCase {

    private static final String PROPERTIES_FILE_MAX_NODES_SET = "maximum-connected-nodes-jboss-ejb-client.properties";
    private static final String PROPERTIES_FILE_MAX_NODES_NOT_SET = "clustered-jboss-ejb-client.properties";

    @Test
    public void testMaximumConnectedNodesSet() throws IOException {
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(JBossEJBProperties.class.getClassLoader(), PROPERTIES_FILE_MAX_NODES_SET);
        Assert.assertEquals(-1, ejbProperties.getClusterConfigurations().get("ejb1").getMaximumAllowedConnectedNodes()); // default value returned by JBossEJBProperties
        Assert.assertEquals(42, ejbProperties.getClusterConfigurations().get("ejb2").getMaximumAllowedConnectedNodes()); // configured
        EJBClientContext context = buildContextFromLegacyProperties(ejbProperties);

        Assert.assertEquals(42, context.getMaximumConnectedClusterNodes());
    }

    @Test
    public void testMaximumConnectedNodesNotSet() throws IOException {
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(JBossEJBProperties.class.getClassLoader(), PROPERTIES_FILE_MAX_NODES_NOT_SET);
        Assert.assertEquals(-1, ejbProperties.getClusterConfigurations().get("ejb").getMaximumAllowedConnectedNodes()); // default value returned by JBossEJBProperties
        EJBClientContext context = buildContextFromLegacyProperties(ejbProperties);

        Assert.assertEquals(10, context.getMaximumConnectedClusterNodes()); // default value
    }

    private EJBClientContext buildContextFromLegacyProperties(JBossEJBProperties ejbProperties) {
        JBossEJBProperties.getContextManager().setThreadDefault(ejbProperties);
        EJBClientContext.Builder builder = new EJBClientContext.Builder();
        LegacyPropertiesConfiguration.configure(builder);
        return builder.build();
    }
}
