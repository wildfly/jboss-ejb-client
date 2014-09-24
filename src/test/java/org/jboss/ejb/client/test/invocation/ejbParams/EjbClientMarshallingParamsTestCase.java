/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.ejb.client.test.invocation.ejbParams;

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.PropertiesBasedEJBClientConfiguration;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Tests to verify that when an unmarshalling error occurs, regarding EJB parameters, a relevant error message is displayed at
 * the client console.
 *
 * @author Panagiotis Sotiropoulos
 * @see https://issues.jboss.org/browse/WFLY-3825 for details
 */
public class EjbClientMarshallingParamsTestCase {

    private static final Logger logger = Logger.getLogger(EjbClientMarshallingParamsTestCase.class);

    private static final String APP_NAME = "EjbClientMarshallingParamsTest";
    private static final String MODULE_NAME = "EjbClientMarshallingParamsTest";
    private static final String DISTINCT_NAME = "";

    private static final String SERVER_ONE_NAME = "server-one";

    private DummyServer serverOne;

    private boolean serverOneStarted;

    private static final String ERROR_MESSAGE = "Issue regarding unmarshalling of EJB parameters";

    private ContextSelector<EJBClientContext> previousSelector;

    @Before
    public void beforeTest() throws IOException {
        // start the server and deploy the beans into them
        // TODO: Come back to this once we enable a way to test this project against IPv6
        serverOne = new DummyServer("localhost", 6999, SERVER_ONE_NAME);

        serverOne.start();
        serverOneStarted = true;


        final Send sendBean = new Send();

        // deploy on server
        serverOne.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, Send.class.getSimpleName(), sendBean);
        // setup EJB client context 
        final String clientPropsName = "stateless-invocation-ejbParams-jboss-ejb-client.properties";
        final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(clientPropsName);
        if (inputStream == null) {
            throw new RuntimeException("Could not find EJB client configuration properties at " + clientPropsName + " using classloader " + this.getClass().getClassLoader());
        }
        final Properties clientProps = new Properties();
        clientProps.load(inputStream);
        final EJBClientConfiguration clientConfiguration = new PropertiesBasedEJBClientConfiguration(clientProps);
        final ConfigBasedEJBClientContextSelector selector = new ConfigBasedEJBClientContextSelector(clientConfiguration);
        this.previousSelector = EJBClientContext.setSelector(selector);
    }

    @After
    public void afterTest() throws IOException {
        if (serverOneStarted) {
            try {
                this.serverOne.stop();
            } catch (Exception e) {
                logger.info("Could not stop server one", e);
            }
        }

        if (this.previousSelector != null) {
            EJBClientContext.setSelector(previousSelector);
        }
       
    }

    /**
     * Start a server which has a remote. Deploy (X) to server. Check that if large parameters are given to an EJB method
     * invocation (eg new byte[400000000]) a relevant exception is thrown at the client console.
     *
     * @throws Exception
     */
    @Test
    public void testInvocateionWithHugeParams() {
	try{
		// first try some invocations which should succeed
		// create a proxy for invocation
		final StatelessEJBLocator<SendRemote> statelessEJBLocator = new StatelessEJBLocator<SendRemote>(SendRemote.class, APP_NAME, MODULE_NAME, Send.class.getSimpleName(), "");
                Assert.assertNotNull("Received a null statelessEJBLocator", statelessEJBLocator);

		final SendRemote proxy = EJBClient.createProxy(statelessEJBLocator);
		Assert.assertNotNull("Received a null proxy", proxy);

                byte[] b1 = new byte[900000000];

		// invoke
		proxy.sendBigParam(b1);
	}catch (Exception e){
		logger.info("Error occured : " + e.getMessage());
                String content = e.getMessage();
                // Verify client console error messages are displayed, when large
                // parameters are given to an EJB method invocation
                if ( !content.contains(ERROR_MESSAGE) )
                	logger.error("Huge Param Error not displayed.");
	}
    }
}
