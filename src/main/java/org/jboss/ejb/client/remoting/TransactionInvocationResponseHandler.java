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
class TransactionInvocationResponseHandler extends ProtocolMessageHandler {

    private final ChannelAssociation channelAssociation;

    TransactionInvocationResponseHandler(final ChannelAssociation channelAssociation) {
        this.channelAssociation = channelAssociation;
    }

    @Override
    protected void processMessage(final MessageInputStream messageInputStream) throws IOException {
        // read the invocation id
        final DataInputStream dataInputStream = new DataInputStream(messageInputStream);
        try {
            final short invocationId = dataInputStream.readShort();
            // check to see if this was a response to a "prepare" invocation. If yes, then
            // the message will also contain an additional int value with the prepare result
            final boolean prepareTx = dataInputStream.readBoolean();
            if (prepareTx) {
                final int prepareInvocationResult = PackedInteger.readPackedInteger(dataInputStream);
                // let the waiting invocation know that the result is ready
                this.channelAssociation.resultReady(invocationId, new TxInvocationResultProducer(prepareInvocationResult));
            } else {
                // let the waiting invocation know that the result is ready
                this.channelAssociation.resultReady(invocationId, new TxInvocationResultProducer());
            }
        } finally {
            dataInputStream.close();
        }
    }

    private class TxInvocationResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private Integer txResult;

        TxInvocationResultProducer() {
            this.txResult = null;
        }

        TxInvocationResultProducer(final Integer txResult) {
            this.txResult = txResult;
        }

        @Override
        public Object getResult() throws Exception {
            return this.txResult;
        }

        @Override
        public void discardResult() {
        }
    }
}
