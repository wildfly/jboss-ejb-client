/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
 * Handles a failure response message returned by the server when a session open request is made for a non-stateful
 * session bean.
 *
 * @author Jaikiran Pai
 */
class NonStatefulBeanSessionOpenFailureHandler extends ProtocolMessageHandler {

    private final ChannelAssociation channelAssociation;

    NonStatefulBeanSessionOpenFailureHandler(final ChannelAssociation channelAssociation) {
        this.channelAssociation = channelAssociation;
    }

    @Override
    protected void processMessage(MessageInputStream messageInputStream) throws IOException {
        final DataInputStream dataInputStream = new DataInputStream(messageInputStream);
        try {
            // read invocation id
            final short invocationId = dataInputStream.readShort();
            final String failureMessage = dataInputStream.readUTF();
            // let the result availability be known
            this.channelAssociation.resultReady(invocationId, new SessionOpenFailureResultProducer(failureMessage));

        } finally {
            dataInputStream.close();
        }
    }

    private class SessionOpenFailureResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private final String failureMessage;

        SessionOpenFailureResultProducer(final String failureMessage) {
            this.failureMessage = failureMessage;
        }

        @Override
        public Object getResult() throws Exception {
            throw new IllegalArgumentException(failureMessage);
        }

        @Override
        public void discardResult() {
        }
    }
}
