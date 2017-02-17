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

package org.jboss.ejb.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.jboss.marshalling.FieldSetter;
import org.wildfly.common.Assert;

/**
 * A locator for a specific EJB method.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBMethodLocator implements Serializable {

    private static final long serialVersionUID = -1387266421025030533L;

    private final String methodName;
    private final String[] parameterTypeNames;
    private final transient int hashCode;

    private static final FieldSetter hashCodeSetter = FieldSetter.get(EJBMethodLocator.class, "hashCode");

    /**
     * Construct a new instance.
     *
     * @param methodName the method name (must not be {@code null})
     * @param parameterTypeNames the parameter type names array (may be empty, must not be {@code null} nor contain {@code null} elements)
     */
    public EJBMethodLocator(final String methodName, final String... parameterTypeNames) {
        this(methodName, parameterTypeNames, true);
    }

    private EJBMethodLocator(final String methodName, final String[] parameterTypeNames, boolean copy) {
        Assert.checkNotNullParam("methodName", methodName);
        Assert.checkNotNullParam("parameterTypeNames", parameterTypeNames);
        this.methodName = methodName;
        final int length = parameterTypeNames.length;
        String[] clone = this.parameterTypeNames = copy && length > 0 ? parameterTypeNames.clone() : parameterTypeNames;
        for (int i = 0; i < length; i++) {
            Assert.checkNotNullArrayParam("parameterTypeNames", i, clone[i]);
        }
        hashCode = calcHashCode(methodName, parameterTypeNames);
    }

    /**
     * Get a method locator for the given reflection method.
     *
     * @param method the reflection method (must not be {@code null})
     * @return the method locator (not {@code null})
     */
    public static EJBMethodLocator forMethod(final Method method) {
        Assert.checkNotNullParam("method", method);
        final String name = method.getName();
        final Class<?>[] parameterTypes = method.getParameterTypes();
        final int length = parameterTypes.length;
        final String[] paramNames = new String[length];
        for (int i = 0; i < length; i ++) {
            paramNames[i] = parameterTypes[i].getName();
        }
        return new EJBMethodLocator(name, paramNames, false);
    }

    private static int calcHashCode(final String methodName, final String[] parameterTypeNames) {
        return methodName.hashCode() + 13 * Arrays.hashCode(parameterTypeNames);
    }

    /**
     * Get the method name.
     *
     * @return the method name (not {@code null})
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Get the parameter count.
     *
     * @return the parameter count
     */
    public int getParameterCount() {
        return parameterTypeNames.length;
    }

    /**
     * Get the name of the parameter at the given index.
     *
     * @return the name of the parameter at the given index
     */
    public String getParameterTypeName(int index) {
        return parameterTypeNames[index];
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        Assert.checkNotNullParam("methodName", methodName);
        Assert.checkNotNullParam("parameterTypeNames", parameterTypeNames);
        for (int i = 0; i < parameterTypeNames.length; i++) {
            Assert.checkNotNullArrayParam("parameterTypeNames", i, parameterTypeNames[i]);
        }
        hashCodeSetter.setInt(this, calcHashCode(methodName, parameterTypeNames));
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof EJBMethodLocator && equals((EJBMethodLocator)other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(EJBMethodLocator other) {
        return this == other || other != null && hashCode == other.hashCode && methodName.equals(other.methodName) && Arrays.equals(parameterTypeNames, other.parameterTypeNames);
    }

    /**
     * Get the hash code.
     *
     * @return the hash code
     */
    public int hashCode() {
        return hashCode;
    }
}
