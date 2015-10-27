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

import javax.ejb.EJBObject;

import org.jboss.marshalling.FieldSetter;
import org.wildfly.common.Assert;

/**
 * A locator for an entity EJB.
 *
 * @param <T> the remote view type
 */
public final class EntityEJBLocator<T extends EJBObject> extends EJBLocator<T> {

    private static final long serialVersionUID = 6674116259124568398L;

    private final Object primaryKey;
    private final transient int hashCode;

    private static final FieldSetter hashCodeSetter = FieldSetter.get(EntityEJBLocator.class, "hashCode");

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
        hashCode = primaryKey.hashCode() * 13 + super.hashCode();
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
        hashCode = primaryKey.hashCode() * 13 + super.hashCode();
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

    /**
     * Get the hash code for this instance.
     *
     * @return the hash code for this instance
     */
    public int hashCode() {
        return hashCode;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        hashCodeSetter.setInt(this, primaryKey.hashCode() * 13 + super.hashCode());
    }

    @Override
    public String toString() {
        return String.format("%s, primary key is %s", super.toString(), getPrimaryKey());
    }
}
