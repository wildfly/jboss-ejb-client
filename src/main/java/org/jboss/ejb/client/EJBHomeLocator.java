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

import javax.ejb.EJBHome;

import org.wildfly.common.Assert;

/**
 * A locator for an EJB's home interface.
 *
 * @param <T> the remote view type
 */
public final class EJBHomeLocator<T extends EJBHome> extends EJBLocator<T> {

    private static final long serialVersionUID = -3040039191221970094L;

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param distinctName the distinct name
     * @param affinity     the affinity
     */
    public EJBHomeLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param distinctName the distinct name
     */
    public EJBHomeLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) {
        this(viewType, appName, moduleName, beanName, distinctName, Affinity.NONE);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param affinity     the affinity
     */
    public EJBHomeLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final Affinity affinity) {
        this(viewType, appName, moduleName, beanName, "", affinity);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     */
    public EJBHomeLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName) {
        this(viewType, appName, moduleName, beanName, "", Affinity.NONE);
    }

    /**
     * Construct a new instance from an original instance but with a new affinity.
     *
     * @param original the original locator
     * @param newAffinity the new affinity to use
     */
    public EJBHomeLocator(final EJBHomeLocator<T> original, final Affinity newAffinity) {
        super(original, newAffinity);
    }

    public EJBHomeLocator<T> withNewAffinity(final Affinity affinity) {
        Assert.checkNotNullParam("affinity", affinity);
        return getAffinity().equals(affinity) ? this : new EJBHomeLocator<T>(this, affinity);
    }

    @SuppressWarnings("unchecked")
    public <S> EJBHomeLocator<? extends S> narrowTo(final Class<S> type) {
        return (EJBHomeLocator<? extends S>) super.narrowTo(type);
    }

    @SuppressWarnings("unchecked")
    public <S extends EJBHome> EJBHomeLocator<? extends S> narrowAsHome(final Class<S> type) {
        if (type.isAssignableFrom(getViewType())) {
            return (EJBHomeLocator<? extends S>) this;
        }
        throw new ClassCastException(type.toString());
    }

    public boolean isHome() {
        return true;
    }

    /**
     * Get the hash code for this instance.
     *
     * @return the hash code for this instance
     */
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Object other) {
        return other instanceof EJBHomeLocator && equals((EJBHomeLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EJBLocator<?> other) {
        return other instanceof EJBHomeLocator && equals((EJBHomeLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EJBHomeLocator<?> other) {
        return super.equals(other);
    }
}
