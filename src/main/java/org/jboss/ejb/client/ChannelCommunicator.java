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

import org.jboss.ejb.client.protocol.InvocationRequest;
import org.jboss.ejb.client.protocol.InvocationResponse;
import org.jboss.ejb.client.protocol.Version0ProtocolHandler;
import org.jboss.ejb.client.proxy.IoFutureHelper;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.ejb.client.proxy.IoFutureHelper.get;
import static org.xnio.Options.SASL_POLICY_NOANONYMOUS;

/**
 * User: jpai
 */
public class ChannelCommunicator implements InvocationResponseManager {

    private volatile Channel channel;
    private volatile Connection connection;
    private final Endpoint endpoint;
    private final URI uri;
    private final Version0ProtocolHandler protocol;
    private final Map<Integer, FutureResult<InvocationResponse>> waitingInvocations = new ConcurrentHashMap<Integer, FutureResult<InvocationResponse>>();


    public ChannelCommunicator(final Endpoint endpoint, final URI uri, final ClassLoader cl) {
        this.uri = uri;
        this.endpoint = endpoint;
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        marshallingConfiguration.setClassResolver(new SimpleClassResolver(cl));
        this.protocol = new Version0ProtocolHandler(marshallingConfiguration);
    }

    public Future<InvocationResponse> invoke(final InvocationRequest invocationRequest) throws IOException {
        final FutureResult futureResult = new FutureResult<InvocationResponse>();
        final Future<InvocationResponse> result = IoFutureHelper.future(futureResult.getIoFuture());
        this.waitingInvocations.put(invocationRequest.getInvocationId(), futureResult);
        // send out the request
        this.protocol.writeInvocationRequest(getChannel().writeMessage(), invocationRequest);

        return result;
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
                            ChannelCommunicator.this.channel = null;
                        }
                    });
                    // send the version greeting message to the channel
                    this.protocol.sendVersionGreeting(channel.writeMessage());
                    // register for responses
                    this.channel.receiveMessage(new ResponseReceiver(this.protocol, this));
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
    public void onResponse(final InvocationResponse invocationResponse) {
        final int invocationId = invocationResponse.getInvocationId();
        final FutureResult<InvocationResponse> futureResult = this.waitingInvocations.remove(invocationId);
        if (futureResult != null) {
            futureResult.setResult(invocationResponse);
        }
    }
}
