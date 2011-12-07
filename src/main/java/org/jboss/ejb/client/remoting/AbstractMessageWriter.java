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
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.Map;

import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.marshalling.AbstractClassResolver;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;

/**
 * @author Jaikiran Pai
 */
class AbstractMessageWriter {

    protected void writeAttachments(final ObjectOutput output, final EJBClientInvocationContext invocationContext) throws IOException {
        final Map<String, Object> contextData = invocationContext.getContextData();
        if (contextData == null) {
            output.writeByte(0);
            return;
        }
        // write the attachment count
        PackedInteger.writePackedInteger(output, contextData.size());
        for (Map.Entry<String, Object> entry : contextData.entrySet()) {
            output.writeObject(entry.getKey());
            output.writeObject(entry.getValue());
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

        return marshallerFactory.createMarshaller(marshallingConfiguration);
    }

}
