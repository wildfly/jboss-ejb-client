/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.security.Permission;

/**
 * The class for various general EJB client permissions.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientPermission extends Permission {

    private static final long serialVersionUID = 8406360684253911321L;

    enum Name {
        createContext,
        createReceiver,
        changeWeakAffinity,
        changeStrongAffinity,
        ;

        public static Name of(final String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Invalid permission name " + name);
            }
        }

    }

    static final Name[] values = Name.values();

    private transient Name name;

    public EJBClientPermission(final String name) {
        this(Name.of(name));
    }

    public EJBClientPermission(final String name, @SuppressWarnings("unused") final String actions) {
        this(Name.of(name));
        if (actions != null && ! actions.isEmpty()) {
            throw new IllegalArgumentException("Unsupported actions string '" + actions + "'");
        }
    }

    EJBClientPermission(final Name name) {
        super(name.name());
        this.name = name;
    }

    public boolean implies(final Permission permission) {
        return permission instanceof EJBClientPermission && implies((EJBClientPermission) permission);
    }

    public boolean implies(final EJBClientPermission permission) {
        return permission != null && name == permission.name;
    }

    public boolean equals(final Object obj) {
        return obj instanceof EJBClientPermission && equals((EJBClientPermission) obj);
    }

    public boolean equals(final Permission obj) {
        return obj instanceof EJBClientPermission && equals((EJBClientPermission) obj);
    }

    public boolean equals(final EJBClientPermission obj) {
        return obj != null && name == obj.name;
    }

    public int hashCode() {
        return name.ordinal();
    }

    public String getActions() {
        return "";
    }
}
