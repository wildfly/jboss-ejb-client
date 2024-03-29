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

import jakarta.ejb.EJBHome;
import jakarta.ejb.EJBObject;

import org.wildfly.common.Assert;

/**
 * Enterprise Beans metadata for entity Enterprise Beans.
 *
 * @param <T> the remote interface type
 * @param <H> the home interface type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EntityEJBMetaData<T extends EJBObject, H extends EJBHome> extends AbstractEJBMetaData<T, H> {

    private static final long serialVersionUID = 5985601714593841885L;

    private final Class<?> primaryKeyClass;

    /**
     * Construct a new instance.
     *
     * @param remoteInterfaceClass the remote interface class
     * @param homeLocator the EJB home locator
     * @param primaryKeyClass the primary key class
     */
    public EntityEJBMetaData(final Class<T> remoteInterfaceClass, final EJBHomeLocator<H> homeLocator, final Class<?> primaryKeyClass) {
        super(remoteInterfaceClass, homeLocator);
        Assert.checkNotNullParam("primaryKeyClass", primaryKeyClass);
        this.primaryKeyClass = primaryKeyClass;
    }

    /**
     * Construct a new instance.
     *
     * @param remoteInterfaceClass the remote interface class (must not be {@code null})
     * @param homeLocator the Enterprise Beans home locator (must not be {@code null})
     * @param primaryKeyClass the primary key class (must not be {@code null})
     * @param <T> the remote interface type
     * @param <H> the home interface type
     * @return the new instance (not {@code null})
     */
    public static <T extends EJBObject, H extends EJBHome> EntityEJBMetaData<T, H> create(final Class<T> remoteInterfaceClass, final EJBHomeLocator<H> homeLocator, final Class<?> primaryKeyClass) {
        return new EntityEJBMetaData<T, H>(remoteInterfaceClass, homeLocator, primaryKeyClass);
    }

    public boolean isSession() {
        return false;
    }

    public Class<?> getPrimaryKeyClass() {
        return primaryKeyClass;
    }
}
