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

import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;

import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;

/**
 * Responsible for writing out a method invocation message, as per the EJB remoting client protocol specification to a stream.
 * <p/>
 * User: Jaikiran Pai
 */
class MethodInvocationMessageWriter extends AbstractMessageWriter {

    private static final byte METHOD_INVOCATION_HEADER = 0x03;

    private static final char METHOD_PARAM_TYPE_SEPARATOR = ',';

    private final byte protocolVersion;

    private final MarshallerFactory marshallerFactory;

    MethodInvocationMessageWriter(final byte protocolVersion, final MarshallerFactory marshallerFactory) {
        this.protocolVersion = protocolVersion;
        this.marshallerFactory = marshallerFactory;
    }

    /**
     * Writes out a method invocation request to the passed <code>output</code>
     *
     * @param output            The {@link DataOutput} to which the message will be written
     * @param invocationId      The invocation id
     * @param invocationContext The EJB client invocation context
     * @throws IOException If there's a problem writing out to the {@link DataOutput}
     */
    void writeMessage(final DataOutput output, final short invocationId, final EJBClientInvocationContext invocationContext) throws IOException {
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

        // marshall the locator and method params
        final Marshaller marshaller = this.prepareForMarshalling(this.marshallerFactory, output);
        final EJBLocator locator = invocationContext.getLocator();
        // Write out the app/module/distinctname/bean name combination using the writeObject method
        // *and* using the objects returned by a call to the locator.getXXX() methods,
        // so that later when the locator is written out later, the marshalling impl uses back-references
        // to prevent duplicating this app/module/bean/distinctname (which is already present in the locator) twice
        marshaller.writeObject(locator.getAppName());
        marshaller.writeObject(locator.getModuleName());
        marshaller.writeObject(locator.getDistinctName());
        marshaller.writeObject(locator.getBeanName());
        // write the invocation locator
        marshaller.writeObject(locator);
        // and the parameters
        if (methodParams != null && methodParams.length > 0) {
            for (final Object methodParam : methodParams) {
                marshaller.writeObject(methodParam);
            }
        }
        // write out the attachments
        this.writeAttachments(marshaller, invocationContext);
        // finish marshalling
        marshaller.finish();

    }
}
