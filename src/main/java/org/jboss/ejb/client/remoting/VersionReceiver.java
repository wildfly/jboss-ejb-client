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

package org.jboss.ejb.client.remoting;

import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.logging.Logger;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.SimpleDataInput;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * User: jpai
 */
class VersionReceiver implements Channel.Receiver {

    private static final Logger logger = Logger.getLogger(VersionReceiver.class);

    private final EJBReceiverContext receiverContext;
    private final RemotingConnectionEJBReceiver ejbReceiver;
    private final byte clientVersion;
    private final String clientMarshallingStrategy;

    VersionReceiver(final RemotingConnectionEJBReceiver ejbReceiver, final EJBReceiverContext receiverContext,
                    final byte clientVersion, final String marshallingStrategy) {
        this.ejbReceiver = ejbReceiver;
        this.receiverContext = receiverContext;
        this.clientVersion = clientVersion;
        this.clientMarshallingStrategy = marshallingStrategy;
    }

    public void handleError(final Channel channel, final IOException error) {
    }

    public void handleEnd(final Channel channel) {
    }

    public void handleMessage(final Channel channel, final MessageInputStream message) {
        // TODO: handle incoming greeting, send our own, set up the connection state,
        // and query the module list
        final SimpleDataInput simpleDataInput = new SimpleDataInput(Marshalling.createByteInput(message));
        byte serverVersion;
        String[] serverMarshallerStrategies;
        try {
            serverVersion = simpleDataInput.readByte();
            final int serverMarshallerCount = PackedInteger.readPackedInteger(simpleDataInput);
            if (serverMarshallerCount <= 0) {
                // TODO: Handle this
            }
            serverMarshallerStrategies = new String[serverMarshallerCount];
            logger.info("Received server version " + serverVersion + " and marshalling strategies " + serverMarshallerStrategies);

            for (int i = 0; i < serverMarshallerCount; i++) {
                serverMarshallerStrategies[i] = simpleDataInput.readUTF();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!this.checkCompatibility(serverVersion, serverMarshallerStrategies)) {
            logger.error("EJB receiver cannot communicate with server, due to version incompatibility");
            // close the context
            this.receiverContext.close();
            return;
        }

        try {
            // send a version message to the server
            this.sendVersionMessage(channel);
            // we had a successful version handshake with the server and our job is done, let the
            // EJBReceiver take it from here
            this.ejbReceiver.onSuccessfulVersionHandshake(this.receiverContext, channel);

        } catch (IOException ioe) {
            // TODO: re-evaluate
            throw new RuntimeException(ioe);
        }


    }

    private boolean checkCompatibility(final byte serverVersion, final String[] serverMarshallingStrategies) {
        if (serverVersion < clientVersion) {
            return false;
        }
        final Collection<String> supportedStrategies = Arrays.asList(serverMarshallingStrategies);
        if (!supportedStrategies.contains(clientMarshallingStrategy)) {
            return false;
        }
        return true;
    }

    private void sendVersionMessage(final Channel channel) throws IOException {
        final MessageOutputStream channelOutputStream = channel.writeMessage();
        final DataOutputStream dataOutputStream = new DataOutputStream(channelOutputStream);
        try {
            // write the client version
            dataOutputStream.write(this.clientVersion);
            // write client marshalling strategy
            dataOutputStream.writeUTF(this.clientMarshallingStrategy);
        } finally {
            dataOutputStream.close();
            channelOutputStream.close();
        }
    }

}
