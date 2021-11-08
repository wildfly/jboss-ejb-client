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

import javax.ejb.EJBObject;

import org.wildfly.common.Assert;

/**
 * A locator for an entity EJB.
 *
 * @param <T> the remote view type
 */
public final class EntityEJBLocator<T extends EJBObject> extends EJBLocator<T> {

    private static final long serialVersionUID = 6674116259124568398L;

    private final Object primaryKey;

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param primaryKey   the entity primary key
     */
    public EntityEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final Object primaryKey) {
        this(viewType, appName, moduleName, beanName, "", primaryKey, Affinity.NONE);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param primaryKey   the entity primary key
     * @param affinity     the affinity
     */
    public EntityEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final Object primaryKey, final Affinity affinity) {
        this(viewType, appName, moduleName, beanName, "", primaryKey, affinity);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param distinctName the distinct name
     * @param primaryKey   the entity primary key
     */
    public EntityEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Object primaryKey) {
        this(viewType, appName, moduleName, beanName, distinctName, primaryKey, Affinity.NONE);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param distinctName the distinct name
     * @param primaryKey   the entity primary key
     * @param affinity     the affinity
     */
    public EntityEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Object primaryKey, final Affinity affinity) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
        Assert.checkNotNullParam("primaryKey", primaryKey);
        this.primaryKey = primaryKey;
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param identifier the EJB identifier
     * @param primaryKey the entity primary key
     * @param affinity the affinity
     */
    public EntityEJBLocator(final Class<T> viewType, final EJBIdentifier identifier, final Object primaryKey, final Affinity affinity) {
        super(viewType, identifier, affinity);
        Assert.checkNotNullParam("primaryKey", primaryKey);
        this.primaryKey = primaryKey;
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param identifier the EJB identifier
     * @param primaryKey the entity primary key
     */
    public EntityEJBLocator(final Class<T> viewType, final EJBIdentifier identifier, final Object primaryKey) {
        this(viewType, identifier, primaryKey, Affinity.NONE);
    }

    /**
     * Construct a new instance.  This constructor creates a copy of the original locator, but with a new affinity.
     *
     * @param original the original locator
     * @param newAffinity the new affinity
     */
    public EntityEJBLocator(final EntityEJBLocator<T> original, final Affinity newAffinity) {
        super(original, newAffinity);
        this.primaryKey = original.primaryKey;
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type (must not be {@code null})
     * @param identifier the EJB identifier (must not be {@code null})
     * @param primaryKey the entity primary key (must not be {@code null})
     * @param affinity the affinity
     * @param <T> the remote view type
     * @return the new instance (not {@code null})
     */
    public static <T extends EJBObject> EntityEJBLocator<T> create(final Class<T> viewType, final EJBIdentifier identifier, final Object primaryKey, final Affinity affinity) {
        return new EntityEJBLocator<T>(viewType, identifier, primaryKey, affinity);
    }

    public EntityEJBLocator<T> withNewAffinity(final Affinity affinity) {
        Assert.checkNotNullParam("affinity", affinity);
        return getAffinity().equals(affinity) ? this : new EntityEJBLocator<T>(this, affinity);
    }

    @SuppressWarnings("unchecked")
    public <S> EntityEJBLocator<? extends S> narrowTo(final Class<S> type) {
        return (EntityEJBLocator<? extends S>) super.narrowTo(type);
    }

    @SuppressWarnings("unchecked")
    public <S extends EJBObject> EntityEJBLocator<? extends S> narrowAsEntity(final Class<S> type) {
        if (type.isAssignableFrom(getViewType())) {
            return (EntityEJBLocator<? extends S>) this;
        }
        throw new ClassCastException(type.toString());
    }

    public boolean isEntity() {
        return true;
    }

    /**
     * Get the primary key for the referenced entity.
     *
     * @return the primary key for the referenced entity
     */
    public Object getPrimaryKey() {
        return primaryKey;
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Object other) {
        return other instanceof EntityEJBLocator && equals((EntityEJBLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EJBLocator<?> other) {
        return other instanceof EntityEJBLocator && equals((EntityEJBLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EntityEJBLocator<?> other) {
        return super.equals(other) && primaryKey.equals(other.primaryKey);
    }

    int calculateHashCode() {
        return primaryKey.hashCode() * 13 + super.calculateHashCode();
    }

    @Override
    public String toString() {
        return String.format("%s, primary key is %s", super.toString(), getPrimaryKey());
    }
}
