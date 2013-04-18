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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

/**
 * A Channel receiver which manages the initial server initiated version handshake between the client
 * and the server
 * <p/>
 * User: Jaikiran Pai
 */
class VersionReceiver implements Channel.Receiver {

    private static final Logger logger = Logger.getLogger(VersionReceiver.class);

    private final String clientMarshallingStrategy;
    private final CountDownLatch latch;
    private Channel compatibleChannel;
    private boolean compatibilityFailed;
    private final SortedSet<Byte> legibleVersions = new TreeSet<Byte>();
    private volatile int negotiatedVersion = -1;

    /**
     * @param latch               The countdown latch which will be used to notify about a successful version handshake between
     *                            the client and the server
     * @param clientVersions      The EJB remoting protocol versions that the client can handle
     * @param marshallingStrategy The marshalling strategy which will be used by the client
     */
    VersionReceiver(final CountDownLatch latch, final byte[] clientVersions, final String marshallingStrategy) {
        for (final byte clientVersion : clientVersions) {
            legibleVersions.add(clientVersion);
        }
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
        this.negotiatedVersion = matchBestCompatibleVersion(serverVersion, serverMarshallerStrategies);
        if (this.negotiatedVersion == -1) {
            // Probably not a good idea to log the exact version of the server, so just print out a generic error message
            logger.error("EJB receiver cannot communicate with server, due to version incompatibility");
            this.compatibilityFailed = true;
            return;
        }

        try {
            // we have verified that the client can handle the version of the server. so send a version message to the server telling it
            // that the client is going to communicate with the server using a certain negotiated version.
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

    /**
     * Returns the protocol version that the client and server negotiated and decided to use for communication. Returns -1 if the negotiation hans't been
     * done or if the negotiation had failed. Else returns the <code>byte</code> value of the version.
     *
     * @return
     */
    int getNegotiatedProtocolVersion() {
        return negotiatedVersion;
    }

    private int matchBestCompatibleVersion(final byte serverVersion, final String[] serverMarshallingStrategies) {
        // make sure the server and client can handle a same marshaling strategy
        final Collection<String> serverSupportedStrategies = Arrays.asList(serverMarshallingStrategies);
        if (!serverSupportedStrategies.contains(clientMarshallingStrategy)) {
            logger.debug("Server doesn't support marshaling strategy: " + clientMarshallingStrategy);
            return -1;
        }
        // at this point, we know the server and client can handle a particular marshaling strategy. Now let's compare the versions.
        // see if the server version is one among the legible versions supported by the client
        if (legibleVersions.contains(serverVersion)) {
            // found the right version
            return serverVersion;
        }
        // the server version isn't among the versions that the client knows of. Now see if the server version is greater that all versions known to the client.
        // if yes, then the client should be able to talk to that higher version server.
        final byte highestKnownVersionOnClient = this.legibleVersions.last();
        if (highestKnownVersionOnClient < serverVersion) {
            // found a compatible (higher) version of server
            return serverVersion;
        }
        // no compatible version found
        return -1;
    }

    private void sendVersionMessage(final Channel channel) throws IOException {
        final MessageOutputStream channelOutputStream = channel.writeMessage();
        final DataOutputStream dataOutputStream = new DataOutputStream(channelOutputStream);
        try {
            // write the client version
            dataOutputStream.write(this.negotiatedVersion);
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
