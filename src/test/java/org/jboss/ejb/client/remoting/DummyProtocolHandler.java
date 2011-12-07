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
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.jboss.ejb.client.EJBLocator;
import org.jboss.marshalling.AbstractClassResolver;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;

/**
 * User: jpai
 */
public class DummyProtocolHandler {


    private static final char METHOD_PARAM_TYPE_SEPARATOR = ',';

    private final MarshallerFactory marshallerFactory;

    private static final byte HEADER_SESSION_OPEN_RESPONSE = 0x02;
    private static final byte HEADER_INVOCATION_REQUEST = 0x03;
    private static final byte HEADER_INVOCATION_CANCEL_REQUEST = 0x04;
    private static final byte HEADER_INVOCATION_RESPONSE = 0x05;
    private static final byte HEADER_INVOCATION_FAILURE = 0x06;
    private static final byte HEADER_MODULE_AVAILABLE = 0x08;
    private static final byte HEADER_MODULE_UNAVAILABLE = 0x09;

    public DummyProtocolHandler(final String marshallerType) {
        this.marshallerFactory = Marshalling.getProvidedMarshallerFactory(marshallerType);
        if (this.marshallerFactory == null) {
            throw new RuntimeException("Could not find a marshaller factory for " + marshallerType + " marshalling strategy");
        }
    }

    public MethodInvocationRequest readMethodInvocationRequest(final DataInput input, final ClassLoader cl) throws IOException {
        // read the invocation id
        final short invocationId = input.readShort();
        final String methodName = input.readUTF();
        // method signature
        String[] methodParamTypes = null;
        final String signature = input.readUTF();
        if (signature.isEmpty()) {
            methodParamTypes = new String[0];
        } else {
            methodParamTypes = signature.split(String.valueOf(METHOD_PARAM_TYPE_SEPARATOR));
        }
        // read the attachments
        final RemotingAttachments attachments = this.readAttachments(input);

        // un-marshall the method params
        final Object[] methodParams = new Object[methodParamTypes.length];
        final Unmarshaller unmarshaller = this.prepareForUnMarshalling(input);
        String appName = null;
        String moduleName = null;
        String distinctName = null;
        String beanName = null;
        EJBLocator ejbLocator = null;
        try {
            appName = (String) unmarshaller.readObject();
            moduleName = (String) unmarshaller.readObject();
            distinctName = (String) unmarshaller.readObject();
            beanName = (String) unmarshaller.readObject();
            ejbLocator = (EJBLocator) unmarshaller.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < methodParamTypes.length; i++) {
            try {
                methodParams[i] = unmarshaller.readObject();
            } catch (ClassNotFoundException cnfe) {
                throw new RuntimeException(cnfe);
            }
        }
        unmarshaller.finish();

        return new MethodInvocationRequest(invocationId, appName, moduleName, distinctName, beanName,
                ejbLocator.getViewType().getName(), methodName, methodParamTypes, methodParams, attachments);
    }

    public void writeMethodInvocationResponse(final DataOutput output, final short invocationId, final Object result,
                                              final RemotingAttachments attachments) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Cannot write to null output");
        }

        // write invocation response header
        output.write(HEADER_INVOCATION_RESPONSE);
        // write the invocation id
        output.writeShort(invocationId);
        // write the attachments
        this.writeAttachments(output, attachments);
        // write out the result
        final Marshaller marshaller = this.prepareForMarshalling(output);
        marshaller.writeObject(result);
        marshaller.finish();
    }

    private RemotingAttachments readAttachments(final DataInput input) throws IOException {
        int numAttachments = input.readByte();
        if (numAttachments == 0) {
            return null;
        }
        final RemotingAttachments attachments = new RemotingAttachments();
        for (int i = 0; i < numAttachments; i++) {
            // read attachment id
            final short attachmentId = input.readShort();
            // read attachment data length
            final int dataLength = PackedInteger.readPackedInteger(input);
            // read the data
            final byte[] data = new byte[dataLength];
            input.readFully(data);

            attachments.putPayloadAttachment(attachmentId, data);
        }
        return attachments;
    }

    private void writeAttachments(final DataOutput output, final RemotingAttachments attachments) throws IOException {
        if (attachments == null) {
            output.writeByte(0);
            return;
        }
        // write attachment count
        output.writeByte(attachments.size());
        for (RemotingAttachments.RemotingAttachment attachment : attachments.entries()) {
            // write attachment id
            output.writeShort(attachment.getKey());
            final byte[] data = attachment.getValue();
            // write data length
            PackedInteger.writePackedInteger(output, data.length);
            // write the data
            output.write(data);

        }
    }

    /**
     * Creates and returns a {@link org.jboss.marshalling.Marshaller} which is ready to be used for marshalling. The {@link org.jboss.marshalling.Marshaller#start(org.jboss.marshalling.ByteOutput)}
     * will be invoked by this method, to use the passed {@link DataOutput dataOutput}, before returning the marshaller.
     *
     * @param dataOutput The {@link DataOutput} to which the data will be marshalled
     * @return
     * @throws IOException
     */
    private Marshaller prepareForMarshalling(final DataOutput dataOutput) throws IOException {
        final Marshaller marshaller = this.getMarshaller();
        final OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                final int byteToWrite = b & 0xff;
                dataOutput.write(byteToWrite);
            }
        };
        final ByteOutput byteOutput = Marshalling.createByteOutput(outputStream);
        // start the marshaller
        marshaller.start(byteOutput);

        return marshaller;
    }

    /**
     * Creates and returns a {@link Marshaller}
     *
     * @return
     * @throws IOException
     */
    private Marshaller getMarshaller() throws IOException {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
        marshallingConfiguration.setVersion(2);

        return marshallerFactory.createMarshaller(marshallingConfiguration);
    }

    /**
     * Creates and returns a {@link org.jboss.marshalling.Unmarshaller} which is ready to be used for unmarshalling. The {@link org.jboss.marshalling.Unmarshaller#start(org.jboss.marshalling.ByteInput)}
     * will be invoked by this method, to use the passed {@link DataInput dataInput}, before returning the unmarshaller.
     *
     * @param dataInput The data input from which to unmarshall
     * @return
     * @throws IOException
     */
    private Unmarshaller prepareForUnMarshalling(final DataInput dataInput) throws IOException {
        final Unmarshaller unmarshaller = this.getUnMarshaller();
        final InputStream is = new InputStream() {
            @Override
            public int read() throws IOException {
                try {

                    final int b = dataInput.readByte();
                    return b & 0xff;
                } catch (EOFException eof) {
                    return -1;
                }
            }
        };
        final ByteInput byteInput = Marshalling.createByteInput(is);
        // start the unmarshaller
        unmarshaller.start(byteInput);

        return unmarshaller;
    }

    /**
     * Creates and returns a {@link Unmarshaller}
     *
     * @return
     * @throws IOException
     */
    private Unmarshaller getUnMarshaller() throws IOException {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
        marshallingConfiguration.setClassResolver(TCCLClassResolver.INSTANCE);

        return marshallerFactory.createUnmarshaller(marshallingConfiguration);
    }

    /**
     * A {@link org.jboss.marshalling.ClassResolver} which returns the context classloader associated
     * with the thread, when the {@link #getClassLoader()} is invoked
     */
    private static final class TCCLClassResolver extends AbstractClassResolver {
        static TCCLClassResolver INSTANCE = new TCCLClassResolver();

        private TCCLClassResolver() {
        }

        @Override
        protected ClassLoader getClassLoader() {
            return SecurityActions.getContextClassLoader();
        }
    }
}
