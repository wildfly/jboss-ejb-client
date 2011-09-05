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

package org.jboss.ejb.client.protocol;

import org.jboss.logging.Logger;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleDataOutput;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;

import java.io.IOException;

/**
 * User: jpai
 */
public class Version0ProtocolHandler {

    private static final Logger logger = Logger.getLogger(Version0ProtocolHandler.class);
    
    private static final MarshallerFactory marshallerFactory = Marshalling.getProvidedMarshallerFactory("river");

    private final MarshallingConfiguration marshallingConfiguration;

    public static final byte VERSION = 0x00;

    public Version0ProtocolHandler(final MarshallingConfiguration marshallingConfiguration) {
//        this.marshallingConfiguration = new MarshallingConfiguration();
//        this.marshallingConfiguration.setVersion(2);
        this.marshallingConfiguration = marshallingConfiguration;

    }

    public void writeInvocationRequest(final MessageOutputStream messageOutputStream, final InvocationRequest invocationRequest) throws IOException {
        final Marshaller marshaller = marshallerFactory.createMarshaller(this.marshallingConfiguration);

        final ByteOutput byteOutput = Marshalling.createByteOutput(messageOutputStream);
        marshaller.start(byteOutput);

        // write the header
        marshaller.write(InvocationRequest.INVOCATION_REQUEST_HEADER);
        marshaller.writeShort(invocationRequest.getInvocationId());
        // full ids
        marshaller.writeByte(0x07);
        if (invocationRequest.getAppName() != null) {
            marshaller.writeUTF(invocationRequest.getAppName());
        }
        marshaller.writeUTF(invocationRequest.getModuleName());
        marshaller.writeUTF(invocationRequest.getBeanName());
        marshaller.writeUTF(invocationRequest.getViewClassName());
        marshaller.writeUTF(invocationRequest.getMethodName());
        final String[] methodParamTypes = invocationRequest.getParamTypes();
        final Object[] params = invocationRequest.getParams();
        if (methodParamTypes != null) {
            marshaller.writeByte(methodParamTypes.length);
            for (int i = 0; i < methodParamTypes.length; i++) {
                final MethodParam methodParam = new MethodParam(methodParamTypes[i], params[i]);
                marshaller.writeObject(methodParam);
            }
        } else {
            marshaller.writeByte(0);
        }
        final Attachment[] attachments = invocationRequest.getAttachments();
        if (attachments != null) {
            marshaller.writeByte(attachments.length);
            for (final Attachment attachment : attachments) {
                // TODO: Write out the attachment
            }
        } else {
            marshaller.writeByte(0);
        }
        // done
        marshaller.finish();
        marshaller.close();
    }

    public void writeInvocationResponse(final InvocationResponse invocationResponse, final MessageOutputStream messageOutputStream) throws IOException {
        final Marshaller marshaller = marshallerFactory.createMarshaller(this.marshallingConfiguration);

        final ByteOutput byteOutput = Marshalling.createByteOutput(messageOutputStream);
        marshaller.start(byteOutput);

        // write the header
        marshaller.write(InvocationResponse.INVOCATION_RESPONSE_HEADER);
        // write the InvocationResponse
        marshaller.writeObject(invocationResponse);

        marshaller.finish();
        marshaller.close();

    }

    public InvocationResponse readInvocationResponse(final MessageInputStream messageInputStream) throws IOException {
        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(this.marshallingConfiguration);
        final ByteInput byteInput = Marshalling.createByteInput(messageInputStream);
        unmarshaller.start(byteInput);

        final byte header = unmarshaller.readByte();
        if (header != InvocationResponse.INVOCATION_RESPONSE_HEADER) {
            throw new IOException("Incorrect header 0x" + Integer.toHexString(header) + " in invocation response");
        }
        // invocation id
//        final short invocationId = unmarshaller.readShort();
//        // exception or not
//        final byte exception = unmarshaller.readByte();
//        final boolean isException = (exception & 0x01) == 1;
//        Object obj = null;
//        try {
//            obj = unmarshaller.readObject();
//        } catch (ClassNotFoundException e) {
//            throw new IOException(e);
//        }
//        InvocationResponse invocationResponse;
//        if (isException) {
//            return new InvocationResponse(invocationId, obj, null);
//        }
//        return new InvocationResponse(invocationId, null, (Exception) obj);
        try {
            final InvocationResponse invocationResponse = unmarshaller.readObject(InvocationResponse.class);
            return invocationResponse;
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    public void sendVersionGreeting(final MessageOutputStream messageOutputStream) throws IOException {
        final SimpleDataOutput output = new SimpleDataOutput(Marshalling.createByteOutput(messageOutputStream));
        output.writeByte(VERSION); // test version
        output.close();
    }
}
