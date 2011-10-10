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

import org.jboss.ejb.client.EJBClientInvocationContext;

import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Responsible for writing out a method invocation message, as per the EJB remoting client protocol specification to a stream.
 * <p/>
 * User: Jaikiran Pai
 */
class MethodInvocationMessageWriter extends AbstractMessageWriter {

    private static final byte METHOD_INVOCATION_HEADER = 0x03;

    private static final char METHOD_PARAM_TYPE_SEPARATOR = ',';

    private final byte protocolVersion;

    private final String marshallingStrategy;

    MethodInvocationMessageWriter(final byte protocolVersion, final String marshallingStrategy) {
        this.protocolVersion = protocolVersion;
        this.marshallingStrategy = marshallingStrategy;
    }

    /**
     * Writes out a message invocation request to the passed <code>output</code>
     *
     * @param output            The {@link DataOutput} to which the message will be written
     * @param invocationId      The invocation id
     * @param invocationContext The EJB client invocation context
     * @throws IOException If there's a problem writing out to the {@link DataOutput}
     */
    void writeMessage(final DataOutput output, final short invocationId, final EJBClientInvocationContext<RemotingAttachments> invocationContext) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Cannot write to null output");
        }

        final Method invokedMethod = invocationContext.getInvokedMethod();
        final Object[] methodParams = invocationContext.getParameters();

        // write the header
        output.writeByte(METHOD_INVOCATION_HEADER);
        // write the invocation id
        output.writeShort(invocationId);
        // method name
        output.writeUTF(invokedMethod.getName());
        // param types
        final Class<?>[] methodParamTypes = invokedMethod.getParameterTypes();
        final StringBuilder methodSignature = new StringBuilder();
        for (int i = 0; i < methodParamTypes.length; i++) {
            methodSignature.append(methodParamTypes[i].getName());
            if (i != methodParamTypes.length - 1) {
                methodSignature.append(METHOD_PARAM_TYPE_SEPARATOR);
            }
        }
        // write the method signature
        output.writeUTF(methodSignature.toString());
        // write out the attachments
        writeAttachments(output, invocationContext.getReceiverSpecific());

        // marshall the locator and method params
        final Marshaller marshaller = MarshallerFactory.createMarshaller(marshallingStrategy);
        marshaller.start(output);
        // write the invocation locator
        marshaller.writeObject(invocationContext.getLocator());
        // and the parameters
        if (methodParams != null && methodParams.length > 0) {
            for (final Object methodParam : methodParams) {
                marshaller.writeObject(methodParam);
            }
            marshaller.finish();
        }

    }
}
