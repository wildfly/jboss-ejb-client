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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

import org.jboss.marshalling.FieldSetter;

/**
 * An identifier for an EJB proxy invocation target instance, suitable for use as a hash key or a serialized token.
 *
 * @param <T> the interface type
 */
public abstract class EJBLocator<T> implements Serializable {
    private static final long serialVersionUID = -7306257085240447972L;

    private final Class<T> viewType;
    private final String appName;
    private final String moduleName;
    private final String beanName;
    private final String distinctName;
    private final Affinity affinity;
    private final transient Class<? extends T> proxyClass;
    private final transient Constructor<? extends T> proxyConstructor;
    private final transient int hashCode;

    private static final FieldSetter hashCodeSetter = FieldSetter.get(EJBLocator.class, "hashCode");
    private static final FieldSetter proxyClassSetter = FieldSetter.get(EJBLocator.class, "proxyClass");
    private static final FieldSetter proxyConstructorSetter = FieldSetter.get(EJBLocator.class, "proxyConstructor");

    EJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        if (viewType == null) {
            throw Logs.MAIN.paramCannotBeNull("viewType");
        }
        if (appName == null) {
            throw Logs.MAIN.paramCannotBeNull("appName");
        }
        if (moduleName == null) {
            throw Logs.MAIN.paramCannotBeNull("moduleName");
        }
        if (beanName == null) {
            throw Logs.MAIN.paramCannotBeNull("beanName");
        }
        if (distinctName == null) {
            throw Logs.MAIN.paramCannotBeNull("distinctName");
        }
        this.viewType = viewType;
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.distinctName = distinctName;
        this.affinity = affinity == null ? Affinity.NONE : affinity;
        proxyClass = Proxy.getProxyClass(viewType.getClassLoader(), viewType).asSubclass(viewType);
        try {
            proxyConstructor = proxyClass.getConstructor(InvocationHandler.class);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError("No valid constructor found on proxy class");
        }
        hashCode = calcHashCode(viewType, appName, moduleName, beanName, distinctName);
    }

    private static int calcHashCode(final Class<?> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) {
        return viewType.hashCode() * 13 + (appName.hashCode() * 13 + (moduleName.hashCode() * 13 + (beanName.hashCode() * 13 + (distinctName.hashCode()))));
    }

    /**
     * Get the view type of this locator.
     *
     * @return the view type
     */
    public Class<T> getViewType() {
        return viewType;
    }

    /**
     * Get the application name.
     *
     * @return the application name
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Get the module name.
     *
     * @return the module name
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Get the EJB bean name.
     *
     * @return the EJB bean name
     */
    public String getBeanName() {
        return beanName;
    }

    /**
     * Get the module distinct name.
     *
     * @return the module distinct name
     */
    public String getDistinctName() {
        return distinctName;
    }

    /**
     * Get the locator affinity.
     *
     * @return the locator affinity
     */
    public Affinity getAffinity() {
        return affinity;
    }

    /**
     * Get the hash code for this instance.
     *
     * @return the hash code for this instance
     */
    public int hashCode() {
        return hashCode;
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof EJBLocator && equals((EJBLocator<?>) other);
    }

    /**
     * Get the proxy class for this locator.
     *
     * @return the proxy class
     */
    public Class<? extends T> getProxyClass() {
        return proxyClass;
    }

    /**
     * Get the proxy class constructor for this locator.  A proxy class constructor accepts a single
     * argument of type {@link InvocationHandler}.
     *
     * @return the proxy constructor
     */
    public Constructor<? extends T> getProxyConstructor() {
        return proxyConstructor;
    }

    /**
     * Create a proxy instance using the cached proxy class.
     *
     * @param invocationHandler the invocation handler to use
     * @return the proxy instance
     */
    public T createProxyInstance(InvocationHandler invocationHandler) {
        if (invocationHandler == null) {
            throw Logs.MAIN.paramCannotBeNull("Invocation handler");
        }
        try {
            return proxyConstructor.newInstance(invocationHandler);
        } catch (InstantiationException e) {
            throw new InstantiationError(e.getMessage());
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            throw new UndeclaredThrowableException(e.getCause());
        }
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(EJBLocator<?> other) {
        return this == other || other != null && hashCode == other.hashCode
                && appName.equals(other.appName)
                && moduleName.equals(other.moduleName)
                && beanName.equals(other.beanName)
                && distinctName.equals(other.distinctName);
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        final Class<? extends T> proxyType = Proxy.getProxyClass(viewType.getClassLoader(), viewType).asSubclass(viewType);
        final Constructor<? extends T> proxyConstructor;
        try {
            proxyConstructor = proxyType.getConstructor(InvocationHandler.class);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError("No valid constructor found on proxy class");
        }
        proxyClassSetter.set(this, proxyType);
        proxyConstructorSetter.set(this, proxyConstructor);
        hashCodeSetter.setInt(this, calcHashCode(viewType, appName, moduleName, beanName, distinctName));
    }
}
