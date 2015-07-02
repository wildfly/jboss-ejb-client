/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
        if (session || statelessSession) {
            if (statelessSession) {
                return createStatelessMetaData(remoteClass.asSubclass(EJBObject.class), homeClass, home);
            }
            return createStatefulMetaData(remoteClass.asSubclass(EJBObject.class), homeClass, home);
        } else {
            return createEntityMetaData(remoteClass.asSubclass(EJBObject.class), homeClass, home, pkClass);
        }
    }

    private static <T extends EJBObject, H extends EJBHome> StatelessEJBMetaData<T, ? extends H> createStatelessMetaData(Class<T> remoteClass, Class<H> homeClass, EJBHome home) {
        return new StatelessEJBMetaData<>(remoteClass, EJBClient.getLocatorFor(home).narrowAsHome(homeClass));
    }

    private static <T extends EJBObject, H extends EJBHome> StatefulEJBMetaData<T, ? extends H> createStatefulMetaData(Class<T> remoteClass, Class<H> homeClass, EJBHome home) {
        return new StatefulEJBMetaData<>(remoteClass, EJBClient.getLocatorFor(home).narrowAsHome(homeClass));
    }

    private static <T extends EJBObject, H extends EJBHome> EntityEJBMetaData<T, ? extends H> createEntityMetaData(Class<T> remoteClass, Class<H> homeClass, EJBHome home, Class<?> pkClass) {
        return new EntityEJBMetaData<>(remoteClass, EJBClient.getLocatorFor(home).narrowAsHome(homeClass), pkClass);
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