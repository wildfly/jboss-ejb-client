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

    static <T extends EJBHome> EJBHomeHandle<T> handleFor(EJBHomeLocator<T> locator) {
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
