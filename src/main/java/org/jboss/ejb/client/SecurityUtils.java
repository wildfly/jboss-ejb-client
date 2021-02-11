/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Security sensitive operations being used in this package.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class SecurityUtils {

    private SecurityUtils() {
        // forbidden instantiation
    }

    static boolean getBoolean(final String propertyName) {
        return getBoolean(propertyName, false);
    }

    static boolean getBoolean(final String propertyName, final boolean defaultValue) {
        try {
            final String propertyValue = getString(propertyName);
            return propertyValue != null ? Boolean.valueOf(propertyValue) : defaultValue;
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    static int getInteger(final String propertyName, final int defaultValue) {
        try {
            final String propertyValue = getString(propertyName);
            return propertyValue != null ? Integer.parseInt(propertyValue) : defaultValue;
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    static long getLong(final String propertyName, final long defaultValue) {
        try {
            final String propertyValue = getString(propertyName);
            return propertyValue != null ? Long.parseLong(propertyValue) : defaultValue;
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    static String getString(final String propertyName) {
        return getString(propertyName, null);
    }

    static String getString(final String propertyName, final String defaultValue) {
        return WildFlySecurityManager.getPropertyPrivileged(propertyName, defaultValue);
    }

}
