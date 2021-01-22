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

package org.jboss.ejb._private;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * System properties being used.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
public final class SystemProperties {

    public static final String DESTINATION_RECHECK_INTERVAL = "org.jboss.ejb.client.destination-recheck-interval";
    public static final String DISCOVERY_ADDITIONAL_NODE_TIMEOUT = "org.jboss.ejb.client.discovery.additional-node-timeout";
    public static final String DISCOVERY_BLACKLIST_TIMEOUT = "org.jboss.ejb.client.discovery.blacklist.timeout";
    public static final String DISCOVERY_TIMEOUT = "org.jboss.ejb.client.discovery.timeout";
    public static final String EXPAND_PASSWORDS = "jboss-ejb-client.expandPasswords";
    public static final String JBOSS_NODE_NAME = "jboss.node.name";
    public static final String MAX_ENTRIES = "org.jboss.ejb.client.max-retries";
    public static final String PROPERTIES_FILE_PATH = "jboss.ejb.client.properties.file.path";
    public static final String QUIET_AUTH = "jboss.sasl.local-user.quiet-auth";
    public static final String USER_DIR = "user.dir";
    public static final String VIEW_ANNOTATION_SCAN_ENABLED = "org.jboss.ejb.client.view.annotation.scan.enabled";
    public static final String WILDFLY_TESTSUITE_HACK = "org.jboss.ejb.client.wildfly-testsuite-hack";

    private SystemProperties() {
        // forbidden instantiation
    }

    public static boolean getBoolean(final String propertyName) {
        return getBoolean(propertyName, false);
    }

    public static boolean getBoolean(final String propertyName, final boolean defaultValue) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) return Boolean.parseBoolean(System.getProperty(propertyName, String.valueOf(defaultValue)));
        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
                return Boolean.valueOf(System.getProperty(propertyName, String.valueOf(defaultValue)));
            }
        });
    }

    public static int getInteger(final String propertyName, final int defaultValue) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) return Integer.getInteger(propertyName, defaultValue);
        return AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            @Override
            public Integer run() {
                return Integer.getInteger(propertyName, defaultValue);
            }
        });
    }

    public static long getLong(final String propertyName, final long defaultValue) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) return Long.getLong(propertyName, defaultValue);
        return AccessController.doPrivileged(new PrivilegedAction<Long>() {
            @Override
            public Long run() {
                return Long.getLong(propertyName, defaultValue);
            }
        });
    }

    public static String getString(final String propertyName) {
        return getString(propertyName, null);
    }

    public static String getString(final String propertyName, final String defaultValue) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) return System.getProperty(propertyName, defaultValue);
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getProperty(propertyName, defaultValue);
            }
        });
    }
}
