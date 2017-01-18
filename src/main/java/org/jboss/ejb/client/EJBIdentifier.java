/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
import java.util.Objects;

import org.wildfly.common.Assert;

/**
 * An identifier for an EJB located within a container.  This identifier only names the EJB; it does not specify
 * a view, which must be done using the {@link EJBLocator} family of types.
 * <p>
 * EJB identifiers are suitable for use as hash keys.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBIdentifier implements Serializable {
    private static final long serialVersionUID = 7065644117552778408L;

    private final String appName;
    private final String moduleName;
    private final String beanName;
    private final String distinctName;
    private transient int hashCode;

    /**
     * Construct a new instance.
     *
     * @param appName the application name (must not be {@code null})
     * @param moduleName the module name (must not be {@code null} or empty)
     * @param beanName the bean name (must not be {@code null} or empty)
     * @param distinctName the distinct name (must not be {@code null})
     */
    public EJBIdentifier(final String appName, final String moduleName, final String beanName, final String distinctName) {
        Assert.checkNotNullParam("appName", appName);
        Assert.checkNotNullParam("moduleName", moduleName);
        Assert.checkNotEmptyParam("moduleName", moduleName);
        Assert.checkNotNullParam("beanName", beanName);
        Assert.checkNotEmptyParam("beanName", beanName);
        Assert.checkNotNullParam("distinctName", distinctName);
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.distinctName = distinctName;
    }

    /**
     * Get the application name, which may be empty.
     *
     * @return the application name (not {@code null})
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Get the module name.
     *
     * @return the module name (not {@code null})
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Get the bean name.
     *
     * @return the bean name (not {@code null})
     */
    public String getBeanName() {
        return beanName;
    }

    /**
     * Get the distinct name.
     *
     * @return the distinct name (not {@code null})
     */
    public String getDistinctName() {
        return distinctName;
    }

    /**
     * Determine if this EJB identifier is equal to the given object.
     *
     * @param other the object to test
     * @return {@code true} if the object is equal to this one, {@code false} otherwise
     */
    public boolean equals(final Object other) {
        return other instanceof EJBIdentifier && equals((EJBIdentifier) other);
    }

    /**
     * Determine if this EJB identifier is equal to the given object.
     *
     * @param other the object to test
     * @return {@code true} if the object is equal to this one, {@code false} otherwise
     */
    public boolean equals(final EJBIdentifier other) {
        return other != null && (
            other == this ||
                other.hashCode() == hashCode() &&
                Objects.equals(appName, other.appName) &&
                Objects.equals(moduleName, other.moduleName) &&
                Objects.equals(beanName, other.beanName) &&
                Objects.equals(distinctName, other.distinctName)
        );
    }

    /**
     * Get the hash code of this identifier.
     *
     * @return the hash code of this identifier
     */
    public int hashCode() {
        int hashCode = this.hashCode;
        if (hashCode != 0) {
            return hashCode;
        }
        hashCode = Objects.hashCode(appName) + 13 * (Objects.hashCode(moduleName) + 13 * (Objects.hashCode(beanName) + 13 * Objects.hashCode(distinctName)));
        return this.hashCode = hashCode == 0 ? 1 : hashCode;
    }

    /**
     * Get the EJB identifier as a human-readable string.
     *
     * @return the EJB identifier as a human-readable string (not {@code null})
     */
    public String toString() {
        final String distinctName = getDistinctName();
        if (distinctName == null || distinctName.isEmpty()) {
            return String.format("%s/%s/%s", getAppName(), getModuleName(), getBeanName());
        } else {
            return String.format("%s/%s/%s/%s", getAppName(), getModuleName(), getBeanName(), distinctName);
        }
    }
}
