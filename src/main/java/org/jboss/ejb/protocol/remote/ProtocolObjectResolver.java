/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

import org.jboss.marshalling.ObjectResolver;
import org.jboss.marshalling.SerializabilityChecker;

/**
 *
 */
abstract class ProtocolObjectResolver implements ObjectResolver {
    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<>();

    protected ProtocolObjectResolver() {
    }

    public Object writeReplace(final Object o) {
        if (o != null && ENABLED.get() == Boolean.TRUE) {
            final Class<?> clazz = o.getClass();
            if (! clazz.isArray() && ! SerializabilityChecker.DEFAULT.isSerializable(clazz)) {
                return null;
            }
        }
        return o;
    }

    static void enableNonSerReplacement() {
        ENABLED.set(Boolean.TRUE);
    }

    static void disableNonSerReplacement() {
        ENABLED.set(Boolean.FALSE);
    }
}
