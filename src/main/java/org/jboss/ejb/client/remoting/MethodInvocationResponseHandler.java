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

import java.io.DataInput;
import java.io.IOException;

/**
 * User: jpai
 */
class MethodInvocationResponseHandler extends ProtocolMessageHandler {


    private final String marshallingType;

    private Object methodInvocationResult;

    private short invocationId;

    private final ChannelAssociation channelAssociation;

    MethodInvocationResponseHandler(final ChannelAssociation channelAssociation, final String marshallingType) {
        this.marshallingType = marshallingType;
        this.channelAssociation = channelAssociation;
    }

    @Override
    public void readMessage(DataInput input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Cannot read from null input");
        }

        // read the invocation id
        this.invocationId = input.readShort();
        final UnMarshaller unMarshaller = MarshallerFactory.createUnMarshaller(this.marshallingType);
        // read the attachments
        this.readAttachments(input);
        // TODO: Read Jason's module CL wiki and other aspects of CL w.r.t EJB remoting and fix this
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        unMarshaller.start(input, classLoader);
        // read the result
        try {
            this.methodInvocationResult = unMarshaller.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        unMarshaller.finish();
    }

    @Override
    public void processMessage() {
        this.channelAssociation.handleInvocationResponse(this.invocationId, this.methodInvocationResult);
    }
}
