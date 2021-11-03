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

package org.jboss.ejb.protocol.remote;

import java.io.Serializable;

import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBHomeLocator;
import org.jboss.ejb.client.EntityEJBLocator;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class V1EJBLocator<T> implements Serializable {
    private static final long serialVersionUID = -7306257085240447972L;

    final Class<T> viewType;
    final String appName;
    final String moduleName;
    final String beanName;
    final String distinctName;
    final Affinity affinity;

    V1EJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        this.viewType = viewType;
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.distinctName = distinctName;
        this.affinity = affinity;
    }

    abstract Object readResolve();
}

final class V1StatefulLocator<T> extends V1EJBLocator<T> {
    private static final long serialVersionUID = 8229686118358785586L;

    final SessionID sessionId;
    final String sessionOwnerNode;

    V1StatefulLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity, final SessionID sessionId, final String sessionOwnerNode) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
        this.sessionId = sessionId;
        this.sessionOwnerNode = sessionOwnerNode;
    }

    Object readResolve() {
        return new StatefulEJBLocator<T>(viewType, appName, moduleName, beanName, distinctName, sessionId, affinity);
    }
}

final class V1StatelessLocator<T> extends V1EJBLocator<T> {
    private static final long serialVersionUID = -3040039191221970094L;

    V1StatelessLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
    }

    Object readResolve() {
        return new StatelessEJBLocator<T>(viewType, appName, moduleName, beanName, distinctName, affinity);
    }
}

final class V1EntityLocator<T extends EJBObject> extends V1EJBLocator<T> {
    private static final long serialVersionUID = 6674116259124568398L;

    final Object primaryKey;

    V1EntityLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity, final Object primaryKey) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
        this.primaryKey = primaryKey;
    }

    Object readResolve() {
        return new EntityEJBLocator<T>(viewType, appName, moduleName, beanName, distinctName, primaryKey, affinity);
    }
}

final class V1HomeLocator<T extends EJBHome> extends V1EJBLocator<T> {
    private static final long serialVersionUID = -3040039191221970094L;

    V1HomeLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        super(viewType, appName, moduleName, beanName, distinctName, affinity);
    }

    Object readResolve() {
        return new EJBHomeLocator<T>(viewType, appName, moduleName, beanName, distinctName, affinity);
    }
}