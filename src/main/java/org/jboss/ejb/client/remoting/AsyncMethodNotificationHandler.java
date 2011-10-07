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

import org.jboss.remoting3.MessageInputStream;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * User: jpai
 */
class AsyncMethodNotificationHandler extends ProtocolMessageHandler {

    private final ChannelAssociation channelAssociation;

    AsyncMethodNotificationHandler(final ChannelAssociation channelAssociation) {
        this.channelAssociation = channelAssociation;
    }

    @Override
    protected void processMessage(MessageInputStream messageInputStream) throws IOException {
        if (messageInputStream == null) {
            throw new IllegalArgumentException("Cannot read from null stream");
        }
        final DataInputStream input = new DataInputStream(messageInputStream);
        short invocationId;
        try {
            // read the invocation id
            invocationId = input.readShort();
        } finally {
            input.close();
        }
        // let the channel association know that the method invocation with the invocationId was a
        // async method
        this.channelAssociation.handleAsyncMethodNotification(invocationId);
    }
}
