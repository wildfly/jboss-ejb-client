/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.ejb.protocol.remote;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKeys;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.TransactionID;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.Unmarshaller;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ProtocolV1ObjectTable implements ObjectTable {
    static final ProtocolV1ObjectTable INSTANCE = new ProtocolV1ObjectTable();

    private static final Map<Object, ByteWriter> writers;
    /**
     * Do NOT change the order of this list.
     */
    private static final Object[] objects = {
            TransactionID.PRIVATE_DATA_KEY,
            Affinity.NONE,
            Affinity.WEAK_AFFINITY_CONTEXT_KEY,
            EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY,
            AttachmentKeys.TRANSACTION_ID_KEY,
            AttachmentKeys.WEAK_AFFINITY,
            AttachmentKeys.COMPRESS_RESPONSE,
            AttachmentKeys.RESPONSE_COMPRESSION_LEVEL
    };

    static {
        final Map<Object, ByteWriter> map = new IdentityHashMap<Object, ByteWriter>();
        for (int i = 0, length = objects.length; i < length; i++) {
            map.put(objects[i], new ByteWriter((byte) i));
        }
        writers = map;
    }

    public Writer getObjectWriter(final Object object) throws IOException {
        return writers.get(object);
    }

    public Object readObject(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
        int idx = unmarshaller.readUnsignedByte();
        if (idx >= objects.length) {
            throw new InvalidObjectException("ObjectTable " + this.getClass().getName() + " cannot find an object for object index " + idx);
        }
        return objects[idx];
    }

    static final class ByteWriter implements Writer {
        final byte[] bytes;

        ByteWriter(final byte... bytes) {
            this.bytes = bytes;
        }

        public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
            marshaller.write(bytes);
        }
    }
}
