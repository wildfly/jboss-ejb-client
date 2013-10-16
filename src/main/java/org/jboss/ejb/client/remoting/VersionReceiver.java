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

import static java.lang.Math.min;
import static org.xnio.IoUtils.safeClose;

import org.jboss.ejb.client.Logs;
import org.jboss.logging.Logger;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.SimpleDataInput;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * A Channel receiver which manages the initial server initiated version handshake between the client
 * and the server
 * <p/>
 * User: Jaikiran Pai
 */
class VersionReceiver implements Channel.Receiver {

    private static final Logger logger = Logger.getLogger(VersionReceiver.class);

    private final CountDownLatch latch;
    private Channel compatibleChannel;
    private boolean compatibilityFailed;
    private volatile int negotiatedVersion = -1;

    /**
     * @param latch               The countdown latch which will be used to notify about a successful version handshake between
     *                            the client and the server
     *
     */
    VersionReceiver(final CountDownLatch latch) {
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
        int serverVersion;
        Set<String> serverMarshallerStrategies;
        try {
            serverVersion = simpleDataInput.readUnsignedByte();
            final int serverMarshallerCount = PackedInteger.readPackedInteger(simpleDataInput);
            if (serverMarshallerCount <= 0) {
                throw new RuntimeException("Client cannot communicate with the server since no marshalling strategy has been " +
                        "configured on server side");
            }
            serverMarshallerStrategies = new HashSet<String>(serverMarshallerCount, 0.5f);
            for (int i = 0; i < serverMarshallerCount; i++) {
                serverMarshallerStrategies.add(simpleDataInput.readUTF());
            }
            Logs.REMOTING.receivedServerVersionAndMarshallingStrategies(serverVersion, serverMarshallerStrategies);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // make sure the server and client can handle a same marshaling strategy
        if (! serverMarshallerStrategies.contains("river")) {
            logger.error("Server doesn't support marshaling strategy: river");
            this.compatibilityFailed = true;
            return;
        }

        this.negotiatedVersion = min(serverVersion, 2);

        try {
            // we have verified that the client can handle the version of the server. so send a version message to the server telling it
            // that the client is going to communicate with the server using a certain negotiated version.
            final MessageOutputStream channelOutputStream = channel.writeMessage();
            try {
                final DataOutputStream dataOutputStream = new DataOutputStream(channelOutputStream);
                try {
                    // write the client version
                    dataOutputStream.write(this.negotiatedVersion);
                    // write client marshalling strategy - always river
                    dataOutputStream.writeUTF("river");
                    dataOutputStream.close();
                } finally {
                    safeClose(dataOutputStream);
                }
                channelOutputStream.close();
            } finally {
                safeClose(channelOutputStream);
            }
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

    /**
     * Returns true if the version handshake between the server and client failed due to server and client
     * incompatibility. Else returns false
     */
    boolean failedCompatibility() {
        return compatibilityFailed;
    }

}
