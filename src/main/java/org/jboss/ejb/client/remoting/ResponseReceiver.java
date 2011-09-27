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

import org.jboss.ejb.client.protocol.MessageType;
import org.jboss.ejb.client.protocol.MethodInvocationResponse;
import org.jboss.ejb.client.protocol.ProtocolHandler;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * User: jpai
 */
class ResponseReceiver implements Channel.Receiver {

    private static final Logger logger = Logger.getLogger(ResponseReceiver.class);


    private final ProtocolHandler protocolHandler;

    public ResponseReceiver(final ProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
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
        try {
            MessageType messageType = this.protocolHandler.getMessageType(inputStream);
            logger.info("Received response with header 0x" + messageType.getHeader());
            switch (messageType) {
                case INVOCATION_RESPONSE:
                    final MethodInvocationResponse invocationResponse = this.protocolHandler.readMethodInvocationResponse(inputStream);
                    logger.info("Response for invocation id " + invocationResponse.getInvocationId() + " received");
                    break;
                    default:
                        throw new RuntimeException("Unsupported response header 0x" + Integer.toHexString(messageType.getHeader()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
