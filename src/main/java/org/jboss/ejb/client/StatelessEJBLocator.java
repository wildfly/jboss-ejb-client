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
 * A locator for a stateless session EJB.
 *
 * @param <T> the remote view type
 */
public final class StatelessEJBLocator<T> extends EJBLocator<T> {

    private static final long serialVersionUID = -3040039191221970094L;

    /**
     * Construct a new instance.
     *
     * @param viewType     the view type
     * @param appName      the application name
     * @param moduleName   the module name
     * @param beanName     the bean name
     */
    public StatelessEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName) {
        super(viewType, appName, moduleName, beanName, "", Affinity.NONE);
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
    public StatelessEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final Affinity affinity) {
        super(viewType, appName, moduleName, beanName, "", affinity);
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
    public StatelessEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName) {
        super(viewType, appName, moduleName, beanName, distinctName, Affinity.NONE);
    }

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
    public StatelessEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param identifier the EJB identifier
     * @param affinity the affinity
     */
    public StatelessEJBLocator(final Class<T> viewType, final EJBIdentifier identifier, final Affinity affinity) {
        super(viewType, identifier, affinity);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type
     * @param identifier the EJB identifier
     */
    public StatelessEJBLocator(final Class<T> viewType, final EJBIdentifier identifier) {
        this(viewType, identifier, Affinity.NONE);
    }

    /**
     * Construct a new instance.  This constructor creates a copy of the original locator, but with a new affinity.
     *
     * @param original the original locator
     * @param newAffinity the new affinity
     */
    public StatelessEJBLocator(final StatelessEJBLocator<T> original, final Affinity newAffinity) {
        super(original, newAffinity);
    }

    StatelessEJBLocator(final EJBLocator<T> original, final Affinity newAffinity) {
        super(original, newAffinity);
    }

    /**
     * Construct a new instance.
     *
     * @param viewType the view type (must not be {@code null})
     * @param identifier the EJB identifier (must not be {@code null})
     * @param affinity the {@link Affinity} for this stateful bean locator
     * @param <T> the remote view type
     * @return the new instance (not {@code null})
     */
    public static <T> StatelessEJBLocator<T> create(final Class<T> viewType, final EJBIdentifier identifier, final Affinity affinity) {
        return new StatelessEJBLocator<T>(viewType, identifier, affinity);
    }

    /**
     * Construct a new instance.  This method uses the location from the original locator, but with the given
     * session ID and affinity.
     *
     * @param original the original locator (must not be {@code null})
     * @param newAffinity the new affinity
     * @param <T> the remote view type
     * @return the new instance (not {@code null})
     */
    public static <T> StatelessEJBLocator<T> create(final EJBLocator<T> original, final Affinity newAffinity) {
        return new StatelessEJBLocator<T>(original, newAffinity);
    }

    public StatelessEJBLocator<T> withNewAffinity(final Affinity affinity) {
        Assert.checkNotNullParam("affinity", affinity);
        return getAffinity().equals(affinity) ? this : new StatelessEJBLocator<T>(this, affinity);
    }

    public StatefulEJBLocator<T> withSession(final SessionID sessionId) {
        Assert.checkNotNullParam("sessionId", sessionId);
        return new StatefulEJBLocator<T>(this, sessionId);
    }

    public StatefulEJBLocator<T> withSessionAndAffinity(final SessionID sessionId, final Affinity affinity) {
        Assert.checkNotNullParam("sessionId", sessionId);
        Assert.checkNotNullParam("affinity", affinity);
        return new StatefulEJBLocator<T>(this, sessionId, affinity);
    }

    @SuppressWarnings("unchecked")
    public <S> StatelessEJBLocator<? extends S> narrowTo(final Class<S> type) {
        return (StatelessEJBLocator<? extends S>) super.narrowTo(type);
    }

    @SuppressWarnings("unchecked")
    public <S> StatelessEJBLocator<? extends S> narrowAsStateless(final Class<S> type) {
        if (type.isAssignableFrom(getViewType())) {
            return (StatelessEJBLocator<? extends S>) this;
        }
        throw new ClassCastException(type.toString());
    }

    public StatelessEJBLocator<T> asStateless() {
        return this;
    }

    public boolean isStateless() {
        return true;
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Object other) {
        return other instanceof StatelessEJBLocator && equals((StatelessEJBLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EJBLocator<?> other) {
        return other instanceof StatelessEJBLocator && equals((StatelessEJBLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final StatelessEJBLocator<?> other) {
        return super.equals(other);
    }
}
