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

import org.jboss.marshalling.AbstractClassResolver;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.reflect.SunReflectiveCreator;
import org.jboss.remoting3.MessageInputStream;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jpai
 */
abstract class ProtocolMessageHandler {

    protected abstract void processMessage(final MessageInputStream messageInputStream) throws IOException;

    protected Map<String, Object> readAttachments(final ObjectInput input) throws IOException, ClassNotFoundException {
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

    short getInvocationId(final DataInput input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Cannot read from null input");
        }

        // read the invocation id
        return input.readShort();
    }

    /**
     * Creates and returns a {@link org.jboss.marshalling.Unmarshaller} which is ready to be used for unmarshalling. The {@link org.jboss.marshalling.Unmarshaller#start(org.jboss.marshalling.ByteInput)}
     * will be invoked by this method, to use the passed {@link DataInput dataInput}, before returning the unmarshaller.
     *
     * @param marshallerFactory The marshaller factory
     * @param dataInput         The data input from which to unmarshall
     * @return
     * @throws IOException
     */
    protected Unmarshaller prepareForUnMarshalling(final MarshallerFactory marshallerFactory, final DataInputStream dataInput) throws IOException {
        final Unmarshaller unmarshaller = this.getUnMarshaller(marshallerFactory);
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

            @Override
            public int read(final byte[] b) throws IOException {
                return dataInput.read(b);
            }

            @Override
            public int read(final byte[] b, final int off, final int len) throws IOException {
                return dataInput.read(b, off, len);
            }
        };
        final ByteInput byteInput = Marshalling.createByteInput(is);
        // start the unmarshaller
        unmarshaller.start(byteInput);

        return unmarshaller;
    }

    /**
     * Glue two stack traces together.
     *
     * @param exception      the exception which occurred in another thread
     * @param userStackTrace the stack trace of the current thread from {@link Thread#getStackTrace()}
     * @param trimCount      the number of frames to trim
     * @param msg            the message to use
     */
    protected void glueStackTraces(final Throwable exception, final StackTraceElement[] userStackTrace, final int trimCount, final String msg) {
        final StackTraceElement[] est = exception.getStackTrace();
        final StackTraceElement[] fst = Arrays.copyOf(est, est.length + userStackTrace.length - trimCount + 1);
        fst[est.length] = new StackTraceElement("..." + msg + "..", "", null, -1);
        System.arraycopy(userStackTrace, trimCount, fst, est.length + 1, userStackTrace.length - trimCount);
        exception.setStackTrace(fst);
    }


    /**
     * Creates and returns a {@link Unmarshaller}
     *
     * @param marshallerFactory The marshaller factory
     * @return
     * @throws IOException
     */
    private Unmarshaller getUnMarshaller(final MarshallerFactory marshallerFactory) throws IOException {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
        marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
        marshallingConfiguration.setClassResolver(TCCLClassResolver.INSTANCE);
        marshallingConfiguration.setSerializedCreator(new SunReflectiveCreator());
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
