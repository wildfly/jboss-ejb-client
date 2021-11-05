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

import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

/**
 * EJB metadata for stateless EJBs.
 *
 * @param <T> the remote interface type
 * @param <H> the home interface type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StatelessEJBMetaData<T extends EJBObject, H extends EJBHome> extends AbstractEJBMetaData<T, H> {

    private static final long serialVersionUID = 8596216068022888027L;

    /**
     * Construct a new instance.
     *
     * @param remoteInterfaceClass the remote interface class
     * @param homeLocator the EJB home locator
     */
    public StatelessEJBMetaData(final Class<T> remoteInterfaceClass, final EJBHomeLocator<H> homeLocator) {
        super(remoteInterfaceClass, homeLocator);
    }

    /**
     * Construct a new instance.
     *
     * @param remoteInterfaceClass the remote interface class (must not be {@code null})
     * @param homeLocator the EJB home locator (must not be {@code null})
     * @param <T> the remote interface type
     * @param <H> the home interface type
     * @return the new instance (not {@code null})
     */
    public static <T extends EJBObject, H extends EJBHome> StatelessEJBMetaData<T, H> create(final Class<T> remoteInterfaceClass, final EJBHomeLocator<H> homeLocator) {
        return new StatelessEJBMetaData<>(remoteInterfaceClass, homeLocator);
    }

    public boolean isStatelessSession() {
        return true;
    }
}
