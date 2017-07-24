package org.jboss.ejb.client.test;

import org.jboss.ejb.client.EJBClientContext;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

/**
 * Tests some basic features of wildfly-client.xml processing
 *
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public class WildflyClientXMLTestCase {

    private static final Logger logger = Logger.getLogger(WildflyClientXMLTestCase.class);
    private static final String CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME = "wildfly.config.url";
    private static final String CONFIGURATION_FILE = "wildfly-client.xml";
    private static final long INVOCATION_TIMEOUT = 10*1000;

    /**
     * Do any general setup here
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // make sure the desired configuration file is picked up
        ClassLoader cl = WildflyClientXMLTestCase.class.getClassLoader();
        File file = new File(cl.getResource(CONFIGURATION_FILE).getFile());
        System.setProperty(CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME,file.getAbsolutePath());
    }

    @Test
    public void testInvocationTimeout() {
        EJBClientContext clientContext = EJBClientContext.getCurrent();
        Assert.assertEquals("Got an unexpected timeout value", INVOCATION_TIMEOUT, clientContext.getInvocationTimeout());
    }
}
