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

import java.rmi.RemoteException;

import javax.ejb.EJBHome;
import javax.ejb.HomeHandle;

import org.wildfly.common.Assert;

/**
 * A handle for an EJB home interface.
 *
 * @param <T> the EJB remote home interface type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBHomeHandle<T extends EJBHome> implements HomeHandle {

    private static final long serialVersionUID = -4870688692508067759L;

    private final EJBHomeLocator<T> locator;

    /**
     * Construct a new instance.
     *
     * @param locator the locator for the home interface
     */
    public EJBHomeHandle(final EJBHomeLocator<T> locator) {
        Assert.checkNotNullParam("locator", locator);
        this.locator = locator;
    }

    /**
     * Construct a new instance.
     *
     * @param locator the locator for the home interface (must not be {@code null})
     * @param <T> the EJB home type
     * @return the handle (not {@code null})
     */
    public static <T extends EJBHome> EJBHomeHandle<T> create(EJBHomeLocator<T> locator) {
        return new EJBHomeHandle<>(locator);
    }

    /**
     * {@inheritDoc}
     */
    public T getEJBHome() throws RemoteException {
        return EJBClient.createProxy(locator);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof EJBHomeHandle && equals((EJBHomeHandle<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(EJBHomeHandle<?> other) {
        return this == other || other != null && locator.equals(other.locator);
    }

    /**
     * Get the hash code for this EJB home handle.
     *
     * @return the hash code
     */
    public int hashCode() {
        return locator.hashCode() ^ 1;
    }

    /**
     * Get the locator for this handle.
     *
     * @return the locator for this handle
     */
    public EJBHomeLocator<T> getLocator() {
        return locator;
    }
}
