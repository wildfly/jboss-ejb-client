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

import org.jboss.marshalling.FieldSetter;
import org.wildfly.common.Assert;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * A locator for a stateful session EJB.
 *
 * @param <T> the remote view type
 */
public final class StatefulEJBLocator<T> extends EJBLocator<T> {

    private static final long serialVersionUID = 8229686118358785586L;
    private static final FieldSetter hashCodeSetter = FieldSetter.get(StatefulEJBLocator.class, "hashCode");

    private final SessionID sessionId;
    private final transient int hashCode;

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param sessionId    the stateful session ID
     */
    public StatefulEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final SessionID sessionId) {
        this(viewType, appName, moduleName, beanName, sessionId, Affinity.NONE);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param sessionId    the stateful session ID
     * @param affinity     The {@link Affinity} for this stateful bean locator
     */
    public StatefulEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final SessionID sessionId, final Affinity affinity) {
        this(viewType, appName, moduleName, beanName, "", sessionId, affinity);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param distinctName the distinct name
     * @param sessionId    the stateful session ID
     */
    public StatefulEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final SessionID sessionId) {
        this(viewType, appName, moduleName, beanName, distinctName, sessionId, Affinity.NONE);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     * @param distinctName the distinct name
     * @param sessionId    the stateful session ID
     * @param affinity     The {@link Affinity} for this stateful bean locator
     */
    public StatefulEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final SessionID sessionId, final Affinity affinity) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
        Assert.checkNotNullParam("sessionId", sessionId);
        this.sessionId = sessionId;
        hashCode = sessionId.hashCode() + 13 * super.hashCode();
    }

    /**
     * Construct a new instance.  This constructor creates a copy of the original locator, but with a new affinity.
     *
     * @param original the original locator
     * @param newAffinity the new affinity
     */
    public StatefulEJBLocator(final StatefulEJBLocator<T> original, final Affinity newAffinity) {
        super(original, newAffinity);
        this.sessionId = original.sessionId;
        hashCode = sessionId.hashCode() + 13 * super.hashCode();
    }

    /**
     * Construct a new instance.  This constructor uses the location from the original locator, but with the given
     * session ID.
     *
     * @param original the original locator
     * @param sessionId the session ID
     */
    public StatefulEJBLocator(final EJBLocator<T> original, final SessionID sessionId) {
        super(original, original.getAffinity());
        Assert.checkNotNullParam("sessionId", sessionId);
        this.sessionId = sessionId;
        hashCode = sessionId.hashCode() + 13 * super.hashCode();
    }

    /**
     * Construct a new instance.  This constructor uses the location from the original locator, but with the given
     * session ID and affinity.
     *
     * @param original the original locator
     * @param sessionId the session ID
     * @param newAffinity the new affinity
     */
    public StatefulEJBLocator(final EJBLocator<T> original, final SessionID sessionId, final Affinity newAffinity) {
        super(original, newAffinity);
        Assert.checkNotNullParam("sessionId", sessionId);
        this.sessionId = sessionId;
        hashCode = sessionId.hashCode() + 13 * super.hashCode();
    }

    public StatefulEJBLocator<T> withNewAffinity(final Affinity affinity) {
        Assert.checkNotNullParam("affinity", affinity);
        return getAffinity().equals(affinity) ? this : new StatefulEJBLocator<T>(this, affinity);
    }

    @SuppressWarnings("unchecked")
    public <S> StatefulEJBLocator<? extends S> narrowTo(final Class<S> type) {
        return (StatefulEJBLocator<? extends S>) super.narrowTo(type);
    }

    @SuppressWarnings("unchecked")
    public <S> StatefulEJBLocator<? extends S> narrowAsStateful(final Class<S> type) {
        if (type.isAssignableFrom(getViewType())) {
            return (StatefulEJBLocator<? extends S>) this;
        }
        throw new ClassCastException(type.toString());
    }

    public StatefulEJBLocator<T> asStateful() {
        return this;
    }

    public boolean isStateful() {
        return true;
    }

    /**
     * Get the session ID associated with this locator.
     *
     * @return the session ID
     */
    public SessionID getSessionId() {
        return sessionId;
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
    public boolean equals(final Object other) {
        return other instanceof StatefulEJBLocator && equals((StatefulEJBLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EJBLocator<?> other) {
        return other instanceof StatefulEJBLocator && equals((StatefulEJBLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final StatefulEJBLocator<?> other) {
        return super.equals(other) && sessionId.equals(other.sessionId);
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        hashCodeSetter.setInt(this, sessionId.hashCode() * 13 + super.hashCode());
    }

    @Override
    public String toString() {
        return String.format("%s, session ID is %s", super.toString(), getSessionId());
    }
}
