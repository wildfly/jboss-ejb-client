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

import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.remoting3.MessageInputStream;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * User: jpai
 */
class GenericInvocationSuccessResponseHandler extends ProtocolMessageHandler {

    private final ChannelAssociation channelAssociation;

    GenericInvocationSuccessResponseHandler(final ChannelAssociation channelAssociation) {
        this.channelAssociation = channelAssociation;
    }

    @Override
    protected void processMessage(final MessageInputStream messageInputStream) throws IOException {
        // read the invocation id
        final DataInputStream dataInputStream = new DataInputStream(messageInputStream);
        final short invocationId;
        try {
            invocationId = dataInputStream.readShort();
        } finally {
            dataInputStream.close();
        }
        this.channelAssociation.resultReady(invocationId, new SuccessAcknowledgementResultProducer());
    }

    private class SuccessAcknowledgementResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        @Override
        public Object getResult() throws Exception {
            // there's no real result, since it's just an acknowledgemnt response that the
            // invocation was successful processed
            return null;
        }

        @Override
        public void discardResult() {
        }
    }
}
