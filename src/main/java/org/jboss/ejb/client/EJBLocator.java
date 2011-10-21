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
import org.jboss.marshalling.FieldSetter;

/**
 * An identifier for an EJB proxy invocation target instance, suitable for use as a hash key or a serialized token.
 *
 * @param <T> the interface type
 */
public abstract class EJBLocator<T> extends Locator<T> {
    private static final long serialVersionUID = -7306257085240447972L;

    private final String appName;
    private final String moduleName;
    private final String beanName;
    private final String distinctName;
    private final transient int hashCode;

    private static final FieldSetter hashCodeSetter = FieldSetter.get(EJBLocator.class, "hashCode");

    /**
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param appName the application name
     * @param moduleName the module name
     * @param beanName the bean name
     * @param distinctName the distinct name
     */
    protected EJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) {
        super(viewType);
        if (appName == null) {
            throw new IllegalArgumentException("appName is null");
        }
        if (moduleName == null) {
            throw new IllegalArgumentException("moduleName is null");
        }
        if (beanName == null) {
            throw new IllegalArgumentException("beanName is null");
        }
        if (distinctName == null) {
            throw new IllegalArgumentException("distinctName is null");
        }
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.distinctName = distinctName;
        hashCode = calcHashCode(super.hashCode(), appName, moduleName, beanName, distinctName);
    }

    private static int calcHashCode(final int superHashCode, final String appName, final String moduleName, final String beanName, final String distinctName) {
        return superHashCode * 13 + (appName.hashCode() * 13 + (moduleName.hashCode() * 13 + (beanName.hashCode() * 13 + (distinctName.hashCode()))));
    }

    public String getAppName() {
        return appName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getBeanName() {
        return beanName;
    }

    public String getDistinctName() {
        return distinctName;
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
        return other instanceof EJBLocator && equals((EJBLocator<?>)other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Locator<?> other) {
        return other instanceof EJBLocator && equals((EJBLocator<?>)other);
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
        hashCodeSetter.setInt(this, calcHashCode(super.hashCode(), appName, moduleName, beanName, distinctName));
    }
}
