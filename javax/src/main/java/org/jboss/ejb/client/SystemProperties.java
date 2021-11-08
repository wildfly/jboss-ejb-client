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

/**
 * System properties being used in this package.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class SystemProperties {

    static final String DISCOVERY_ADDITIONAL_NODE_TIMEOUT = "org.jboss.ejb.client.discovery.additional-node-timeout";
    static final String DISCOVERY_BLACKLIST_TIMEOUT = "org.jboss.ejb.client.discovery.blacklist.timeout";
    static final String DISCOVERY_TIMEOUT = "org.jboss.ejb.client.discovery.timeout";
    static final String JBOSS_NODE_NAME = "jboss.node.name";
    static final String MAX_ENTRIES = "org.jboss.ejb.client.max-retries";
    static final String VIEW_ANNOTATION_SCAN_ENABLED = "org.jboss.ejb.client.view.annotation.scan.enabled";
    static final String WILDFLY_TESTSUITE_HACK = "org.jboss.ejb.client.wildfly-testsuite-hack";

    private SystemProperties() {
        // forbidden instantiation
    }

}
