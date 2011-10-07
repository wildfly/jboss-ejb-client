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

import java.io.Serializable;

/**
 * An identifier for a proxy invocation target instance, suitable for use as a hash key or a serialized token.
 *
 * @param <T> the interface type
 */
public abstract class Locator<T> implements Serializable {

    private static final long serialVersionUID = -5023698945241978131L;

    private final Class<T> interfaceType;

    /**
     * Construct a new instance.
     *
     * @param interfaceType the interface type
     */
    protected Locator(final Class<T> interfaceType) {
        this.interfaceType = interfaceType;
    }

    /**
     * Get the interface type type for this locator.
     *
     * @return the interface type class object
     */
    public Class<T> getInterfaceType() {
        return interfaceType;
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof Locator && equals((Locator<?>)other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Locator<?> other) {
        return this == other || other != null && interfaceType == other.interfaceType;
    }

    /**
     * Get the hash code for this instance.
     *
     * @return the hash code for this instance
     */
    public int hashCode() {
        return interfaceType.hashCode();
    }

    /**
     * Get the application name.
     *
     * @return the application name
     */
    public abstract String getAppName();

    /**
     * Get the module name.
     *
     * @return the module name
     */
    public abstract String getModuleName();

    /**
     * Get the EJB name.
     *
     * @return the EJB name
     */
    public abstract String getBeanName();

    /**
     * Get the module distinct name.
     *
     * @return the module distinct name
     */
    public abstract String getDistinctName();
}
