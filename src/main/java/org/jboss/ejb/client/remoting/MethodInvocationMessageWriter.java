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
 * User: jpai
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

    public void writeMessage(final DataOutput output, final short invocationId, final EJBClientInvocationContext<RemotingAttachments> invocationContext) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Cannot write to null output");
        }

        final String appName = invocationContext.getAppName();
        final String moduleName = invocationContext.getModuleName();
        final String distinctName = invocationContext.getDistinctName();
        final String beanName = invocationContext.getBeanName();
        final String viewClassName = invocationContext.getViewClass().getName();
        final Method invokedMethod = invocationContext.getInvokedMethod();
        final Object[] methodParams = invocationContext.getParameters();

        // write the header
        output.writeByte(METHOD_INVOCATION_HEADER);
        // write the invocation id
        output.writeShort(invocationId);
        // write the ejb identifier info
        if (appName != null) {
            output.writeUTF(appName);
        } else {
            output.writeUTF("");
        }
        output.writeUTF(moduleName);
        if (distinctName != null) {
            output.writeUTF(distinctName);
        } else {
            output.writeUTF("");
        }
        // bean name
        output.writeUTF(beanName);
        // view class
        output.writeUTF(viewClassName);
        // method name
        output.writeUTF(invokedMethod.getName());
        // param types
        final Class<?>[] methodParamTypes = invokedMethod.getParameterTypes();
        final StringBuffer methodSignature = new StringBuffer();
        for (int i = 0; i < methodParamTypes.length; i++) {
            methodSignature.append(methodParamTypes[i].getName());
            if (i != methodParamTypes.length - 1) {
                methodSignature.append(METHOD_PARAM_TYPE_SEPARATOR);
            }
        }
        // write the method signature
        output.writeUTF(methodSignature.toString());
        // write out the attachments
        this.writeAttachments(output, invocationContext.getReceiverSpecific());

        if (methodParams != null && methodParams.length > 0) {
            // marshall the method params
            final Marshaller marshaller = MarshallerFactory.createMarshaller(this.marshallingStrategy);
            marshaller.start(output);
            for (int i = 0; i < methodParams.length; i++) {
                marshaller.writeObject(methodParams[i]);
            }
            marshaller.finish();
        }

    }
}
