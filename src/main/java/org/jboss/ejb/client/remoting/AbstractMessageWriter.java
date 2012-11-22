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
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.reflect.SunReflectiveCreator;

import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author Jaikiran Pai
 */
class AbstractMessageWriter {

    protected void writeAttachments(final ObjectOutput output, final EJBClientInvocationContext invocationContext) throws IOException {
        // we write out the private (a.k.a JBoss specific) attachments as well as public invocation context data
        // (a.k.a user application specific data)
        final Map<Object, Object> privateAttachments = invocationContext.getAttachments();
        final Map<String, Object> contextData = invocationContext.getContextData();
        // no private or public data to write out
        if (contextData == null && privateAttachments.isEmpty()) {
            output.writeByte(0);
            return;
        }
        // write the attachment count which is the sum of invocation context data + 1 (since we write
        // out the private attachments under a single key with the value being the entire attachment map)
        int totalAttachments = contextData.size();
        if (!privateAttachments.isEmpty()) {
            totalAttachments++;
        }
        PackedInteger.writePackedInteger(output, totalAttachments);
        // write out public (application specific) context data
        for (Map.Entry<String, Object> invocationContextData : contextData.entrySet()) {
            output.writeObject(invocationContextData.getKey());
            output.writeObject(invocationContextData.getValue());
        }
        if (!privateAttachments.isEmpty()) {
            // now write out the JBoss specific attachments under a single key and the value will be the
            // entire map of JBoss specific attachments
            output.writeObject(EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY);
            output.writeObject(privateAttachments);
        }
    }

    /**
     * Creates and returns a {@link Marshaller} which is ready to be used for marshalling. The {@link Marshaller#start(org.jboss.marshalling.ByteOutput)}
     * will be invoked by this method, to use the passed {@link DataOutput dataOutput}, before returning the marshaller.
     *
     * @param marshallerFactory The marshaller factory
     * @param dataOutput        The {@link DataOutput} to which the data will be marshalled
     * @return
     * @throws IOException
     */
    protected Marshaller prepareForMarshalling(final MarshallerFactory marshallerFactory, final DataOutput dataOutput) throws IOException {
        final Marshaller marshaller = this.getMarshaller(marshallerFactory);
        final OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                final int byteToWrite = b & 0xff;
                dataOutput.write(byteToWrite);
            }

            @Override
            public void write(final byte[] b, final int off, final int len) throws IOException {
                dataOutput.write(b, off, len);
            }

            @Override
            public void write(final byte[] b) throws IOException {
                dataOutput.write(b);
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
     * @param marshallerFactory The marshaller factory
     * @return
     * @throws IOException
     */
    private Marshaller getMarshaller(final MarshallerFactory marshallerFactory) throws IOException {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
        marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
        marshallingConfiguration.setVersion(2);
        marshallingConfiguration.setSerializedCreator(new SunReflectiveCreator());
        return marshallerFactory.createMarshaller(marshallingConfiguration);
    }

}
