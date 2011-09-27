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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import org.jboss.marshalling.FieldSetter;

/**
 * A generic serializable handle for an EJB.
 *
 * @param <T> the target type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class EJBGenericHandle<T> implements Serializable {

    private static final long serialVersionUID = -8321219873002436838L;

    private final Class<T> type;
    private final String appName;
    private final String moduleName;
    private final String distinctName;
    private final String beanName;
    private final transient int hashCode;

    private static final FieldSetter hashCodeSetter = FieldSetter.get(EJBGenericHandle.class, "hashCode");

    EJBGenericHandle(final Class<T> type, final String appName, final String moduleName, final String distinctName, final String beanName) {
        this.type = type;
        this.appName = appName;
        this.moduleName = moduleName;
        this.distinctName = distinctName;
        this.beanName = beanName;
        hashCode = calcHashCode(type, appName, moduleName, distinctName, beanName);
    }

    private static int calcHashCode(final Class<?> type, final String appName, final String moduleName, final String distinctName, final String beanName) {
        return (((type.hashCode() * 13 + appName.hashCode()) * 13 + moduleName.hashCode()) * 13 + distinctName.hashCode()) * 13 + beanName.hashCode();
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof EJBGenericHandle && equals((EJBGenericHandle<?>)other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(EJBGenericHandle<?> other) {
        return this == other || other != null
                && type == other.type
                && appName.equals(other.appName)
                && moduleName.equals(other.moduleName)
                && distinctName.equals(other.distinctName)
                && beanName.equals(other.beanName);
    }

    /**
     * Get a new proxy for this handle object.
     *
     * @return the proxy
     */
    protected final T getProxy() {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, getInvocationHandler()));
    }

    /**
     * Get a new invocation handler for this handle object.
     *
     * @return the invocation handler
     */
    protected EJBInvocationHandler getInvocationHandler() {
        return new EJBInvocationHandler(type, appName, moduleName, distinctName, beanName);
    }

    public int hashCode() {
        return hashCode;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        hashCodeSetter.setInt(this, calcHashCode(type, appName, moduleName, distinctName, beanName));
    }
}
