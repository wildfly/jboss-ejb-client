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

import org.jboss.ejb.client.AttachmentKeys;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.logging.Logger;

import javax.ejb.NoSuchEJBException;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Responsible for handling {@link NoSuchEJBException} response from a server and retrying that invocation
 * on a different node, if possible
 *
 * @author Jaikiran Pai
 */
class NoSuchEJBExceptionResponseHandler extends ProtocolMessageHandler {

    private static final Logger logger = Logger.getLogger(NoSuchEJBExceptionResponseHandler.class);

    private final ChannelAssociation channelAssociation;

    NoSuchEJBExceptionResponseHandler(final ChannelAssociation channelAssociation) {
        this.channelAssociation = channelAssociation;
    }

    /**
     * Process the passed <code>MessageInputStream</code> for the invocation failure
     *
     *
     * @param inputStream@throws java.io.IOException If there's a problem while reading the stream
     */
    @Override
    protected void processMessage(InputStream inputStream) throws IOException {
        final DataInputStream dataInputStream = new DataInputStream(inputStream);
        final NoSuchEJBException noSuchEJBException;
        final short invocationId;
        try {
            // read invocation id
            invocationId = dataInputStream.readShort();
            final String failureMessage = dataInputStream.readUTF();
            noSuchEJBException = new NoSuchEJBException(failureMessage);
        } finally {
            dataInputStream.close();
        }
        final EJBReceiverInvocationContext receiverInvocationContext = this.channelAssociation.getEJBReceiverInvocationContext(invocationId);
        if (receiverInvocationContext == null) {
            logger.info("Cannot retry invocation which failed with exception:", noSuchEJBException);
            // let the client know the result, since we can't retry without a receiver invocation context
            this.channelAssociation.resultReady(invocationId, new ResultProducer(noSuchEJBException));
            return;
        }
        // if the invocation is associated with a transaction, disable retry
        EJBClientInvocationContext clientInvocationContext = receiverInvocationContext.getClientInvocationContext();
        if (clientInvocationContext.getAttachment(AttachmentKeys.TRANSACTION_ID_KEY) != null) {
            final IllegalStateException illegalStateException = new IllegalStateException("Cannot retry transaction-scoped invocation");
            logger.info("Cannot retry invocation which failed with exception:", illegalStateException);
            // let the client know the result, since we can't retry with a transaction context
            this.channelAssociation.resultReady(invocationId, new ResultProducer(illegalStateException));
            return;
        }
        // retry the invocation on a different node
        try {
            logger.info("Retrying invocation which failed on node " + receiverInvocationContext.getNodeName() + " with exception:", noSuchEJBException);
            receiverInvocationContext.retryInvocation(true);

            // EJBCLIENT-130: remove any stale invocationID associated with the failed invocation from
            // the ChannelAssociation map of waiting method invocations, if the retry is successful
            this.channelAssociation.cleanupStaleResponse(invocationId);
        } catch (Exception e) {
            // retry failed, let the waiting client know of this failure
            this.channelAssociation.resultReady(invocationId, new ResultProducer(e));
            return;
        }
    }

    /**
     * A ResultProducer which throws back {@link NoSuchEJBException}
     */
    private class ResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private final Exception exception;

        ResultProducer(final Exception exception) {
            this.exception = exception;
        }


        @Override
        public Object getResult() throws Exception {
            // glue the client side exception with the server side
            NoSuchEJBExceptionResponseHandler.this.glueStackTraces(this.exception, Thread.currentThread().getStackTrace(), 1, "asynchronous invocation");
            throw this.exception;
        }

        @Override
        public void discardResult() {
        }
    }
}

