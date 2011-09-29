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
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: jpai
 */
class ChannelAssociation {

    private static final Logger logger = Logger.getLogger(ChannelAssociation.class);

    private final RemotingConnectionEJBReceiver ejbReceiver;

    private final EJBReceiverContext ejbReceiverContext;

    private final Channel channel;

    private final byte protocolVersion;

    private final String marshallingType;

    private final AtomicInteger nextInvocationId = new AtomicInteger(0);

    private Map<Short, EJBReceiverInvocationContext> waitingInvocations = Collections.synchronizedMap(new HashMap<Short, EJBReceiverInvocationContext>());

    ChannelAssociation(final RemotingConnectionEJBReceiver ejbReceiver, final EJBReceiverContext ejbReceiverContext,
                       final Channel channel, final byte protocolVersion, final String marshallingType) {
        this.ejbReceiver = ejbReceiver;
        this.ejbReceiverContext = ejbReceiverContext;
        this.channel = channel;
        this.protocolVersion = protocolVersion;
        this.marshallingType = marshallingType;
        // register a receiver for receiving messages on the channel
        this.channel.receiveMessage(new ResponseReceiver());
    }

    Channel getChannel() {
        return this.channel;
    }

    short getNextInvocationId() {
        return (short) nextInvocationId.getAndIncrement();
    }

    void receiveResponse(final short invocationId, final EJBReceiverInvocationContext ejbReceiverInvocationContext) {
        this.waitingInvocations.put(invocationId, ejbReceiverInvocationContext);

    }

    EJBReceiverInvocationContext getEJBReceiverInvocationContext(short invocationId) {
        return this.waitingInvocations.get(invocationId);
    }

    private ProtocolMessageHandler getProtocolMessageHandler(final byte header) {
        switch (header) {
            case 0x08:
                return new ModuleAvailabilityMessageHandler(this.ejbReceiver);
            case 0x05:
                return new MethodInvocationResponseHandler(this, this.marshallingType);
            default:
                return null;
        }
    }


    private class ResponseReceiver implements Channel.Receiver {

        @Override
        public void handleError(Channel channel, IOException error) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void handleEnd(Channel channel) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void handleMessage(Channel channel, MessageInputStream messageInputStream) {

            try {
                final int header = messageInputStream.read();
                // TODO: Log at a lower level (once we have a bit of stability in the impl)
                logger.info("Received message with header 0x" + Integer.toHexString(header));
                final ProtocolMessageHandler messageHandler = ChannelAssociation.this.getProtocolMessageHandler((byte) header);
                if (messageHandler == null) {
                    logger.warn("Unsupported message received with header 0x" + Integer.toHexString(header));
                    return;
                }
                messageHandler.processMessage(messageInputStream);

            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                // receive next message
                channel.receiveMessage(this);
            }
        }

    }
}
