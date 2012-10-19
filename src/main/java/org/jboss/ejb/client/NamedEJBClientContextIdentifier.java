/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.io.Serializable;

/**
 * A {@link EJBClientContextIdentifier} which identifies a {@link EJBClientContext} by a name
 *
 * @author Jaikiran Pai
 */
public class NamedEJBClientContextIdentifier implements EJBClientContextIdentifier, Serializable {

    private final String name;

    private final String cachedToString;

    /**
     * Creates a {@link NamedEJBClientContextIdentifier} for the passed <code>name</code>.
     *
     * @param name Cannot be null.
     * @throws IllegalArgumentException If the passed <code>name</code> is null
     */
    public NamedEJBClientContextIdentifier(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        this.name = name;

        this.cachedToString = "[Named EJB client context identifier: " + this.name + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NamedEJBClientContextIdentifier that = (NamedEJBClientContextIdentifier) o;

        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return this.cachedToString;
    }
}
