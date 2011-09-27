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

package org.jboss.ejb.client.remoting;

import org.jboss.ejb.client.protocol.Attachment;
import org.jboss.ejb.client.protocol.MethodInvocationResponse;
import org.jboss.ejb.client.protocol.ProtocolHandler;
import org.jboss.ejb.client.protocol.ProtocolHandlerFactory;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
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
class ChannelCommunicator  {

    private volatile Channel channel;
    private volatile Connection connection;
    private final Endpoint endpoint;
    private final URI uri;
    private final ProtocolHandler protocolHandler;
    private final Map<Short, FutureResult<MethodInvocationResponse>> waitingInvocations = new ConcurrentHashMap<Short, FutureResult<MethodInvocationResponse>>();


    private final byte clientVersion = 0x00;

    private final String clientMarshallingStrategy = "java-serial";

    private byte serverVersion;

    private String[] serverMarshallingStrategies;

    private boolean serverInCompatible;

    private Object channelStartLock = new Object();

    ChannelCommunicator(final Endpoint endpoint, final URI uri, final ClassLoader cl) {
        this.uri = uri;
        this.endpoint = endpoint;
        this.protocolHandler = ProtocolHandlerFactory.getProtocolHandler(clientVersion, clientMarshallingStrategy);
    }

    public void start(long timeoutInMilliSeconds) throws IOException {
        this.openChannel();
        synchronized (this.channelStartLock) {
            try {
                this.channelStartLock.wait(timeoutInMilliSeconds);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        // make sure channel was opened and version message exchanged
        this.getChannel();
    }

    public Future<MethodInvocationResponse> invokeMethod(final short invocationId, final String appName,
                                                         final String moduleName, final String beanName, final String viewClassName,
                                                         final Method method, final Object[] methodParams,
                                                         final Attachment[] attachments) throws IOException {

        final FutureResult futureResult = new FutureResult<MethodInvocationResponse>();
        final Future<MethodInvocationResponse> result = IoUtils.getFuture(futureResult.getIoFuture());
        this.waitingInvocations.put(invocationId, futureResult);

        final DataOutputStream outputStream = new DataOutputStream(this.getChannel().writeMessage());
        try {
            this.protocolHandler.writeMethodInvocationRequest(outputStream, invocationId, appName, moduleName, beanName, viewClassName, method, methodParams, attachments);
        } finally {
            IoUtils.safeClose(outputStream);
        }

        return result;
    }

    private void openChannel() throws IOException {
        final Channel notYetReadyChannel = get(getConnection().openChannel("ejb3", OptionMap.EMPTY), 5, SECONDS);
        //notYetReadyChannel.receiveMessage(new ProtocolVersionChannelReceiver(this.clientVersion, this.clientMarshallingStrategy, this));
    }

    private synchronized Channel getChannel() throws IOException {
        if (this.channel == null) {
            if (this.serverInCompatible) {
                throw new IOException("Server at URI " + this.uri + " cannot handle client version "
                        + this.clientVersion + " and marshalling strategy " + this.clientMarshallingStrategy);
            }
            throw new IOException("Channel for URI " + this.uri + " not yet ready for communication");
        }
        return this.channel;
    }

    private Connection getConnection() throws IOException {
        // TODO: a connection per URI, not per proxy
        if (connection == null) {
            synchronized (this) {
                if (connection == null) {
                    final OptionMap clientOptions = OptionMap.create(SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
                    final IoFuture<Connection> futureConnection = this.endpoint.connect(uri, clientOptions,null);
                    this.connection = get(futureConnection, 5, SECONDS);
                }
            }
        }
        return connection;
    }

/*
    @Override
    public void onResponse(final MethodInvocationResponse invocationResponse) {
        final short invocationId = invocationResponse.getInvocationId();
        final FutureResult<MethodInvocationResponse> futureResult = this.waitingInvocations.remove(invocationId);
        if (futureResult != null) {
            futureResult.setResult(invocationResponse);
        }
    }

    @Override
    public void handleCompatibleChannel(final Channel channel, final byte serverVersion, final String[] serverMarshallingStrategies) {
        this.serverVersion = serverVersion;
        this.serverMarshallingStrategies = serverMarshallingStrategies;

        // send a version greeting from the client to the server
        DataOutputStream outputStream = null;
        try {
            outputStream = new DataOutputStream(channel.writeMessage());
            this.protocolHandler.writeVersionMessage(outputStream, this.clientMarshallingStrategy);
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to send version message from client to server at URI " + this.uri, ioe);
        } finally {
            IoUtils.safeClose(outputStream);
        }
        this.channel = channel;
        // notify of channel readiness
        this.notifyChannelVersionExchange();

        this.channel.addCloseHandler(new CloseHandler<Channel>() {
            @Override
            public void handleClose(Channel closed, IOException exception) {
                ChannelCommunicator.this.channel = null;
            }
        });
        // register for receiving messages from the server
        //this.channel.receiveMessage(new ResponseReceiver(this.protocolHandler, this));
    }
    @Override
    public void handleInCompatibleChannel(final Channel channel) {
        this.serverInCompatible = true;
        // notify that the channel was opened and version message exchanged
        this.notifyChannelVersionExchange();
    }
*/
    private void notifyChannelVersionExchange() {
        synchronized (this.channelStartLock) {
            this.channelStartLock.notifyAll();
        }
    }
}
