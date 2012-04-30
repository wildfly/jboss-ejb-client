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

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * A locator for a stateful session EJB.
 *
 * @param <T> the remote view type
 */
public final class StatefulEJBLocator<T> extends EJBLocator<T> {

    private static final long serialVersionUID = 8229686118358785586L;

    private final SessionID sessionId;
    private final transient int hashCode;
    private final String sessionOwnerNode;
    private static final FieldSetter hashCodeSetter = FieldSetter.get(StatefulEJBLocator.class, "hashCode");

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
     * @deprecated Since 1.0.2. Use {@link #StatefulEJBLocator(Class, String, String, String, String, SessionID, Affinity, String)} instead
     */
    @Deprecated
    public StatefulEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final SessionID sessionId, final Affinity affinity) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
        if (sessionId == null) {
            throw Logs.MAIN.paramCannotBeNull("Session id");
        }
        this.sessionId = sessionId;
        this.sessionOwnerNode = null;
        hashCode = sessionId.hashCode() * 13 + super.hashCode();
    }

    /**
     * Constructs a {@link StatefulEJBLocator}
     *
     * @param viewType         the view type
     * @param appName          the application name
     * @param moduleName       the module name
     * @param beanName         the bean name
     * @param distinctName     the distinct name
     * @param sessionId        the stateful session ID
     * @param affinity         The {@link Affinity} for this stateful bean locator
     * @param sessionOwnerNode The name of the node on which the sessionId was generated
     */
    public StatefulEJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final SessionID sessionId, final Affinity affinity,
                              final String sessionOwnerNode) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
        if (sessionId == null) {
            throw Logs.MAIN.paramCannotBeNull("Session id");
        }
        if (sessionOwnerNode == null || sessionOwnerNode.trim().isEmpty()) {
            throw Logs.MAIN.paramCannotBeNullOrEmptyString("Session owning node");
        }
        this.sessionId = sessionId;
        this.sessionOwnerNode = sessionOwnerNode;
        hashCode = sessionId.hashCode() * 13 + super.hashCode();
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

    /**
     * Returns the name of the node on which the session was created for the stateful EJB represented by this
     * {@link StatefulEJBLocator}
     *
     * @return
     */
    String getSessionOwnerNode() {
        return this.sessionOwnerNode;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        hashCodeSetter.setInt(this, sessionId.hashCode() * 13 + super.hashCode());
    }


    @Override
    public String toString() {
        return "StatefulEJBLocator{" +
                "appName='" + getAppName() + '\'' +
                ", moduleName='" + getModuleName() + '\'' +
                ", distinctName='" + getDistinctName() + '\'' +
                ", beanName='" + getBeanName() + '\'' +
                ", view='" + getViewType() + '\'' +
                ", sessionId='" + getSessionId() + '\'' +
                '}';
    }
}
