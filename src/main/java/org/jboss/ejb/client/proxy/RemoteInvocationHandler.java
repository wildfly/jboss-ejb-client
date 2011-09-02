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
package org.jboss.ejb.client.proxy;

import org.jboss.ejb.client.EndpointAuthenticationCallbackHandler;
import org.jboss.ejb.client.protocol.Attachment;
import org.jboss.ejb.client.protocol.InvocationRequest;
import org.jboss.ejb.client.protocol.Version0Protocol;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.sasl.JBossSaslProvider;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;


import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.Security;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.ejb.client.proxy.IoFutureHelper.get;
import static org.xnio.Options.SASL_POLICY_NOANONYMOUS;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class RemoteInvocationHandler implements InvocationHandler {

    private final Version0Protocol protocol;
    private final URI uri;
    private final String appName;
    private final String moduleName;
    private final String beanName;
    private final Class<?> view;
    private volatile Channel channel;
    private volatile Connection connection;
    private volatile short invocationId = 0;
    private final Endpoint endpoint;

    public RemoteInvocationHandler(final Endpoint endpoint, final URI uri, final String appName, final String moduleName, final String beanName, final Class<?> view) {
        this.uri = uri;
        this.endpoint = endpoint;
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.view = view;
//        config.setClassResolver(new SimpleClassResolver(view.getClassLoader()));
        this.protocol = new Version0Protocol();
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
                    // send the version greeting message to the channel
                    this.protocol.sendVersionGreeting(channel.writeMessage());
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
                    final IoFuture<Connection> futureConnection = this.endpoint.connect(uri, clientOptions, new EndpointAuthenticationCallbackHandler());
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
        final InvocationRequest invocationRequest = this.protocol.createInvocationRequest(this.invocationId++, this.appName,
                this.moduleName, this.beanName,
                this.view.getName(), method.getName(), this.toString(method.getParameterTypes()), args, attachments);

        this.protocol.writeInvocationRequest(getChannel().writeMessage(), invocationRequest);
        // For now we send directly onto the channel and wait for the reply
//        final ByteOutput output = Marshalling.createByteOutput(getChannel().writeMessage());
//        marshaller.start(output);
//        marshaller.writeObject(request);
//        //request.writeExternal(marshaller);
//        marshaller.finish();
//        marshaller.close();
//        output.close();
        Thread.sleep(10000l);
        return null;
    }

    private String[] toString(final Class[] classTypes) {
        final String[] types = new String[classTypes.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = classTypes[i].getName().toString();
        }
        return types;
    }
}
