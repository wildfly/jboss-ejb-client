/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client;

import org.jboss.ejb.client.proxy.EJBProxyInvocationHandler;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.sasl.JBossSaslProvider;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Security;
import java.util.Hashtable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * User: jpai
 */
public class EJBClient {

    /**
     * Logger
     */
    private static final Logger logger = Logger.getLogger(EJBClient.class);

    // TODO: Make it configurable
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private volatile Endpoint endpoint;

    private Registration connectionProviderRegistration;

    private volatile boolean channelOpened;

    // TODO: Externalize
    // Key to the EJB remote provider URL
    public static final String EJB_REMOTE_PROVIDER_URI = "org.jboss.ejb.remote.provider.uri";

    private final String remoteServerURI;

    {
        Security.addProvider(new JBossSaslProvider());
    }

    public EJBClient(final Hashtable<?, ?> environment) {
        if (environment == null) {
            throw new IllegalArgumentException("Environment cannot be null");
        }
        if (environment.get(EJB_REMOTE_PROVIDER_URI) == null) {
            throw new RuntimeException("Need to specify " + EJB_REMOTE_PROVIDER_URI + " while creating a EJBClient");
        }
        this.remoteServerURI = (String) environment.get(EJB_REMOTE_PROVIDER_URI);
    }

    /**
     * Creates and returns a proxy for a EJB identified by the <code>appName</code>, <code>moduleName</code>, <code>beanName</code>
     * and the <code>beanInterfaceType</code>
     *
     * @param appName           The application name of the deployment in which the EJB is deployed. This typically is the name of the enterprise
     *                          archive (without the .ear suffix) in which the EJB is deployed on the server. The application name of the deployment
     *                          is sometimes overridden through the use of application.xml. In such cases, the passed <code>appName</code> should match
     *                          that name.
     *                          <p/>
     *                          The <code>appName</code> passed can be null if the EJB is <i>not</i> deployed an enterprise archive (.ear)
     * @param moduleName        The module name of the deployment in which the EJB is deployed. This typically is the name of the jar file (without the
     *                          .jar suffix) or the name of the war file (without the .war suffix). The module name is allowed to be overridden through
     *                          the use of deployment descriptors, in which case the passed <code>moduleName</code> should match that name.
     *                          <p/>
     *                          <code>moduleName</code> cannot be null or an empty value
     * @param beanName          The name of the EJB for which the proxy is being created
     * @param beanInterfaceType The interface type exposed by the bean, for which we are creating the proxy. For example, if the bean exposes
     *                          remote business interface view, then the client can pass that as the <code>beanInterfaceType</code>. Same applies
     *                          for remote home interface.
     *                          <p/>
     *                          The <code>beanInterfaceType</code> cannot be null
     * @param <T>
     * @return
     * @throws IllegalArgumentException If the moduleName is null or empty
     * @throws IllegalArgumentException If the beanName is null or empty
     * @throws IllegalArgumentException If the beanInterfaceType is null
     * @throws IllegalArgumentException If the passed beanInterfaceType isn't a remote view of the bean
     * @throws IllegalArgumentException If the passed combination of appName, moduleName, beanName and beanInterfaceType cannot identify
     *                                  an EJB deployed on the server
     */
    public <T> T getProxy(final String appName, final String moduleName, final String beanName, final Class<T> beanInterfaceType) {
        if (!this.channelOpened) {
            try {
                this.connect();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (moduleName == null || moduleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name cannot be null or empty");
        }
        if (beanName == null || beanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Bean name cannot be null or empty");
        }
        if (beanInterfaceType == null) {
            throw new IllegalArgumentException("Bean interface type cannot be null");
        }
        // TODO: Implement remoting protocol
//        final byte[] resolutionRequest = EJBRemoteProtocol.generateResolutionRequest(appName, moduleName, beanName, beanInterfaceType);
//        this.sendData(resolutionRequest);
        if (beanInterfaceType == null) {
            throw new UnsupportedOperationException("Proxy generation with a specific bean interface type not yet implemented");
        }
        return (T) Proxy.newProxyInstance(beanInterfaceType.getClassLoader(), new Class<?>[]{beanInterfaceType}, new EJBProxyInvocationHandler());
    }

    public void createSession(final Object proxy) {

    }

    private void sendData(final byte[] data) {

    }

    private synchronized void connect() throws IOException, URISyntaxException, InterruptedException, ExecutionException {
        if (this.endpoint == null) {
            this.endpoint = Remoting.createEndpoint("endpoint", executor, OptionMap.EMPTY);
            final Xnio xnio = Xnio.getInstance();
            this.connectionProviderRegistration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(xnio), OptionMap.create(Options.SSL_ENABLED, false));
        }
        final OptionMap clientOptions = OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        final IoFuture<Connection> futureConnection = endpoint.connect(new URI(remoteServerURI), clientOptions, new EndpointAuthenticationCallbackHandler());
        final IoFuture.Status status = futureConnection.awaitInterruptibly(5, SECONDS);
        Connection connection = null;
        switch (status) {
            case CANCELLED:
                throw new CancellationException();
            case FAILED:
                throw new ExecutionException(futureConnection.getException());
            case DONE:
                connection = futureConnection.get();
                break;
            case WAITING:
                throw new RuntimeException("Timed out waiting to connect to EJB endpoint");
            default:
                throw new RuntimeException("Unknow return status: " + status + " during an attempt to connect to EJB endpoint");
        }
        logger.info("Connected to remote EJB endpoint " + connection);
        final Channel channel = this.openChannel(connection);
        channel.addCloseHandler(new ChannelCloseHandler());
        logger.info("Channel opened: " + channel);
        this.channelOpened = true;

    }

    private Channel openChannel(final Connection connection) throws InterruptedException, IOException, ExecutionException {
        final IoFuture<Channel> futureChannel = connection.openChannel("ejb3", OptionMap.EMPTY);
        final IoFuture.Status status = futureChannel.awaitInterruptibly(5, SECONDS);
        switch (status) {
            case CANCELLED:
                throw new CancellationException();
            case FAILED:
                throw new ExecutionException(futureChannel.getException());
            case DONE:
                return futureChannel.get();
            case WAITING:
                throw new RuntimeException("Timed out waiting to open a channel on EJB endpoint");
            default:
                throw new RuntimeException("Unknown status");
        }
    }

    private class ChannelCloseHandler implements CloseHandler<Channel> {

        @Override
        public void handleClose(Channel closed, IOException exception) {
            EJBClient.this.channelOpened = false;
        }
    }

    private void cleanup() throws IOException {
        if (endpoint != null) {
            endpoint.close();
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        this.cleanup();
        super.finalize();
    }
}
