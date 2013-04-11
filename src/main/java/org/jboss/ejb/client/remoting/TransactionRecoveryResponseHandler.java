/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
import org.jboss.ejb.client.XidTransactionID;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Unmarshaller;

import javax.transaction.xa.Xid;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Responsible for handling the response returned back for a transaction recovery request
 *
 * @author Jaikiran Pai
 */
class TransactionRecoveryResponseHandler extends ProtocolMessageHandler {

    private final ChannelAssociation channelAssociation;
    private final MarshallerFactory marshallerFactory;

    TransactionRecoveryResponseHandler(final ChannelAssociation channelAssociation, final MarshallerFactory marshallerFactory) {
        this.channelAssociation = channelAssociation;
        this.marshallerFactory = marshallerFactory;
    }

    @Override
    protected void processMessage(final InputStream inputStream) throws IOException {
        // read the invocation id
        final DataInputStream dataInputStream = new DataInputStream(inputStream);
        try {
            final short invocationId = dataInputStream.readShort();
            final int numXidsToRecover = PackedInteger.readPackedInteger(dataInputStream);
            if (numXidsToRecover > 0) {
                // prepare the unmarshaller
                final Unmarshaller unmarshaller = this.prepareForUnMarshalling(marshallerFactory, dataInputStream);
                final Xid[] xidsToRecover = new Xid[numXidsToRecover];
                for (int i = 0; i < numXidsToRecover; i++) {
                    final XidTransactionID xidTransactionID = (XidTransactionID) unmarshaller.readObject();
                    xidsToRecover[i] = xidTransactionID.getXid();
                }
                // finish unmarshalling
                unmarshaller.finish();
                // let the waiting invocation know that the result is ready
                this.channelAssociation.resultReady(invocationId, new TxRecoveryResultProducer(xidsToRecover));
            } else {
                // let the waiting invocation know that the result is ready
                this.channelAssociation.resultReady(invocationId, new TxRecoveryResultProducer(new Xid[0]));
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        } finally {
            dataInputStream.close();
        }
    }

    private class TxRecoveryResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private Xid[] xidsToRecover;

        TxRecoveryResultProducer(final Xid[] xidsToRecover) {
            this.xidsToRecover = xidsToRecover;
        }

        @Override
        public Object getResult() throws Exception {
            return this.xidsToRecover;
        }

        @Override
        public void discardResult() {
        }
    }
}
