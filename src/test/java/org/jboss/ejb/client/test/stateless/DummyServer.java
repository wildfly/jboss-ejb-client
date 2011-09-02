/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client.test.stateless;

import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.marshalling.SimpleDataInput;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.jboss.sasl.JBossSaslProvider;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.jboss.ejb.client.protocol.InvocationRequest.INVOCATION_REQUEST_HEADER;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
class DummyServer {
    private static final MarshallerFactory MARSHALLER_FACTORY;
    static {
        Security.addProvider(new JBossSaslProvider());
        MARSHALLER_FACTORY = Marshalling.getProvidedMarshallerFactory("river");
    }

    private final MarshallingConfiguration config;

    class VersionReceiver extends AbstractReceiver {
        @Override
        public void handleMessage(Channel channel, MessageInputStream message) {
            final SimpleDataInput input = new SimpleDataInput(Marshalling.createByteInput(message));
            try {
                final byte version = input.readByte();
                input.close();
                switch(version) {
                    case 0x00:
                        channel.receiveMessage(new Version0Receiver());
                        break;
                    default:
                        channel.close();
                        break;
                }
            } catch (IOException e) {
                // log it
                e.printStackTrace();
                try {
                    channel.writeShutdown();
                } catch (IOException e1) {
                    // ignore
                }
            }
        }
    }

    class Version0Receiver extends AbstractReceiver {
        @Override
        public void handleMessage(Channel channel, MessageInputStream message) {
            try {
                int command = message.read();
                switch (command) {
                    case INVOCATION_REQUEST_HEADER:
                        break;
                }
            } catch (IOException e) {
                // log it
                e.printStackTrace();
                try {
                    channel.writeShutdown();
                } catch (IOException e1) {
                    // ignore
                }
            }
        }
    }

    DummyServer() {
        this.config = new MarshallingConfiguration();
        config.setVersion(2);
        // TODO: need to use the EJB bean class loader, this depends on the packet received
        config.setClassResolver(new SimpleClassResolver(DummyServer.class.getClassLoader()));
    }

    void start() throws IOException {
        final ExecutorService serverExecutor = Executors.newFixedThreadPool(4);
        final OptionMap options = OptionMap.EMPTY;
        final Endpoint endpoint = Remoting.createEndpoint("endpoint", serverExecutor, options);
        final Xnio xnio = Xnio.getInstance();
        final Registration registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(xnio), OptionMap.create(Options.SSL_ENABLED, false));
        final NetworkServerProvider serverProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SocketAddress bindAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 6999);
        final SimpleServerAuthenticationProvider authenticationProvider = new SimpleServerAuthenticationProvider();
        authenticationProvider.addUser("test", "localhost.localdomain", "test".toCharArray());
        final OptionMap serverOptions = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("ANONYMOUS"), Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        final AcceptingChannel<? extends ConnectedStreamChannel> server = serverProvider.createServer(bindAddress, serverOptions, authenticationProvider);

        endpoint.registerService("ejb3", new OpenListener() {
            @Override
            public void channelOpened(Channel channel) {
                channel.addCloseHandler(new CloseHandler<Channel>() {
                    @Override
                    public void handleClose(Channel closed, IOException exception) {
                        System.out.println("Bye " + closed);
                    }
                });
                Channel.Receiver handler = new VersionReceiver();
                channel.receiveMessage(handler);
            }

            @Override
            public void registrationTerminated() {
                throw new RuntimeException("NYI: .registrationTerminated");
            }
        }, OptionMap.EMPTY);
    }
}
