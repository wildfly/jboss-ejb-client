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
 * User: jpai
 */
class MethodInvocationResponseHandler extends ProtocolMessageHandler {


    private static final Logger logger = Logger.getLogger(MethodInvocationResponseHandler.class);

    private final String marshallingType;

    private final ChannelAssociation channelAssociation;

    MethodInvocationResponseHandler(final ChannelAssociation channelAssociation, final String marshallingType) {
        this.marshallingType = marshallingType;
        this.channelAssociation = channelAssociation;
    }


    @Override
    public void processMessage(MessageInputStream messageInputStream) throws IOException {
        if (messageInputStream == null) {
            throw new IllegalArgumentException("Cannot read from null stream");
        }
        final DataInputStream input = new DataInputStream(messageInputStream);
        // read the invocation id
        final short invocationId = input.readShort();
        final EJBReceiverInvocationContext context = this.channelAssociation.getEJBReceiverInvocationContext(invocationId);
        if (context == null) {
            logger.debug("No context available for invocation id: " + invocationId + ". Discarding result");
            return;
        }
        // create a ResultProducer which can unmarshal and return the result, later
        final EJBReceiverInvocationContext.ResultProducer resultProducer = new MethodInvocationResultProducer(input);
        context.resultReady(resultProducer);

    }

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
                unMarshaller.start(this.input, classLoader);
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
