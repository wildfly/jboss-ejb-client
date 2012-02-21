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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;

import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.logging.Logger;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.MessageInputStream;

/**
 * @author Jaikiran Pai
 */
class InvocationExceptionResponseHandler extends ProtocolMessageHandler {

    private static final Logger logger = Logger.getLogger(InvocationExceptionResponseHandler.class);

    private final MarshallerFactory marshallerFactory;

    private final ChannelAssociation channelAssociation;

    InvocationExceptionResponseHandler(final ChannelAssociation channelAssociation, final MarshallerFactory marshallerFactory) {
        this.marshallerFactory = marshallerFactory;
        this.channelAssociation = channelAssociation;
    }


    @Override
    protected void processMessage(MessageInputStream messageInputStream) throws IOException {
        if (messageInputStream == null) {
            throw new IllegalArgumentException("Cannot read from null stream");
        }
        final DataInputStream input = new DataInputStream(messageInputStream);
        // read the invocation id
        final short invocationId = input.readShort();
        // create a ResultProducer which can unmarshall and return the result, later
        final EJBReceiverInvocationContext.ResultProducer resultProducer = new MethodInvocationExceptionResultProducer(input);
        // make it known that the result is available
        this.channelAssociation.resultReady(invocationId, resultProducer);
    }

    /**
     * A result producer which parses a input stream and returns a method invocation response as a result
     */
    private class MethodInvocationExceptionResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private final DataInputStream input;

        MethodInvocationExceptionResultProducer(final DataInputStream input) {
            this.input = input;
        }

        @Override
        public Object getResult() throws Exception {
            try {
                // prepare the unmarshaller
                final Unmarshaller unmarshaller = InvocationExceptionResponseHandler.this.prepareForUnMarshalling(InvocationExceptionResponseHandler.this.marshallerFactory, this.input);
                Object result = unmarshaller.readObject();

                // read the attachments
                final Map<String, Object> attachments = InvocationExceptionResponseHandler.this.readAttachments(unmarshaller);

                // finish unmarshalling
                unmarshaller.finish();

                // should never happen if the server implemented the protocol correctly
                // but let's just check - instead of running into a ClassCastException, we can provide a more
                // meaningful error message
                if (result instanceof Throwable == false) {
                    throw new RuntimeException("Method invocation failure message contained a payload: " + result + " which is *not* of type " + Throwable.class.getName());
                }
                final Throwable t = (Throwable) result;
                if (t instanceof Exception) {
                    // glue the client side exception with the server side
                    InvocationExceptionResponseHandler.this.glueStackTraces(t, Thread.currentThread().getStackTrace(), 1, "asynchronous invocation");
                    throw (Exception) t;
                } else {
                    throw new RuntimeException(t);
                }
            } finally {
                this.input.close();
            }
        }

        @Override
        public void discardResult() {
        }
    }
}
