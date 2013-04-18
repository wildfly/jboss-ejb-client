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

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.SessionID;
import org.jboss.marshalling.AbstractClassResolver;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

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
    private static final byte HEADER_MODULE_AVAILABLE = 0x08;
    private static final byte HEADER_MODULE_UNAVAILABLE = 0x09;
    private static final byte HEADER_NO_SUCH_EJB_FAILURE = 0x0A;
    private static final byte HEADER_INVOCATION_EXCEPTION = 0x06;
    private static final byte HEADER_ASYNC_METHOD_NOTIFICATION = 0x0E;

    public static final String REQUEST_RECEIVED_WAS_COMPRESSED = "Request received by server was compressed";

    public DummyProtocolHandler(final String marshallerType) {
        this.marshallerFactory = Marshalling.getProvidedMarshallerFactory(marshallerType);
        if (this.marshallerFactory == null) {
            throw new RuntimeException("Could not find a marshaller factory for " + marshallerType + " marshalling strategy");
        }
    }

    public MethodInvocationRequest readMethodInvocationRequest(final InputStream inputStream, final ClassLoader cl) throws IOException {
        final DataInput input = new DataInputStream(inputStream);
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

        // unmarshall the locator and the method params
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
        // unmarshall the attachments
        final Map<String, Object> attachments;
        try {
            attachments = this.readAttachments(unmarshaller);
        } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException(cnfe);
        }

        unmarshaller.finish();

        return new MethodInvocationRequest(invocationId, appName, moduleName, distinctName, beanName,
                ejbLocator.getViewType().getName(), methodName, methodParamTypes, methodParams, attachments);
    }

    public void writeMethodInvocationResponse(final DataOutput output, final short invocationId, final Object result,
                                              final Map<String, Object> attachments) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Cannot write to null output");
        }
        output.write(HEADER_INVOCATION_RESPONSE);
        // write the invocation id
        output.writeShort(invocationId);
        // write out the result
        final Marshaller marshaller = this.prepareForMarshalling(output);
        marshaller.writeObject(result);
        // write the attachments
        this.writeAttachments(marshaller, attachments);
        marshaller.finish();
    }

    public void writeException(final DataOutput output, final short invocationId, final Throwable t,
                               final Map<String, Object> attachments) throws IOException {
        // write the header
        output.write(HEADER_INVOCATION_EXCEPTION);
        // write the invocation id
        output.writeShort(invocationId);
        // write out the exception
        final Marshaller marshaller = this.prepareForMarshalling(output);
        marshaller.writeObject(t);
        // write the attachments
        this.writeAttachments(marshaller, attachments);
        // finish marshalling
        marshaller.finish();
    }

    private void writeInvocationFailure(final DataOutput output, final byte messageHeader, final short invocationId, final String failureMessage) throws IOException {
        // write header
        output.writeByte(messageHeader);
        // write invocation id
        output.writeShort(invocationId);
        // write the failure message
        output.writeUTF(failureMessage);
    }

    public void writeNoSuchEJBFailureMessage(final DataOutput output, final short invocationId, final String appName, final String moduleName,
                                             final String distinctName, final String beanName, final String viewClassName) throws IOException {

        final StringBuffer sb = new StringBuffer("No such EJB[");
        sb.append("appname=").append(appName).append(",");
        sb.append("modulename=").append(moduleName).append(",");
        sb.append("distinctname=").append(distinctName).append(",");
        sb.append("beanname=").append(beanName);
        if (viewClassName != null) {
            sb.append(",").append("viewclassname=").append(viewClassName);
        }
        sb.append("]");
        this.writeInvocationFailure(output, HEADER_NO_SUCH_EJB_FAILURE, invocationId, sb.toString());
    }

    public void writeSessionId(final DataOutput output, final short invocationId, final SessionID sessionID, final Affinity hardAffinity) throws IOException {
        final byte[] sessionIdBytes = sessionID.getEncodedForm();
        // write out header
        output.writeByte(HEADER_SESSION_OPEN_RESPONSE);
        // write out invocation id
        output.writeShort(invocationId);
        // session id byte length
        PackedInteger.writePackedInteger(output, sessionIdBytes.length);
        // write out the session id bytes
        output.write(sessionIdBytes);
        // now marshal the hard affinity associated with this session
        final Marshaller marshaller = this.prepareForMarshalling(output);
        marshaller.writeObject(hardAffinity);

        // finish marshalling
        marshaller.finish();
    }

    public void writeAsyncMethodNotification(final DataOutput output, final short invocationId) throws IOException {
        // write the header
        output.write(HEADER_ASYNC_METHOD_NOTIFICATION);
        // write the invocation id
        output.writeShort(invocationId);
    }

    private Map<String, Object> readAttachments(final ObjectInput input) throws IOException, ClassNotFoundException {
        final int numAttachments = input.readByte();
        if (numAttachments == 0) {
            return null;
        }
        final Map<String, Object> attachments = new HashMap<String, Object>(numAttachments);
        for (int i = 0; i < numAttachments; i++) {
            // read the key
            final String key = (String) input.readObject();
            // read the attachment value
            final Object val = input.readObject();
            attachments.put(key, val);
        }
        return attachments;
    }

    private void writeAttachments(final ObjectOutput output, final Map<String, Object> attachments) throws IOException {
        if (attachments == null) {
            output.writeByte(0);
            return;
        }
        // write the attachment count
        PackedInteger.writePackedInteger(output, attachments.size());
        for (Map.Entry<String, Object> entry : attachments.entrySet()) {
            output.writeObject(entry.getKey());
            output.writeObject(entry.getValue());
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
        marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
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
        marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
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
