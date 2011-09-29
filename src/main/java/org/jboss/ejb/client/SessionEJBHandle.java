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
import org.jboss.marshalling.FieldSetter;

import javax.ejb.EJBObject;

/**
 * A handle for a session EJB.  A session EJB's uniqueness depends not only on its identity but also its session ID.
 * Stateless session EJBs have a session ID which is {@code ==} to {@link NoSessionID#INSTANCE}.
 *
 * @param <T> the EJB type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SessionEJBHandle<T extends EJBObject> extends EJBHandle<T> {

    private static final long serialVersionUID = 5811526405102776623L;

    private final SessionID sessionId;
    private final transient int hashCode;

    private static final FieldSetter hashCodeSetter = FieldSetter.get(SessionEJBHandle.class, "hashCode");

    SessionEJBHandle(final Class<T> type, final String appName, final String moduleName, final String distinctName, final String beanName, final SessionID sessionId) {
        super(type, appName, moduleName, distinctName, beanName);
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is null");
        }
        this.sessionId = sessionId;
        hashCode = super.hashCode() * 13 + sessionId.hashCode();
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Object other) {
        return other instanceof SessionEJBHandle && equals((SessionEJBHandle<?> )other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EJBGenericHandle<?> other) {
        return other instanceof SessionEJBHandle && equals((SessionEJBHandle<?> )other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EJBHandle<?> other) {
        return other instanceof SessionEJBHandle && equals((SessionEJBHandle<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final SessionEJBHandle<?> other) {
        return this == other || other != null && super.equals(other) && sessionId.equals(other.sessionId);
    }

    /** {@inheritDoc} */
    protected EJBInvocationHandler getInvocationHandler() {
        final EJBInvocationHandler invocationHandler = super.getInvocationHandler();
        invocationHandler.putAttachment(SessionID.SESSION_ID_KEY, sessionId);
        return invocationHandler;
    }

    public int hashCode() {
        return hashCode;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        hashCodeSetter.setInt(this, super.hashCode() * 13 + sessionId.hashCode());
    }
}
