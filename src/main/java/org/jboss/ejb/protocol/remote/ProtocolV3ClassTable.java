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
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
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

import org.jboss.ejb.client.AbstractEJBMetaData;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.AttachmentKey;
import org.jboss.ejb.client.BasicSessionID;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBClientPermission;
import org.jboss.ejb.client.EJBHandle;
import org.jboss.ejb.client.EJBHomeHandle;
import org.jboss.ejb.client.EJBHomeLocator;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBMethodLocator;
import org.jboss.ejb.client.EntityEJBLocator;
import org.jboss.ejb.client.EntityEJBMetaData;
import org.jboss.ejb.client.NodeAffinity;
import org.jboss.ejb.client.SerializedEJBInvocationHandler;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatefulEJBMetaData;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.StatelessEJBMetaData;
import org.jboss.ejb.client.TransactionID;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.ejb.client.UnknownSessionID;
import org.jboss.ejb.client.UserTransactionID;
import org.jboss.ejb.client.XidTransactionID;
import org.jboss.marshalling.ByteWriter;
import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.Unmarshaller;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Jaikiran Pai
 */
final class ProtocolV3ClassTable implements ClassTable {
    static final ProtocolV3ClassTable INSTANCE = new ProtocolV3ClassTable();

    private static final Map<Class<?>, ByteWriter> writers;
    /**
     * Do NOT change the order of this list.
     * Do NOT even remove entries from this list. If at all you no longer want a certain
     * class to be made available by this ClassTable, then add that class to the {@code deprecatedClassTableClasses}
     * set below.
     */
    private static final Class<?>[] classes = {
            EJBLocator.class,
            EJBHomeLocator.class,
            StatelessEJBLocator.class,
            StatefulEJBLocator.class,
            EntityEJBLocator.class,
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
            StackTraceElement.class,
            SessionID.Serialized.class,
            TransactionID.class,
            TransactionID.Serialized.class,
            Affinity.class,
            NodeAffinity.class,
            ClusterAffinity.class,
            URIAffinity.class,
            EJBMethodLocator.class,
            AbstractEJBMetaData.class,
            StatelessEJBMetaData.class,
            StatefulEJBMetaData.class,
            EntityEJBMetaData.class,
            AttachmentKey.class,
            EJBClientPermission.class,
            AsyncResult.class,

    };

    static {
        final Map<Class<?>, ByteWriter> map = new IdentityHashMap<Class<?>, ByteWriter>();
        final Class<?>[] classes = ProtocolV3ClassTable.classes;
        for (int i = 0, length = classes.length; i < length; i++) {
            map.put(classes[i], new ByteWriter((byte) i));
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

    private ProtocolV3ClassTable() {
    }
}
