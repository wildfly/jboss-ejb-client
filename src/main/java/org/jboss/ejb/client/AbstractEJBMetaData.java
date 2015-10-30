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

import java.io.Serializable;

import javax.ejb.EJBHome;
import javax.ejb.EJBMetaData;
import javax.ejb.EJBObject;

import org.jboss.ejb._private.Logs;

/**
 * Abstract base class for all EJB metadata.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractEJBMetaData<T extends EJBObject, H extends EJBHome> implements EJBMetaData, Serializable {

    private static final long serialVersionUID = -5391231161942555933L;

    private final Class<T> remoteInterfaceClass;
    private final EJBHomeLocator<H> homeLocator;

    AbstractEJBMetaData(final Class<T> remoteInterfaceClass, final EJBHomeLocator<H> homeLocator) {
        this.remoteInterfaceClass = remoteInterfaceClass;
        this.homeLocator = homeLocator;
    }

    /**
     * Get the EJB home interface.
     *
     * @return the EJB home interface
     */
    public H getEJBHome() {
        return EJBClient.createProxy(homeLocator);
    }

    /**
     * Get the remote interface class.
     *
     * @return the remote interface class
     */
    public Class<T> getRemoteInterfaceClass() {
        return remoteInterfaceClass;
    }

    /**
     * Get the home interface class.
     *
     * @return the home interface
     */
    public Class<H> getHomeInterfaceClass() {
        return homeLocator.getViewType();
    }

    /**
     * Get the primary key class.
     *
     * @return the primary key class
     */
    public Class<?> getPrimaryKeyClass() {
        throw Logs.MAIN.primaryKeyNotRelevantForSessionBeans();
    }

    /**
     * Determine whether this EJB metadata refers to a session EJB.
     *
     * @return {@code true} if the EJB is a session EJB, {@code false} otherwise
     */
    public boolean isSession() {
        return true;
    }

    /**
     * Determine whether this EJB metadata refers to a stateless session EJB.
     *
     * @return {@code true} if the EJB is a stateless session EJB, {@code false} otherwise
     */
    public boolean isStatelessSession() {
        return false;
    }
}
