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

import org.jboss.ejb.client.protocol.PackedInteger;
import org.jboss.logging.Logger;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.SimpleDataInput;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * User: jpai
 */
public class ProtocolVersionChannelReceiver implements Channel.Receiver {

    private static final Logger logger = Logger.getLogger(ProtocolVersionChannelReceiver.class);

    private final byte clientVersion;

    private final String clientMarshallingStrategy;

    //private final ProtocolVersionCompatibilityListener versionCompatibilityListener;

    public ProtocolVersionChannelReceiver(final byte clientVersion, final String clientMarshallingStrategy) {
        this.clientVersion = clientVersion;
        this.clientMarshallingStrategy = clientMarshallingStrategy;
    }

    @Override
    public void handleError(Channel channel, IOException error) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleEnd(Channel channel) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleMessage(Channel channel, MessageInputStream message) {
        final SimpleDataInput simpleDataInput = new SimpleDataInput(Marshalling.createByteInput(message));
        try {
            final byte serverVersion = simpleDataInput.readByte();
            final int serverMarshallerCount = PackedInteger.readPackedInteger(simpleDataInput);
            if (serverMarshallerCount <= 0) {
                // TODO: Handle this
            }
            final String[] serverMarshallerStrategies = new String[serverMarshallerCount];
            logger.info("Received server version " + serverVersion + " and marshalling strategies " + serverMarshallerStrategies);

            for (int i = 0; i < serverMarshallerCount; i++) {
                serverMarshallerStrategies[i] = simpleDataInput.readUTF();
            }/*
            if (this.checkCompatibility(serverVersion, serverMarshallerStrategies)) {
                if (this.versionCompatibilityListener != null) {
                    this.versionCompatibilityListener.handleCompatibleChannel(channel, serverVersion, serverMarshallerStrategies);
                }
            } else {
                if (this.versionCompatibilityListener != null) {
                    this.versionCompatibilityListener.handleInCompatibleChannel(channel);
                }
            }*/
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean checkCompatibility(final byte serverVersion, final String[] serverMarshallingStrategies) {
        if (serverVersion < this.clientVersion) {
            return false;
        }
        final Collection<String> supportedStrategies = Arrays.asList(serverMarshallingStrategies);
        if (!supportedStrategies.contains(this.clientMarshallingStrategy)) {
            return false;
        }
        return true;
    }
}
