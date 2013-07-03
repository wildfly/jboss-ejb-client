/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client.naming.ejb;

import junit.framework.Assert;
import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver;
import org.jboss.ejb.client.test.common.AnonymousCallbackHandler;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.EchoRemote;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.jboss.ejb.client.remoting.IoFutureHelper.get;

/**
 * Test for the EJBCLIENT-34 feature which includes the ability to create scoped EJB client context using the
 * properties passed to the {@link InitialContext} constructor
 *
 * @author Jaikiran Pai
 * @see https://issues.jboss.org/browse/EJBCLIENT-34
 */
public class JNDIContextInvocationTestCase {

    private static final Logger logger = Logger.getLogger(JNDIContextInvocationTestCase.class);

    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private static final int serverPort = 6999;
    private static final String serverHost = "localhost";

    private DummyServer server;
    private ContextSelector<EJBClientContext> previousSelector;

    /**
     * Just creates a dummy server which can handle EJB invocations
     *
     * @throws IOException
     */
    @Before
    public void beforeTest() throws IOException {
        server = new DummyServer(serverHost, serverPort, "jndi-invocation-test-server");
        server.start();

        final EchoRemote echoBean = new EchoBean();

        // deploy on server
        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), echoBean);

        // setup a EJB client context selector which can handle scoped contexts
        this.previousSelector = EJBClientContext.setSelector(new ConfigBasedEJBClientContextSelector(null));

    }

    /**
     * Stops the server
     *
     * @throws IOException
     */
    @After
    public void afterTest() throws IOException {
        // reset the selector
        if (this.previousSelector != null) {
            EJBClientContext.setSelector(previousSelector);
        }
        try {
            this.server.stop();
        } catch (Exception e) {
            logger.info("Could not stop server", e);
        }
    }

    /**
     * A simple test which uses the {@link InitialContext} constructor to pass in the EJB context properties
     * and relies on the EJB client API to create a scoped EJB client context which will be used by the EJB
     * proxies returned by the lookup method on that JNDI context
     *
     * @throws Exception
     */
    @Test
    public void testSimpleLookupInvocation() throws Exception {
        // get the JNDI properties
        final Properties jndiProps = this.getEJBClientConfigurationProperties();
        // create the JNDI context using those properties
        final Context context = new InitialContext(jndiProps);
        try {
            // lookup the EJB proxy
            final EchoRemote echoBean = (EchoRemote) context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + DISTINCT_NAME + "/" + EchoBean.class.getSimpleName() + "!" + EchoRemote.class.getName());
            final String message = "EJBCLIENT-34!!!";
            // invoke on that proxy and expect it to use the EJB client context (and the receiver within it) for the invocation
            final String echo = echoBean.echo(message);
            Assert.assertEquals("Unexpected echo message received from bean", message, echo);
        } finally {
            // close the context after we are done
            context.close();
        }
    }

    /**
     * Tests that EJB client context scoped and created via a JNDI context is available at <code>ejb:/EJBClientContext</code>
     * jndi name within that same JNDI context
     *
     * @throws Exception
     */
    @Test
    public void testEJBClientContextLookup() throws Exception {
        // get the JNDI properties
        final Properties jndiProps = this.getEJBClientConfigurationProperties();
        // create the JNDI context using those properties
        final Context context = new InitialContext(jndiProps);
        try {
            final EJBClientContext ejbClientContext = (EJBClientContext) context.lookup("ejb:/EJBClientContext");
            Assert.assertNotNull("Lookup of ejb:/EJBClientContext returned null", ejbClientContext);
        } finally {
            // close the context after we are done
            context.close();
        }
    }

    /**
     * Tests that a proxy which was scoped to a EJB client context, is <b>not</b> scoped to any
     * EJB client context after it is serialized/deserialized.
     *
     * @throws Exception
     */
    @Test
    public void testProxySerializationInvocation() throws Exception {
        // first test that the invocation works before serialization of the proxy
        // get the JNDI properties
        final Properties jndiProps = this.getEJBClientConfigurationProperties();
        // create the JNDI context using those properties
        final Context context = new InitialContext(jndiProps);
        try {
            // lookup the EJB proxy
            final EchoRemote echoBean = (EchoRemote) context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + DISTINCT_NAME + "/" + EchoBean.class.getSimpleName() + "!" + EchoRemote.class.getName());
            final String message = "EJBCLIENT-34!!!";
            // invoke on that proxy and expect it to use the EJB client context (and the receiver within it) for the invocation
            final String echo = echoBean.echo(message);
            Assert.assertEquals("Unexpected echo message received from bean", message, echo);

            // Now serialize/deserialize the proxy
            final EchoRemote serializedProxy = this.serializeDeserialize(echoBean);
            final String messageForSerializedProxy = "Proxy serialized";
            try {
                // invoke on the serialized proxy - it's expected that this will fail since the serialized
                // version of the proxy no longer is scoped to the EJB client context which has the appropriate receiver
                // that can handle this invocation
                final String echoAfterSerialization = serializedProxy.echo(messageForSerializedProxy);
                Assert.fail("Invocation on serialized proxy was expected to fail since there was supposed to be no receivers in the \"current\" EJB client context");
            } catch (IllegalStateException ise) {
                // expected since we don't have any receivers registered in the "current" EJB client context
                logger.info("Got the expected exception on invocation of a serialized proxy: " + ise.getMessage());
            }
            // now create a receiver which can handle the EJB request
            final EJBReceiver receiver = this.createReceiver();
            try {
                EJBClientContext.requireCurrent().registerEJBReceiver(receiver);
                // now invoke on the serialized proxy. It's expected that the invocation will now use the
                // receiver that we added to the "current" EJB client context
                final String echoAfterSerialization = serializedProxy.echo(messageForSerializedProxy);
                Assert.assertEquals("Unexpected echo message received from serialized proxy of a bean", messageForSerializedProxy, echoAfterSerialization);
            } finally {
                // unregister the receiver
                EJBClientContext.requireCurrent().unregisterEJBReceiver(receiver);
            }
        } finally {
            // close the context after we are done
            context.close();
        }
    }

    private <T> T serializeDeserialize(T obj) throws Exception {
        // serialize
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();

        //de-serialize
        final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        final ObjectInputStream ois = new ObjectInputStream(bais);
        return (T) ois.readObject();

    }

    /**
     * Returns the properties that can be passed on to the constructor of the {@link InitialContext}
     * to create scoped EJB client contexts
     *
     * @return
     * @throws Exception
     */
    private Properties getEJBClientConfigurationProperties() throws Exception {
        final Properties ejbClientContextProps = new Properties();
        // Property to enable scoped EJB client context which will be tied to the JNDI context
        ejbClientContextProps.put("org.jboss.ejb.client.scoped.context", true);
        // Property which will handle the ejb: namespace during JNDI lookup
        ejbClientContextProps.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");

        final String connectionName = "foo-bar-connection";
        ejbClientContextProps.put("remote.connectionprovider.create.options.org.xnio.Options.SSL_ENABLED", "false");
        // add a property which lists the connections that we are configuring. In
        // this example, we are just configuring a single connection named "foo-bar-connection"
        ejbClientContextProps.put("remote.connections", connectionName);
        // add a property which points to the host server of the "foo-bar-connection"
        ejbClientContextProps.put("remote.connection." + connectionName + ".host", "localhost");
        // add a property which points to the port on which the server is listening for EJB invocations
        ejbClientContextProps.put("remote.connection." + connectionName + ".port", "6999");
        ejbClientContextProps.put("remote.connection." + connectionName + ".protocol", "remote");
        // since we are connecting to a dummy server, we use anonymous user
        ejbClientContextProps.put("remote.connection." + connectionName + ".connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS", "false");


        // since this is a dummy server we are connecting to, we don't need the rest of the properties
        // below. Connecting to a real AS7 will require the rest of the following properties

        // add the username and password properties which will be used to establish this connection
//        ejbClientContextProps.put("remote.connection." + connectionName + ".username", userName);
//        ejbClientContextProps.put("remote.connection." + connectionName + ".password", password);
        // disable "silent" auth, which gets triggered if the client is on the same machine as the server
        //ejbClientContextProps.put("remote.connection." + connectionName + ".connect.options.org.xnio.Options.SASL_DISALLOWED_MECHANISMS", "JBOSS-LOCAL-USER");

        return ejbClientContextProps;

    }

    /**
     * Creates and returns a EJB receiver which communicates with the dummy server started in this test
     *
     * @return
     * @throws Exception
     */
    private EJBReceiver createReceiver() throws Exception {
        final Endpoint endpoint = Remoting.createEndpoint("jndi-context-test-endpoint", OptionMap.EMPTY);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));


        // open a connection
        final IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://" + serverHost + ":" + serverPort), OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE), new AnonymousCallbackHandler());
        final Connection connection = get(futureConnection, 5, TimeUnit.SECONDS);
        return new RemotingConnectionEJBReceiver(connection, "remote");
    }
}
