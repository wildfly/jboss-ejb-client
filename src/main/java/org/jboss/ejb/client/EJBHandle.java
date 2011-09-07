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

package org.jboss.ejb.client;

import java.rmi.RemoteException;

import javax.ejb.EJBObject;
import javax.ejb.Handle;

/**
 * Base class for serializable EJB handles.
 *
 * @param <T> the EJB remote interface type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class EJBHandle<T extends EJBObject> extends EJBGenericHandle<T> implements Handle {

    private static final long serialVersionUID = -4870688692508067759L;

    EJBHandle(final Class<T> type, final String appName, final String moduleName, final String distinctName, final String beanName) {
        super(type, appName, moduleName, distinctName, beanName);
    }

    /** {@inheritDoc} */
    public final T getEJBObject() throws RemoteException {
        return super.getProxy();
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof EJBHandle && equals((EJBHandle<?>)other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EJBGenericHandle<?> other) {
        return other instanceof EJBHandle && equals((EJBHandle<?>)other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(EJBHandle<?> other) {
        return this == other || other != null && super.equals(other);
    }

    public int hashCode() {
        return super.hashCode();
    }
}
