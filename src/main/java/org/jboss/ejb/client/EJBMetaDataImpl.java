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

/**
 * An implementation of the EJBMetaData interface which allows a
 * client to obtain the enterprise Bean's meta-data information.
 *
 * @author Stuart Douglas
 *
 * @deprecated Retained for compatibility with older protocol versions; use the {@link AbstractEJBMetaData} hierarchy
 * instead.
 */
@Deprecated
public final class EJBMetaDataImpl implements EJBMetaData, Serializable {

    private static final long serialVersionUID = 100581743643837404L;

    private final Class<?> remoteClass;

    private final Class<? extends EJBHome> homeClass;

    private final Class<?> pkClass;

    private final boolean session;

    private final boolean statelessSession;

    private final EJBHome home;

    public EJBMetaDataImpl(final Class<?> remoteClass, final Class<? extends EJBHome> homeClass, final Class<?> pkClass, final boolean session,
                           final boolean statelessSession, final EJBHome home) {
        this.remoteClass = remoteClass;
        this.homeClass = homeClass;
        this.pkClass = pkClass;
        this.session = session;
        this.statelessSession = statelessSession;
        this.home = home;
    }

    public EJBMetaDataImpl(AbstractEJBMetaData<?, ?> ejbMetaData) {
        this.remoteClass = ejbMetaData.getRemoteInterfaceClass();
        this.homeClass = ejbMetaData.getHomeInterfaceClass();
        this.session = ejbMetaData.isSession();
        this.statelessSession = ejbMetaData.isStatelessSession();
        this.pkClass = session || statelessSession ? null : ejbMetaData.getPrimaryKeyClass();
        this.home = ejbMetaData.getEJBHome();
    }

    public AbstractEJBMetaData<?, ?> toAbstractEJBMetaData() {
        final EJBHomeLocator<? extends EJBHome> homeLocator = EJBClient.getLocatorFor(home).narrowAsHome(homeClass);
        final Class<? extends EJBObject> ejbObjectClass = remoteClass.asSubclass(EJBObject.class);
        if (session || statelessSession) {
            if (statelessSession) {
                return StatelessEJBMetaData.create(ejbObjectClass, homeLocator);
            }
            return StatefulEJBMetaData.create(ejbObjectClass, homeLocator);
        } else {
            return EntityEJBMetaData.create(ejbObjectClass, homeLocator, pkClass);
        }
    }

    /**
     * Obtains the home interface of the enterprise Bean.
     */
    public EJBHome getEJBHome() {
        return home;
    }

    /**
     * Obtains the <code>Class</code> object for the enterprise Bean's home
     * interface.
     */
    public Class<? extends EJBHome> getHomeInterfaceClass() {
        return homeClass;
    }

    /**
     * Obtains the <code>Class</code> object for the enterprise Bean's remote
     * interface.
     */
    public Class<?> getRemoteInterfaceClass() {
        return remoteClass;
    }

    /**
     * Obtains the <code>Class</code> object for the enterprise Bean's primary
     * key class.
     */
    public Class<?> getPrimaryKeyClass() {
        if (session)
            throw Logs.MAIN.primaryKeyNotRelevantForSessionBeans();
        return pkClass;
    }

    /**
     * Tests if the enterprise Bean's type is "session".
     *
     * @return true if the type of the enterprise Bean is session bean.
     */
    public boolean isSession() {
        return session;
    }

    /**
     * Tests if the enterprise Bean's type is "stateless session".
     *
     * @return true if the type of the enterprise Bean is stateless session.
     */
    public boolean isStatelessSession() {
        return statelessSession;
    }
}