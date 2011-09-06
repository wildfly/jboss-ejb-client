/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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

import org.jboss.ejb.client.EJBViewResolutionResult;
import org.jboss.ejb.client.EJBViewResolver;
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
        this.marshallingConfiguration = marshallingConfiguration;

    }

    public MessageType getMessageType(final Unmarshaller unmarshaller) throws IOException {
        final byte header = unmarshaller.readByte();
        for (MessageType messageType : MessageType.values()) {
            if (messageType.getHeader() == header) {
                return messageType;
            }
        }
        throw new IOException("Unidentified messaget type header 0x" + Integer.toHexString(header));
    }

    public void writeInvocationRequest(final InvocationRequest invocationRequest, final Marshaller marshaller) throws IOException {
        // write the header
        marshaller.write(MessageType.INVOCATION_REQUEST.getHeader());
        //invocationRequest.writeExternal(marshaller);
        marshaller.writeShort(invocationRequest.getInvocationId());
        marshaller.writeByte(0x07); // full ids
        // TODO: fix the protocol so we know whether appName is coming up or not
        if (invocationRequest.getAppName() != null) {
            marshaller.writeUTF(invocationRequest.getAppName());
        } else {
            throw new RuntimeException("NYI");
        }
        marshaller.writeUTF(invocationRequest.getModuleName());
        marshaller.writeUTF(invocationRequest.getBeanName());
        marshaller.writeUTF(invocationRequest.getViewClassName());
        marshaller.writeUTF(invocationRequest.getMethodName());
        final Object[] params = invocationRequest.getParams();
        final Class<?>[] paramTypes = invocationRequest.getParamTypes();
        if (params != null) {
            marshaller.writeByte(params.length);
            for (int i = 0; i < params.length; i++) {
                marshaller.writeObject(paramTypes[i]);
                marshaller.writeObject(params[i]);
            }
        } else {
            marshaller.writeByte(0);
        }
        Attachment[] attachments = invocationRequest.getAttachments();
        if (attachments != null) {
            marshaller.writeByte(attachments.length);
            for (final Attachment attachment : attachments) {
                // do not call writeObject, because we don't want serialization bits
                attachment.writeExternal(marshaller);
            }
        } else
            marshaller.writeByte(0);

        // done
        marshaller.finish();
        marshaller.close();
    }

    public InvocationRequest readInvocationRequest(final Unmarshaller unmarshaller, final EJBViewResolver ejbClassLoaderResolver) throws IOException {
        final int invocationId = unmarshaller.readShort();
        unmarshaller.read(); // full ids
        final String appName = unmarshaller.readUTF();
        final String moduleName = unmarshaller.readUTF();
        final String beanName = unmarshaller.readUTF();
        final String viewClassName = unmarshaller.readUTF();
        final String methodName = unmarshaller.readUTF();
        int paramLength = unmarshaller.readByte();
        if (paramLength < 0) {
            // negative length isn't valid
            throw new IOException("Invalid input - Negative param length: " + paramLength + " found");
        }
        final Class<?> methodParamTypes[] = new Class<?>[paramLength];
        final Object methodParams[] = new Object[paramLength];
        Attachment[] attachments = null;

        final EJBViewResolutionResult ejbViewResolutionResult = ejbClassLoaderResolver.resolveEJBView(appName, moduleName, beanName, viewClassName);
        final ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
        try {
            // set the TCCL to the bean's CL to ensure that the unmarshalling happens in the context
            // of the bean's CL
            Thread.currentThread().setContextClassLoader(ejbViewResolutionResult.getEJBClassLoader());
            if (paramLength > 0) {
                for (int i = 0; i < paramLength; i++) {
                    try {
                        methodParamTypes[i] = (Class<?>) unmarshaller.readObject();
                        methodParams[i] = unmarshaller.readObject();

                    } catch (ClassNotFoundException e) {
                        throw new IOException(e);
                    }
                }
            }
            int attachmentLength = unmarshaller.readByte();
            if (attachmentLength < 0) {
                // negative length isn't valid
                throw new IOException("Invalid input - Negative attachment length: " + attachmentLength + " found");
            }
            if (attachmentLength > 0) {
                attachments = new Attachment[attachmentLength];
                for (int i = 0; i < attachmentLength; i++) {
                    // TODO: Implement unmarshalling of attachments
                }
            }

        } finally {
            Thread.currentThread().setContextClassLoader(oldCL);
        }
        return new InvocationRequest(invocationId, appName, moduleName, beanName, viewClassName, methodName,
                methodParamTypes, methodParams, attachments);
    }

    public void writeInvocationResponse(final InvocationResponse invocationResponse, final Marshaller marshaller) throws IOException {
        // write the header
        marshaller.write(InvocationResponse.INVOCATION_RESPONSE_HEADER);
        marshaller.writeShort(invocationResponse.getInvocationId());
        if (invocationResponse.isException()) {
            // exception indicator
            marshaller.writeBoolean(true);
            marshaller.writeObject(invocationResponse.getException());
        } else {
            // no exception
            marshaller.writeBoolean(false);
            marshaller.writeObject(invocationResponse.getResult());
        }
        marshaller.finish();
        marshaller.close();

    }

    public InvocationResponse readInvocationResponse(final Unmarshaller unmarshaller) throws IOException {
        final byte header = unmarshaller.readByte();
        if (header != InvocationResponse.INVOCATION_RESPONSE_HEADER) {
            throw new IOException("Incorrect header 0x" + Integer.toHexString(header) + " in invocation response");
        }
        final int invocationId = unmarshaller.readShort();
        final boolean isException = unmarshaller.readBoolean();
        try {
            if (isException) {
                final Throwable throwable = (Throwable) unmarshaller.readObject();
                return new InvocationResponse(invocationId, null, throwable);

            } else {
                final Object result = unmarshaller.readObject();
                return new InvocationResponse(invocationId, result, null);
            }

        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    public void sendVersionGreeting(final MessageOutputStream messageOutputStream) throws IOException {
        final SimpleDataOutput output = new SimpleDataOutput(Marshalling.createByteOutput(messageOutputStream));
        output.writeByte(VERSION); // test version
        output.close();
    }

    public Unmarshaller createUnMarshaller(final MessageInputStream messageInputStream) throws IOException {
        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(this.marshallingConfiguration);
        final ByteInput byteInput = Marshalling.createByteInput(messageInputStream);
        unmarshaller.start(byteInput);

        return unmarshaller;
    }

    public Marshaller createMarshaller(final MessageOutputStream messageOutputStream) throws IOException {
        final Marshaller marshaller = marshallerFactory.createMarshaller(this.marshallingConfiguration);
        final ByteOutput byteOutput = Marshalling.createByteOutput(messageOutputStream);
        marshaller.start(byteOutput);

        return marshaller;
    }
}
