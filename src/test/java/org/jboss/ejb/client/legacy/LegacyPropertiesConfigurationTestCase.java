package org.jboss.ejb.client.legacy;

import org.jboss.ejb.client.EJBClientContext;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests to verify that configuration of the EJBClientContext by a legacy jboss-ejb-client.properties file works
 * as expected. The jboss-ejb-client.properties file allows configuring invocation-related properties for the
 * EJBClientContext at three different levels:
 * - invocations targetting singleton nodes
 * - invocations targetting clusters
 * - invocations targetting specific nodes in clusters
 *
 * @author <a href="mailto:thofman@redhat.com">Thomas Hofman</a>
 */
public class LegacyPropertiesConfigurationTestCase {

    private static final String PROPERTIES_FILE_MAX_NODES_SET = "maximum-connected-nodes-jboss-ejb-client.properties";
    private static final String PROPERTIES_FILE_MAX_NODES_NOT_SET = "clustered-jboss-ejb-client.properties";

    /**
     * Tests that values configured for the cluster property "max-allowed-connected-nodes" are passed through
     * to the resulting EJBClientContext.
     *
     * @throws IOException
     */
    @Test
    public void testMaximumConnectedNodesSet() throws IOException {
        JBossEJBProperties ejbProperties = JBossEJBProperties.fromClassPath(JBossEJBProperties.class.getClassLoader(), PROPERTIES_FILE_MAX_NODES_SET);
        Assert.assertEquals(-1, ejbProperties.getClusterConfigurations().get("ejb1").getMaximumAllowedConnectedNodes()); // default value returned by JBossEJBProperties
        Assert.assertEquals(42, ejbProperties.getClusterConfigurations().get("ejb2").getMaximumAllowedConnectedNodes()); // configured
        EJBClientContext context = buildContextFromLegacyProperties(ejbProperties);

        Assert.assertEquals(42, context.getMaximumConnectedClusterNodes());
    }

    /**
     * Tests that default values configured for the cluster property "max-allowed-connected-nodes" are passed through
     * to the resulting EJBClientContext when the property is not set.
     *
     * @throws IOException
     */
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
