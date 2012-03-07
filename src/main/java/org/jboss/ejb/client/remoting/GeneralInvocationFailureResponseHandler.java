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

import javax.ejb.EJBException;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Responsible for parsing an invocation failure message from the stream and throwing back the exception as
 * a result of the (prior) invocation
 * <p/>
 * User: Jaikiran Pai
 */
class GeneralInvocationFailureResponseHandler extends ProtocolMessageHandler {

    /**
     * Failure types
     */
    enum FailureType {
        NO_SUCH_METHOD,
        SESSION_NOT_ACTIVE,
        EJB_NOT_STATEFUL
    }

    private final ChannelAssociation channelAssociation;
    private final FailureType failureType;

    GeneralInvocationFailureResponseHandler(final ChannelAssociation channelAssociation, final FailureType failureType) {
        this.failureType = failureType;
        this.channelAssociation = channelAssociation;
    }

    /**
     * Process the passed <code>MessageInputStream</code> for the invocation failure
     *
     * @param messageInputStream The message input stream
     * @throws IOException If there's a problem while reading the stream
     */
    @Override
    protected void processMessage(MessageInputStream messageInputStream) throws IOException {
        final DataInputStream dataInputStream = new DataInputStream(messageInputStream);
        try {
            // read invocation id
            final short invocationId = dataInputStream.readShort();
            final String failureMessage = dataInputStream.readUTF();
            final Exception exception = new EJBException(failureMessage);
            // let the result availability be known
            this.channelAssociation.resultReady(invocationId, new InvocationFailureResultProducer(exception));

        } finally {
            dataInputStream.close();
        }
    }

    /**
     * A ResultProducer which throws back the invocation failure exception when the
     * {@link org.jboss.ejb.client.EJBReceiverInvocationContext.ResultProducer#getResult()} is invoked
     */
    private class InvocationFailureResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private final Exception invocationFailure;

        InvocationFailureResultProducer(final Exception invocationFailure) {
            this.invocationFailure = invocationFailure;
        }


        @Override
        public Object getResult() throws Exception {
            // glue the client side exception with the server side
            GeneralInvocationFailureResponseHandler.this.glueStackTraces(this.invocationFailure, Thread.currentThread().getStackTrace(), 1, "asynchronous invocation");
            throw this.invocationFailure;
        }

        @Override
        public void discardResult() {
        }
    }
}
