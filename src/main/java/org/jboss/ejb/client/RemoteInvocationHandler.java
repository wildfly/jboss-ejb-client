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
package org.jboss.ejb.client;

import org.jboss.ejb.client.protocol.Attachment;
import org.jboss.ejb.client.protocol.InvocationRequest;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.marshalling.SimpleDataOutput;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URI;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.ejb.client.EJBClient.getEndpoint;
import static org.jboss.ejb.client.IoFutureHelper.get;
import static org.xnio.Options.SASL_POLICY_NOANONYMOUS;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
class RemoteInvocationHandler implements InvocationHandler {
    private static final MarshallerFactory MARSHALLER_FACTORY;
    static {
        MARSHALLER_FACTORY = Marshalling.getProvidedMarshallerFactory("river");
    }

    private final MarshallingConfiguration config;
    private final URI uri;
    private final String fqBeanName;
    private final Class<?> view;
    private volatile Channel channel;
    private volatile Connection connection;
    private volatile short invocationId = 0;

    RemoteInvocationHandler(final URI uri, final String fqBeanName, final Class<?> view) {
        this.uri = uri;
        this.fqBeanName = fqBeanName;
        this.view = view;
        this.config = new MarshallingConfiguration();
        config.setVersion(2);
        config.setClassResolver(new SimpleClassResolver(view.getClassLoader()));
    }

    private Channel getChannel() throws IOException {
        // TODO: a channel per URI, not per proxy
        if (channel == null) {
            synchronized (this) {
                if (channel == null) {
                    channel = get(getConnection().openChannel("ejb3", OptionMap.EMPTY), 5, SECONDS);
                    channel.addCloseHandler(new CloseHandler<Channel>() {
                        @Override
                        public void handleClose(Channel closed, IOException exception) {
                            RemoteInvocationHandler.this.channel = null;
                        }
                    });
                    final SimpleDataOutput output = new SimpleDataOutput(Marshalling.createByteOutput(channel.writeMessage()));
                    output.writeByte(0x00); // test version
                    output.close();
                }
            }
        }
        return channel;
    }

    private Connection getConnection() throws IOException {
        // TODO: a connection per URI, not per proxy
        if (connection == null) {
            synchronized (this) {
                if (connection == null) {
                    final OptionMap clientOptions = OptionMap.create(SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
                    final IoFuture<Connection> futureConnection = getEndpoint().connect(uri, clientOptions, new EndpointAuthenticationCallbackHandler());
                    this.connection = get(futureConnection, 5, SECONDS);
                }
            }
        }
        return connection;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // TODO: construct a packet and send it to the channel manager, then wait for the channel manager to reply
        final Attachment[] attachments = null;
        final InvocationRequest request = new InvocationRequest(invocationId++ & 0xFFFF, fqBeanName, view.getName(), method.getName(), args, attachments);

        final Marshaller marshaller = MARSHALLER_FACTORY.createMarshaller(config);

        // For now we send directly onto the channel and wait for the reply
        final ByteOutput output = Marshalling.createByteOutput(getChannel().writeMessage());
        marshaller.start(output);
        request.writeExternal(marshaller);
        marshaller.finish();
        marshaller.close();
        output.close();

        return null;
    }
}
