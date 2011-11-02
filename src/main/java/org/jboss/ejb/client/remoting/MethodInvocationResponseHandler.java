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
import org.jboss.logging.Logger;
import org.jboss.remoting3.MessageInputStream;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Responsible for processing a message and parsing the method invocation response from it, as per the
 * EJB remoting client protocol specification
 * <p/>
 * User: Jaikiran Pai
 */
class MethodInvocationResponseHandler extends ProtocolMessageHandler {


    private static final Logger logger = Logger.getLogger(MethodInvocationResponseHandler.class);

    private final String marshallingType;

    private final ChannelAssociation channelAssociation;

    MethodInvocationResponseHandler(final ChannelAssociation channelAssociation, final String marshallingType) {
        this.marshallingType = marshallingType;
        this.channelAssociation = channelAssociation;
    }


    /**
     * Reads the passed <code>messageInputStream</code> and parses it for a method invocation response.
     * This method doesn't fully parse the stream and instead just parses enough so that it is able to
     * create a {@link EJBReceiverInvocationContext.ResultProducer} which can further parse the rest of the
     * stream in its {@link org.jboss.ejb.client.EJBReceiverInvocationContext.ResultProducer#getResult()} (whenever that
     * gets invoked)
     *
     * @param messageInputStream The message input stream
     * @throws IOException If there is a problem reading from the stream
     */
    @Override
    protected void processMessage(MessageInputStream messageInputStream) throws IOException {
        if (messageInputStream == null) {
            throw new IllegalArgumentException("Cannot read from null stream");
        }
        final DataInputStream input = new DataInputStream(messageInputStream);
        // read the invocation id
        final short invocationId = input.readShort();
        // create a ResultProducer which can unmarshall and return the result, later
        final EJBReceiverInvocationContext.ResultProducer resultProducer = new MethodInvocationResultProducer(input);
        // make it known that the result is available
        this.channelAssociation.resultReady(invocationId, resultProducer);
    }

    /**
     * A result producer which parses a input stream and returns a method invocation response as a result
     */
    private class MethodInvocationResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private final DataInputStream input;

        MethodInvocationResultProducer(final DataInputStream input) {
            this.input = input;
        }

        @Override
        public Object getResult() throws Exception {
            try {
                // read the attachments
                MethodInvocationResponseHandler.this.readAttachments(input);
                final UnMarshaller unMarshaller = MarshallerFactory.createUnMarshaller(MethodInvocationResponseHandler.this.marshallingType);
                final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                // A ClassLoaderProvider which returns TCCL that was present when this provider
                // was instantiated
                final UnMarshaller.ClassLoaderProvider classLoaderProvider = new UnMarshaller.ClassLoaderProvider() {
                    @Override
                    public ClassLoader provideClassLoader() {
                        return classLoader;
                    }
                };
                unMarshaller.start(this.input, classLoaderProvider);
                // read the result
                final Object result = unMarshaller.readObject();
                unMarshaller.finish();
                return result;
            } finally {
                this.input.close();
            }
        }

        @Override
        public void discardResult() {
            //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
