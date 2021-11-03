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

import org.wildfly.security.permission.AbstractBooleanPermission;

/**
 * Represents permission to invoke an EJB remotely
 *
 * @author Stuart Douglas
 */
public class RemoteEJBPermission extends AbstractBooleanPermission<RemoteEJBPermission> {

    /**
     * Construct a new instance.
     */
    public RemoteEJBPermission() {
    }

    /**
     * Construct a new instance.
     *
     * @param name ignored
     */
    public RemoteEJBPermission(@SuppressWarnings("unused") final String name) {
    }

    /**
     * Construct a new instance.
     *
     * @param name ignored
     * @param actions ignored
     */
    public RemoteEJBPermission(@SuppressWarnings("unused") final String name, @SuppressWarnings("unused") final String actions) {
    }

    private static final RemoteEJBPermission INSTANCE = new RemoteEJBPermission();

    /**
     * Get the instance of this class.
     *
     * @return the instance of this class
     */
    public static RemoteEJBPermission getInstance() {
        return INSTANCE;
    }
}
