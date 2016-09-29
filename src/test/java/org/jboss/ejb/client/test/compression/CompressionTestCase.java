package org.jboss.ejb.client.test.compression;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import junit.framework.Assert;
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
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that the {@link org.jboss.ejb.client.annotation.CompressionHint} on view classes and the view methods is taken into account during EJB invocations
 *
 * @author: Jaikiran Pai
 */
public class CompressionTestCase {

    private static final Logger logger = Logger.getLogger(CompressionTestCase.class);

    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private DummyServer server;
    private boolean serverStarted;

    private ContextSelector<EJBClientContext> previousSelector;

    @Before
    public void beforeTest() throws IOException {
        server = new DummyServer("localhost", 6999);
        server.start();
        serverStarted = true;

        final CompressableDataBean bean = new CompressableDataBean();
        // deploy
        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, CompressableDataBean.class.getSimpleName(), bean);

        // setup EJB client context with both the servers
        final String clientPropsName = "data-compression-jboss-ejb-client.properties";
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
        if (serverStarted) {
            try {
                this.server.stop();
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            }
        }
        if (this.previousSelector != null) {
            EJBClientContext.setSelector(previousSelector);
        }
    }

    @Test
    public void testDataCompressionEnabled() {
        // set the system property which enables annotation scan on the client view
        System.setProperty("org.jboss.ejb.client.view.annotation.scan.enabled", "true");
        try {
            // create a proxy for invocation
            final StatelessEJBLocator<CompressableDataRemoteView> statelessEJBLocator = new StatelessEJBLocator<CompressableDataRemoteView>(CompressableDataRemoteView.class, APP_NAME, MODULE_NAME, CompressableDataBean.class.getSimpleName(), "");
            final CompressableDataRemoteView bean = EJBClient.createProxy(statelessEJBLocator);
            final String message = "some message";

            // no request or response compression
            final String echoWithhNoCompress = bean.echoWithNoCompress(message);
            Assert.assertEquals("Unexpected response for invocation with no request or response compression", message, echoWithhNoCompress);

            // only request compression
            final String echoWithRequestCompressed = bean.echoWithRequestCompress(message);
            Assert.assertEquals("Unexpected response for invocation with only request compressed", DummyServer.REQUEST_WAS_COMPRESSED + " " + message, echoWithRequestCompressed);

            // only response compressed
            final String echoWithResponseCompressed = bean.echoWithResponseCompress(message);
            Assert.assertEquals("Unexpected response for invocation with only response compressed", DummyServer.RESPONSE_WAS_COMPRESSED + " " + message, echoWithResponseCompressed);

            // both request and response compressed
            final String echoWithRequestAndResponseCompressed = bean.echoWithRequestAndResponseCompress(message);
            Assert.assertEquals("Unexpected response for invocation with both request and response compressed", DummyServer.RESPONSE_WAS_COMPRESSED + " " + DummyServer.REQUEST_WAS_COMPRESSED + " " + message, echoWithRequestAndResponseCompressed);


        } finally {
            // remove the system property which enables annotation scan on the client view
            System.getProperties().remove("org.jboss.ejb.client.view.annotation.scan.enabled");
        }

    }

    @Test
    public void testDataCompressionImplicitlyDisabled() {
        // create a proxy for invocation
        final StatelessEJBLocator<CompressableDataRemoteView> statelessEJBLocator = new StatelessEJBLocator<CompressableDataRemoteView>(CompressableDataRemoteView.class, APP_NAME, MODULE_NAME, CompressableDataBean.class.getSimpleName(), "");
        final CompressableDataRemoteView bean = EJBClient.createProxy(statelessEJBLocator);
        final String message = "some message";

        // no request or response compression
        final String echoWithhNoCompress = bean.echoWithNoCompress(message);
        Assert.assertEquals("Unexpected response for invocation with compression disabled", message, echoWithhNoCompress);

        // only request compression - No compression expected since annotation scanning isn't enabled
        final String echoWithRequestCompressed = bean.echoWithRequestCompress(message);
        Assert.assertEquals("Unexpected response for invocation with compression disabled", message, echoWithRequestCompressed);

        // only response compressed - No compression expected since annotation scanning isn't enabled
        final String echoWithResponseCompressed = bean.echoWithResponseCompress(message);
        Assert.assertEquals("Unexpected response for invocation with compression disabled", message, echoWithResponseCompressed);

        // both request and response compressed - No compression expected since annotation scanning isn't enabled
        final String echoWithRequestAndResponseCompressed = bean.echoWithRequestAndResponseCompress(message);
        Assert.assertEquals("Unexpected response for invocation with compression disabled", message, echoWithRequestAndResponseCompressed);

    }

    @Test
    public void testDataCompressionExplicitlyDisabled() {
        // set the system property which disables annotation scan on the client view
        System.setProperty("org.jboss.ejb.client.view.annotation.scan.enabled", "false");
        try {
            // create a proxy for invocation
            final StatelessEJBLocator<CompressableDataRemoteView> statelessEJBLocator = new StatelessEJBLocator<CompressableDataRemoteView>(CompressableDataRemoteView.class, APP_NAME, MODULE_NAME, CompressableDataBean.class.getSimpleName(), "");
            final CompressableDataRemoteView bean = EJBClient.createProxy(statelessEJBLocator);
            final String message = "some message";

            // no request or response compression
            final String echoWithhNoCompress = bean.echoWithNoCompress(message);
            Assert.assertEquals("Unexpected response for invocation with compression disabled", message, echoWithhNoCompress);

            // only request compression - No compression expected since annotation scanning isn't enabled
            final String echoWithRequestCompressed = bean.echoWithRequestCompress(message);
            Assert.assertEquals("Unexpected response for invocation with compression disabled", message, echoWithRequestCompressed);

            // only response compressed - No compression expected since annotation scanning isn't enabled
            final String echoWithResponseCompressed = bean.echoWithResponseCompress(message);
            Assert.assertEquals("Unexpected response for invocation with compression disabled", message, echoWithResponseCompressed);

            // both request and response compressed - No compression expected since annotation scanning isn't enabled
            final String echoWithRequestAndResponseCompressed = bean.echoWithRequestAndResponseCompress(message);
            Assert.assertEquals("Unexpected response for invocation with compression disabled", message, echoWithRequestAndResponseCompressed);
        } finally {
            // remove the system property which enables annotation scan on the client view
            System.getProperties().remove("org.jboss.ejb.client.view.annotation.scan.enabled");
        }

    }

    @Test
    public void testRequestCompressionOnView() {
        // set the system property which enables annotation scan on the client view
        System.setProperty("org.jboss.ejb.client.view.annotation.scan.enabled", "true");
        try {
            // create a proxy for invocation
            final StatelessEJBLocator<ClassLevelRequestCompressionRemoteView> statelessEJBLocator = new StatelessEJBLocator<ClassLevelRequestCompressionRemoteView>(ClassLevelRequestCompressionRemoteView.class, APP_NAME, MODULE_NAME, CompressableDataBean.class.getSimpleName(), "");
            final ClassLevelRequestCompressionRemoteView bean = EJBClient.createProxy(statelessEJBLocator);
            final String message = "some message";

            // only request compression
            final String echoWithRequestCompressed = bean.echo(message);
            Assert.assertEquals("Unexpected response for invocation with only request compressed", DummyServer.REQUEST_WAS_COMPRESSED + " " + message, echoWithRequestCompressed);

        } finally {
            // remove the system property which enables annotation scan on the client view
            System.getProperties().remove("org.jboss.ejb.client.view.annotation.scan.enabled");
        }

    }

    @Test
    public void testResponseCompressionOnView() {
        // set the system property which enables annotation scan on the client view
        System.setProperty("org.jboss.ejb.client.view.annotation.scan.enabled", "true");
        try {
            // create a proxy for invocation
            final StatelessEJBLocator<ClassLevelResponseCompressionRemoteView> statelessEJBLocator = new StatelessEJBLocator<ClassLevelResponseCompressionRemoteView>(ClassLevelResponseCompressionRemoteView.class, APP_NAME, MODULE_NAME, CompressableDataBean.class.getSimpleName(), "");
            final ClassLevelResponseCompressionRemoteView bean = EJBClient.createProxy(statelessEJBLocator);
            final String message = "some message";

            // only response compression
            final String echoWithResponseCompressed = bean.echo(message);
            Assert.assertEquals("Unexpected response for invocation with only response compressed", DummyServer.RESPONSE_WAS_COMPRESSED + " " + message, echoWithResponseCompressed);

        } finally {
            // remove the system property which enables annotation scan on the client view
            System.getProperties().remove("org.jboss.ejb.client.view.annotation.scan.enabled");
        }

    }

    @Test
    public void testRequestAndResponseCompressionOnView() {
        // set the system property which enables annotation scan on the client view
        System.setProperty("org.jboss.ejb.client.view.annotation.scan.enabled", "true");
        try {
            // create a proxy for invocation
            final StatelessEJBLocator<ClassLevelRequestAndResponseCompressionRemoteView> statelessEJBLocator = new StatelessEJBLocator<ClassLevelRequestAndResponseCompressionRemoteView>(ClassLevelRequestAndResponseCompressionRemoteView.class, APP_NAME, MODULE_NAME, CompressableDataBean.class.getSimpleName(), "");
            final ClassLevelRequestAndResponseCompressionRemoteView bean = EJBClient.createProxy(statelessEJBLocator);
            final String message = "some message";

            // request and response compression
            final String echoWithRequestAndResponseCompressed = bean.echo(message);
            Assert.assertEquals("Unexpected response for invocation with both request and response compressed", DummyServer.RESPONSE_WAS_COMPRESSED + " " + DummyServer.REQUEST_WAS_COMPRESSED + " " + message, echoWithRequestAndResponseCompressed);

        } finally {
            // remove the system property which enables annotation scan on the client view
            System.getProperties().remove("org.jboss.ejb.client.view.annotation.scan.enabled");
        }

    }

    @Test
    public void testMethodOverrideDataCompression() {
        // set the system property which enables annotation scan on the client view
        System.setProperty("org.jboss.ejb.client.view.annotation.scan.enabled", "true");
        try {
            // create a proxy for invocation
            final StatelessEJBLocator<MethodOverrideDataCompressionRemoteView> statelessEJBLocator = new StatelessEJBLocator<MethodOverrideDataCompressionRemoteView>(MethodOverrideDataCompressionRemoteView.class, APP_NAME, MODULE_NAME, CompressableDataBean.class.getSimpleName(), "");
            final MethodOverrideDataCompressionRemoteView bean = EJBClient.createProxy(statelessEJBLocator);
            final String message = "some message";

            // only request compression
            final String echoWithRequestCompressed = bean.echoWithRequestCompress(message);
            Assert.assertEquals("Unexpected response for invocation with only request compressed", DummyServer.REQUEST_WAS_COMPRESSED + " " + message, echoWithRequestCompressed);

            // only response compressed
            final String echoWithResponseCompressed = bean.echoWithResponseCompress(message);
            Assert.assertEquals("Unexpected response for invocation with only response compressed", DummyServer.RESPONSE_WAS_COMPRESSED + " " + message, echoWithResponseCompressed);

            // both request and response compressed based on the annotation at the view class level
            final String echoWithRequestAndResponseCompressed = bean.echoWithNoExplicitDataCompressionHintOnMethod(message);
            Assert.assertEquals("Unexpected response for invocation with both request and response compressed", DummyServer.RESPONSE_WAS_COMPRESSED + " " + DummyServer.REQUEST_WAS_COMPRESSED + " " + message, echoWithRequestAndResponseCompressed);

        } finally {
            // remove the system property which enables annotation scan on the client view
            System.getProperties().remove("org.jboss.ejb.client.view.annotation.scan.enabled");
        }
    }
}
