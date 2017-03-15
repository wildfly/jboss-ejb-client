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
