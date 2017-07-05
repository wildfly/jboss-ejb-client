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

import org.wildfly.common.Assert;
import org.wildfly.security.permission.AbstractNameSetOnlyPermission;
import org.wildfly.security.util.StringEnumeration;
import org.wildfly.security.util.StringMapping;

/**
 * The class for various general EJB client permissions.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientPermission extends AbstractNameSetOnlyPermission<EJBClientPermission> {

    private static final long serialVersionUID = 8406360684253911321L;

    private static final StringEnumeration names = StringEnumeration.of(
        "createContext",
        "createReceiver",
        "changeWeakAffinity",
        "changeStrongAffinity"
    );

    private static final StringMapping<EJBClientPermission> mapping = new StringMapping<>(names, EJBClientPermission::new);

    public static final EJBClientPermission CREATE_CONTEXT = mapping.getItemById(0);
    public static final EJBClientPermission CREATE_RECEIVER = mapping.getItemById(1);
    public static final EJBClientPermission CHANGE_WEAK_AFFINITY = mapping.getItemById(2);
    public static final EJBClientPermission CHANGE_STRONG_AFFINITY = mapping.getItemById(3);

    private static final EJBClientPermission ALL = new EJBClientPermission("*");

    public EJBClientPermission(final String name) {
        super(name, names);
    }

    public EJBClientPermission(final String name, @SuppressWarnings("unused") final String actions) {
        super(name, names);
        if (actions != null && ! actions.isEmpty()) {
            throw new IllegalArgumentException("Unsupported actions string '" + actions + "'");
        }
    }

    public EJBClientPermission withName(final String name) {
        Assert.checkNotNullParam("name", name);
        return "*".equals(name) ? ALL : mapping.getItemByString(name);
    }
}
