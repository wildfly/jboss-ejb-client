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

package org.jboss.ejb.server;

import java.util.List;
import java.util.Objects;

/**
 * A module availability listener for no-affinity EJBs.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ModuleAvailabilityListener {
    void moduleAvailable(List<ModuleIdentifier> modules);

    void moduleUnavailable(List<ModuleIdentifier> modules);

    final class ModuleIdentifier {
        private final String appName;
        private final String moduleName;
        private final String distinctName;
        private final int hashCode;

        public ModuleIdentifier(final String appName, final String moduleName, final String distinctName) {
            this.appName = appName;
            this.moduleName = moduleName;
            this.distinctName = distinctName;
            hashCode = Objects.hash(appName, moduleName, distinctName);
        }

        public String getAppName() {
            return appName;
        }

        public String getModuleName() {
            return moduleName;
        }

        public String getDistinctName() {
            return distinctName;
        }

        public int hashCode() {
            return hashCode;
        }

        public boolean equals(final Object obj) {
            return obj instanceof ModuleIdentifier && equals((ModuleIdentifier) obj);
        }

        boolean equals(ModuleIdentifier obj) {
            return this == obj || hashCode == obj.hashCode && Objects.equals(appName, obj.appName) && Objects.equals(moduleName, obj.moduleName) && Objects.equals(distinctName, obj.distinctName);
        }
    }
}
