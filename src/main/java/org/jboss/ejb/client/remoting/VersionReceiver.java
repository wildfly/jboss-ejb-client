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

import org.jboss.ejb.client.Logs;
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
import java.util.concurrent.CountDownLatch;

/**
 * A Channel receiver which manages the initial server initiated version handshake between the client
 * and the server
 * <p/>
 * User: Jaikiran Pai
 */
class VersionReceiver implements Channel.Receiver {

    private static final Logger logger = Logger.getLogger(VersionReceiver.class);

    private final byte clientVersion;
    private final String clientMarshallingStrategy;
    private final CountDownLatch latch;
    private Channel compatibleChannel;
    private boolean compatibilityFailed;

    /**
     * @param latch               The countdown latch which will be used to notify about a successful version handshake between
     *                            the client and the server
     * @param clientVersion       The EJB remoting protocol version of the client
     * @param marshallingStrategy The marshalling strategy which will be used by the client
     */
    VersionReceiver(final CountDownLatch latch, final byte clientVersion, final String marshallingStrategy) {
        this.clientVersion = clientVersion;
        this.clientMarshallingStrategy = marshallingStrategy;
        this.latch = latch;
        this.compatibilityFailed = false;
    }

    @Override
    public void handleError(final Channel channel, final IOException error) {
        logger.error("Error on channel " + channel, error);
        try {
            channel.close();
        } catch (IOException ioe) {
            // ignore
        }
    }

    @Override
    public void handleEnd(final Channel channel) {
        Logs.REMOTING.channelCanNoLongerProcessMessages(channel);
        try {
            channel.close();
        } catch (IOException ioe) {
            // ignore
        }
    }

    @Override
    public void handleMessage(final Channel channel, final MessageInputStream message) {
        final SimpleDataInput simpleDataInput = new SimpleDataInput(Marshalling.createByteInput(message));
        byte serverVersion;
        String[] serverMarshallerStrategies;
        try {
            serverVersion = simpleDataInput.readByte();
            final int serverMarshallerCount = PackedInteger.readPackedInteger(simpleDataInput);
            if (serverMarshallerCount <= 0) {
                throw new RuntimeException("Client cannot communicate with the server since no marshalling strategy has been " +
                        "configured on server side");
            }
            serverMarshallerStrategies = new String[serverMarshallerCount];
            for (int i = 0; i < serverMarshallerCount; i++) {
                serverMarshallerStrategies[i] = simpleDataInput.readUTF();
            }
            Logs.REMOTING.receivedServerVersionAndMarshallingStrategies(String.valueOf(serverVersion), Arrays.toString(serverMarshallerStrategies));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!this.checkCompatibility(serverVersion, serverMarshallerStrategies)) {
            // Probably not a good idea to log the exact version of the server, so just print out a generic error message
            logger.error("EJB receiver cannot communicate with server, due to version incompatibility");
            this.compatibilityFailed = true;
            return;
        }

        try {
            // send a version message to the server
            this.sendVersionMessage(channel);
            // we had a successful version handshake with the server and our job is done, let the
            // EJBReceiver take it from here
            this.compatibleChannel = channel;
            this.latch.countDown();

        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }


    }

    Channel getCompatibleChannel() {
        return this.compatibleChannel;
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

    /**
     * Returns true if the version handshake between the server and client failed due to server and client
     * incompatibility. Else returns false
     */
    boolean failedCompatibility() {
        return compatibilityFailed;
    }

}
