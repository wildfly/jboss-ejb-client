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

import org.wildfly.common.Assert;

/**
 * A locator for a stateful session EJB.
 *
 * @param <T> the remote view type
 */
public final class StatefulEJBLocator<T> extends EJBLocator<T> {

    private static final long serialVersionUID = 8229686118358785586L;

    private final SessionID sessionId;

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
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param identifier the EJB identifier
     * @param sessionId the stateful session ID
     * @param affinity the {@link Affinity} for this stateful bean locator
     */
    public StatefulEJBLocator(final Class<T> viewType, final EJBIdentifier identifier, final SessionID sessionId, final Affinity affinity) {
        super(viewType, identifier, affinity);
        Assert.checkNotNullParam("sessionId", sessionId);
        this.sessionId = sessionId;
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param identifier the EJB identifier
     * @param sessionId the stateful session ID
     */
    public StatefulEJBLocator(final Class<T> viewType, final EJBIdentifier identifier, final SessionID sessionId) {
        this(viewType, identifier, sessionId, Affinity.NONE);
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
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type (must not be {@code null})
     * @param identifier the EJB identifier (must not be {@code null})
     * @param sessionId the stateful session ID (must not be {@code null})
     * @param affinity the {@link Affinity} for this stateful bean locator
     * @param <T> the remote view type
     * @return the new instance (not {@code null})
     */
    public static <T> StatefulEJBLocator<T> create(final Class<T> viewType, final EJBIdentifier identifier, final SessionID sessionId, final Affinity affinity) {
        return new StatefulEJBLocator<T>(viewType, identifier, sessionId, affinity);
    }

    /**
     * Get a copy of this stateful EJB locator, but without any session ID.
     *
     * @return the stateless EJB locator (not {@code null})
     */
    public StatelessEJBLocator<T> withoutSession() {
        return new StatelessEJBLocator<T>(this, getAffinity());
    }

    public StatefulEJBLocator<T> withSession(final SessionID sessionId) {
        Assert.checkNotNullParam("sessionId", sessionId);
        if (sessionId.equals(this.sessionId)) {
            return this;
        } else {
            return new StatefulEJBLocator<T>(this, sessionId);
        }
    }

    public StatefulEJBLocator<T> withNewAffinity(final Affinity affinity) {
        Assert.checkNotNullParam("affinity", affinity);
        return getAffinity().equals(affinity) ? this : new StatefulEJBLocator<T>(this, affinity);
    }

    public StatefulEJBLocator<T> withSessionAndAffinity(final SessionID sessionId, final Affinity affinity) {
        Assert.checkNotNullParam("sessionId", sessionId);
        Assert.checkNotNullParam("affinity", affinity);
        return getAffinity().equals(affinity) && getSessionId().equals(sessionId) ? this : new StatefulEJBLocator<T>(this, sessionId, affinity);
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

    int calculateHashCode() {
        return sessionId.hashCode() * 13 + super.calculateHashCode();
    }

    @Override
    public String toString() {
        return String.format("%s, session ID is %s", super.toString(), getSessionId());
    }
}
