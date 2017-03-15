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

import javax.ejb.EJBObject;
import javax.ejb.Handle;

import org.wildfly.common.Assert;

/**
 * A handle for an EJB interface.
 *
 * @param <T> the EJB remote interface type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBHandle<T extends EJBObject> implements Handle {

    private static final long serialVersionUID = -4870688692508067759L;

    private final EJBLocator<T> locator;

    /**
     * Construct a new instance.
     *
     * @param locator the locator for the EJB instance
     */
    public EJBHandle(final EJBLocator<T> locator) {
        Assert.checkNotNullParam("locator", locator);
        this.locator = locator;
    }

    /**
     * Construct a new instance.
     *
     * @param locator the locator for the EJB instance (must not be {@code null})
     * @param <T> the EJB object type
     * @return the handle (not {@code null})
     */
    public static <T extends EJBObject> EJBHandle<T> create(EJBLocator<T> locator) {
        return new EJBHandle<>(locator);
    }

    /**
     * {@inheritDoc}
     */
    public T getEJBObject() throws RemoteException {
        return EJBClient.createProxy(locator);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof EJBHandle && equals((EJBHandle<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(EJBHandle<?> other) {
        return this == other || other != null && locator.equals(other.locator);
    }

    /**
     * Get the hash code for this EJB handle.
     *
     * @return the hash code
     */
    public int hashCode() {
        return locator.hashCode();
    }

    /**
     * Get the locator for this handle.
     *
     * @return the locator for this handle
     */
    public EJBLocator<T> getLocator() {
        return locator;
    }
}
