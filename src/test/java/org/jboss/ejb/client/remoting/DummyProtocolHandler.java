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

import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.Locator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * User: jpai
 */
public class DummyProtocolHandler {


    private static final char METHOD_PARAM_TYPE_SEPARATOR = ',';

    private final String marshallerType;

    private static final byte HEADER_SESSION_OPEN_RESPONSE = 0x02;
    private static final byte HEADER_INVOCATION_REQUEST = 0x03;
    private static final byte HEADER_INVOCATION_CANCEL_REQUEST = 0x04;
    private static final byte HEADER_INVOCATION_RESPONSE = 0x05;
    private static final byte HEADER_INVOCATION_FAILURE = 0x06;
    private static final byte HEADER_MODULE_AVAILABLE = 0x08;
    private static final byte HEADER_MODULE_UNAVAILABLE = 0x09;

    public DummyProtocolHandler(final String marshallerType) {
        this.marshallerType = marshallerType;
    }


//  void writeSessionOpenResponse(final DataOutput output, final short invocationId, final byte[] sessionId,
//                                         final Attachment[] attachments) throws IOException {
//        if (output == null) {
//            throw new IllegalArgumentException("Cannot write to null output");
//        }
//        if (sessionId == null) {
//            throw new IllegalArgumentException("Session id cannot be null while writing out session open response");
//        }
//        // write the session open response header
//        output.write(MessageType.SESSION_OPEN_RESPONSE.getHeader());
//        // write the invocation id
//        output.writeShort(invocationId);
//        // write session id length
//        PackedInteger.writePackedInteger(output, sessionId.length);
//        // write the session id
//        output.write(sessionId);
//        // write the attachments
//        this.writeAttachments(output, attachments);
//
//    }


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
        final UnMarshaller unMarshaller = MarshallerFactory.createUnMarshaller(this.marshallerType);
        unMarshaller.start(input, cl);
        Locator ejbLocator = null;
        try {
            ejbLocator = (Locator) unMarshaller.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < methodParamTypes.length; i++) {
            try {
                methodParams[i] = unMarshaller.readObject();
            } catch (ClassNotFoundException cnfe) {
                throw new RuntimeException(cnfe);
            }
        }
        unMarshaller.finish();

        return new MethodInvocationRequest(invocationId, ejbLocator.getAppName(), ejbLocator.getModuleName(), ejbLocator.getDistinctName(), ejbLocator.getBeanName(),
                ejbLocator.getInterfaceType().getName(), methodName, methodParamTypes, methodParams, attachments);
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
        final Marshaller marshaller = MarshallerFactory.createMarshaller(this.marshallerType);
        marshaller.start(output);
        marshaller.writeObject(result);
        marshaller.finish();
    }

//    @Override
//    public void writeModuleAvailability(final DataOutput output, final EJBModuleIdentifier[] ejbModuleIdentifiers) throws IOException {
//        if (output == null) {
//            throw new IllegalArgumentException("Cannot write to null output");
//        }
//        if (ejbModuleIdentifiers == null) {
//            throw new IllegalArgumentException("EJB module identifiers cannot be null");
//        }
//        // write the header
//        output.write(MessageType.MODULE_AVAILABLE.getHeader());
//        // write the count
//        PackedInteger.writePackedInteger(output, ejbModuleIdentifiers.length);
//        // write the app/module names
//        for (int i = 0; i < ejbModuleIdentifiers.length; i++) {
//            // write the app name
//            final String appName = ejbModuleIdentifiers[i].getAppName();
//            if (appName == null) {
//                // write out a empty string
//                output.writeUTF("");
//            } else {
//                output.writeUTF(appName);
//            }
//            // write the module name
//            output.writeUTF(ejbModuleIdentifiers[i].getModuleName());
//            // write the distinct name
//            final String distinctName = ejbModuleIdentifiers[i].getDistinctName();
//            if (distinctName == null) {
//                // write out an empty string
//                output.writeUTF("");
//            } else {
//                output.writeUTF(distinctName);
//            }
//        }
//    }

//    @Override
//    public MethodInvocationResponse readMethodInvocationResponse(final DataInput input, final InvocationClassLoaderResolver classLoaderResolver) throws IOException {
//        if (input == null) {
//            throw new IllegalArgumentException("Cannot read from null input");
//        }
//
//        // read the invocation id
//        final short invocationId = input.readShort();
//        final UnMarshaller unMarshaller = MarshallerFactory.createUnMarshaller(this.marshallerType);
//        // read the attachments
//        this.readAttachments(input);
//        // check exception flag
//        boolean exception = input.readBoolean();
//        final ClassLoader classLoader = classLoaderResolver.resolveClassLoader(invocationId);
//        unMarshaller.start(input, classLoader);
//
//        Throwable t = null;
//        Object result = null;
//        try {
//            if (exception) {
//                // read the excption
//                t = (Throwable) unMarshaller.readObject();
//            } else {
//                // read the result
//                result = unMarshaller.readObject();
//            }
//        } catch (ClassNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//        unMarshaller.finish();
//
//        return new MethodInvocationResponse(invocationId, result, t);
//    }

//    @Override
//    public Message readSessionOpenRequest(DataInput input) throws IOException {
//        if (input == null) {
//            throw new IllegalArgumentException("Cannot read from null input");
//        }
//
//        // read the invocation id
//        final short invocationId = input.readShort();
//        // read the ejb identifier info
//        // first read a flag to check whether app name is included
//        final boolean appNamePresent = input.readBoolean();
//        String appName = null;
//        if (appNamePresent) {
//            appName = input.readUTF();
//        }
//        final String moduleName = input.readUTF();
//        final String beanName = input.readUTF();
//        final String viewClassName = input.readUTF();
//        // read the attachments
//        final Attachment[] attachments = this.readAttachments(input);
//
//        return new Message(invocationId, appName, moduleName, beanName, viewClassName, attachments);
//    }

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

}
