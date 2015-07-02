/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.protocol.remote;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKeys;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.TransactionID;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.Unmarshaller;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("serial")
final class ProtocolV3ObjectTable implements ObjectTable {
    static final ProtocolV3ObjectTable INSTANCE = new ProtocolV3ObjectTable();

    private static final Map<Object, AbstractWritingExternalizer> objectWriters;
    private static final Map<Class<?>, AbstractWritingExternalizer> classWriters;

    private static final AbstractWritingExternalizer[] extById;

    static {
        final Object[] simpleObjects = {
            TransactionID.PRIVATE_DATA_KEY,
            Affinity.NONE,
            Affinity.LOCAL,
            Affinity.WEAK_AFFINITY_CONTEXT_KEY,
            EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY,
            AttachmentKeys.TRANSACTION_ID_KEY,
            AttachmentKeys.WEAK_AFFINITY,
            AttachmentKeys.COMPRESS_RESPONSE,
            AttachmentKeys.RESPONSE_COMPRESSION_LEVEL,
            AttachmentKeys.TRANSACTION_KEY,
            AttachmentKeys.HINTS_DISABLED,
            AttachmentKeys.VIEW_CLASS_DATA_COMPRESSION_HINT_ATTACHMENT_KEY,
            AttachmentKeys.VIEW_METHOD_DATA_COMPRESSION_HINT_ATTACHMENT_KEY,
            Throwable.class.getName(),
            Exception.class.getName(),
            RuntimeException.class.getName(),
            "detailMessage",
            "cause",
            "stackTrace",
            "value",
            "suppressedExceptions",
            "ejbCreate",
            "ejbRemove",
            "ejbHome",
            "remove",
            "ejbActivate",
            "ejbPassivate",
            "ejbLoad",
            "ejbStore",
        };
        final AbstractWritingExternalizer[] extByIdTmp = new AbstractWritingExternalizer[simpleObjects.length];
        final Map<Object, AbstractWritingExternalizer> objMap = new HashMap<>();
        for (int i = 0, simpleObjectsLength = simpleObjects.length; i < simpleObjectsLength; i++) {
            ByteExternalizer ext = new ByteExternalizer(simpleObjects[i], i);
            extByIdTmp[i] = ext;
            objMap.put(ext.getObject(), ext);
        }
        // TODO: add class-based ext for LocalTransaction
        // TODO: add class-based ext for RemoteTransaction
        // TODO: add class-based ext for InputStream
        // TODO: add class-based ext for OutputStream
        extById = extByIdTmp;
        objectWriters = objMap;
        classWriters = Collections.emptyMap();
    }

    public Writer getObjectWriter(final Object object) throws IOException {
        Writer writer = objectWriters.get(object);
        if (writer == null) {
            writer = classWriters.get(object.getClass());
        }
        return writer;
    }

    public Object readObject(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
        int idx = unmarshaller.readUnsignedByte();
        if (idx >= extById.length) {
            throw new InvalidObjectException("ObjectTable " + this.getClass().getName() + " cannot find an object for object index " + idx);
        }
        return extById[idx].createExternal(Object.class, unmarshaller, null);
    }
}
