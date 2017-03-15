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

import java.io.Serializable;

import javax.ejb.EJBHome;
import javax.ejb.EJBMetaData;
import javax.ejb.EJBObject;

import org.jboss.ejb._private.Logs;
import org.wildfly.common.Assert;

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
        Assert.checkNotNullParam("remoteInterfaceClass", remoteInterfaceClass);
        Assert.checkNotNullParam("homeLocator", homeLocator);
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

    /**
     * Get the home locator for this metadata instance.
     *
     * @return the home locator for this metadata instance (not {@code null})
     */
    public EJBHomeLocator<H> getHomeLocator() {
        return homeLocator;
    }
}
