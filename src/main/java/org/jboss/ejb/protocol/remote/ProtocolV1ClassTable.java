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
import org.jboss.ejb.client.BasicSessionID;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBHandle;
import org.jboss.ejb.client.EJBHomeHandle;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.SerializedEJBInvocationHandler;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.TransactionID;
import org.jboss.ejb.client.UnknownSessionID;
import org.jboss.ejb.client.UserTransactionID;
import org.jboss.ejb.client.XidTransactionID;
import org.jboss.marshalling.ByteWriter;
import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.Unmarshaller;

import javax.ejb.CreateException;
import javax.ejb.DuplicateKeyException;
import javax.ejb.EJBAccessException;
import javax.ejb.EJBException;
import javax.ejb.EJBHome;
import javax.ejb.EJBMetaData;
import javax.ejb.EJBObject;
import javax.ejb.EJBTransactionRequiredException;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.FinderException;
import javax.ejb.Handle;
import javax.ejb.HomeHandle;
import javax.ejb.NoSuchEJBException;
import javax.ejb.NoSuchEntityException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.RemoveException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionRequiredException;
import javax.transaction.TransactionRolledbackException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Jaikiran Pai
 */
final class ProtocolV1ClassTable implements ClassTable {
    static final ProtocolV1ClassTable INSTANCE = new ProtocolV1ClassTable();

    private static final Map<Class<?>, ByteWriter> writers;
    /**
     * Do NOT change the order of this list.
     * Do NOT even remove entries from this list. If at all you no longer want a certain
     * class to be made available by this ClassTable, then add that class to the {@code deprecatedClassTableClasses}
     * set below.
     */
    private static final Class<?>[] classes = {
            V1EJBLocator.class,
            V1HomeLocator.class,
            V1StatelessLocator.class,
            V1StatefulLocator.class,
            V1EntityLocator.class,
            EJBHandle.class,
            EJBHomeHandle.class,
            SerializedEJBInvocationHandler.class,
            SessionID.class,
            UnknownSessionID.class,
            BasicSessionID.class,
            UserTransactionID.class,
            XidTransactionID.class,
            EJBHome.class,
            EJBObject.class,
            Handle.class,
            HomeHandle.class,
            EJBMetaData.class,
            RemoteException.class,
            NoSuchEJBException.class,
            NoSuchEntityException.class,
            CreateException.class,
            DuplicateKeyException.class,
            EJBAccessException.class,
            EJBException.class,
            EJBTransactionRequiredException.class,
            EJBTransactionRolledbackException.class,
            FinderException.class,
            RemoveException.class,
            ObjectNotFoundException.class,
            Future.class,
            SystemException.class,
            RollbackException.class,
            TransactionRequiredException.class,
            TransactionRolledbackException.class,
            NotSupportedException.class,
            InvalidTransactionException.class,
            Throwable.class,
            Exception.class,
            RuntimeException.class,
            StackTraceElement4.class,
            SessionID.Serialized.class,
            TransactionID.class,
            TransactionID.Serialized.class,
            Affinity.class,
            Affinity.NONE.getClass(),
            NodeAffinity.class,
            ClusterAffinity.class,
    };

    static {
        final Map<Class<?>, ByteWriter> map = new IdentityHashMap<Class<?>, ByteWriter>();
        final Class<?>[] classes = ProtocolV1ClassTable.classes;
        for (int i = 0, length = classes.length; i < length; i++) {
            final Class<?> clazz = classes[i];
            if (Throwable.class.isAssignableFrom(clazz)) {
                // don't write out exception types as class table items since they're out of our control
                continue;
            }
            map.put(clazz, new ByteWriter((byte) i));
        }
        writers = map;
    }

    @Override
    public Writer getClassWriter(final Class<?> clazz) throws IOException {
        return writers.get(clazz);
    }

    @Override
    public Class<?> readClass(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
        int idx = unmarshaller.readUnsignedByte();
        if (idx >= classes.length) {
            throw new ClassNotFoundException("ClassTable " + this.getClass().getName() + " cannot find a class for class index " + idx);
        }
        return classes[idx];
    }

    private ProtocolV1ClassTable() {
    }
}
