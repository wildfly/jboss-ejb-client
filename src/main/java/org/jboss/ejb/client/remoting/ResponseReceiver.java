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

import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;
import org.xnio.IoUtils;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * User: jpai
 */
class ResponseReceiver implements Channel.Receiver {

    private static final Logger logger = Logger.getLogger(ResponseReceiver.class);


    private final RemotingConnectionEJBReceiver ejbReceiver;

    private final EJBReceiverContext ejbReceiverContext;

    ResponseReceiver(final RemotingConnectionEJBReceiver ejbReceiver, final EJBReceiverContext ejbReceiverContext) {
        this.ejbReceiver = ejbReceiver;
        this.ejbReceiverContext = ejbReceiverContext;
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
    public void handleMessage(Channel channel, MessageInputStream messageInputStream) {

        final DataInputStream inputStream = new DataInputStream(messageInputStream);
        ProtocolMessageHandler messageHandler = null;
        try {
            final byte header = inputStream.readByte();
            // TODO: Log at a lower level (once we have a bit of stability in the impl)
            logger.info("Received message with header 0x" + Integer.toHexString(header));
            messageHandler = this.ejbReceiver.getProtocolMessageHandler(this.ejbReceiverContext, header);
            if (messageHandler == null) {
                logger.warn("Unsupported message received with header 0x" + Integer.toHexString(header));
                return;
            }
            // let the message handler read the message
            messageHandler.readMessage(inputStream);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // receive next message
            channel.receiveMessage(this);
            IoUtils.safeClose(inputStream);
        }

        // Let the message handler process the message
        if (messageHandler != null) {
            messageHandler.processMessage();
        }
    }

}
