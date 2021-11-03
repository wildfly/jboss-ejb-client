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
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param identifier the EJB identifier
     * @param affinity the affinity
     */
    public EJBHomeLocator(final Class<T> viewType, final EJBIdentifier identifier, final Affinity affinity) {
        super(viewType, identifier, affinity);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param identifier the EJB identifier
     */
    public EJBHomeLocator(final Class<T> viewType, final EJBIdentifier identifier) {
        super(viewType, identifier, Affinity.NONE);
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

    /**
     * Construct a new instance.
     *
     * @param viewType the view type (must not be {@code null})
     * @param identifier the EJB identifier (must not be {@code null})
     * @param affinity the affinity
     * @param <T> the remote view type
     * @return the new instance (not {@code null})
     */
    public static <T extends EJBHome> EJBHomeLocator<T> create(final Class<T> viewType, final EJBIdentifier identifier, final Affinity affinity) {
        return new EJBHomeLocator<T>(viewType, identifier, affinity);
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
