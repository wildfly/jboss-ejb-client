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

import java.util.Arrays;

import org.jboss.ejb.client.EJBHomeLocator;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EntityEJBLocator;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.marshalling.ObjectResolver;

final class ProtocolV1ObjectPreResolver implements ObjectResolver {

    static final ProtocolV1ObjectPreResolver INSTANCE = new ProtocolV1ObjectPreResolver();

    private ProtocolV1ObjectPreResolver() {
    }

    public Object readResolve(final Object replacement) {
        if (replacement instanceof Object[]) {
            final Object[] array = (Object[]) replacement;
            final int len = array.length;
            if (replacement instanceof StackTraceElement4[]) {
                return Arrays.copyOf(array, len, StackTraceElement[].class);
            } else if (replacement instanceof V1EntityLocator[]) {
                return Arrays.copyOf(array, len, EntityEJBLocator[].class);
            } else if (replacement instanceof V1HomeLocator[]) {
                return Arrays.copyOf(array, len, EJBHomeLocator[].class);
            } else if (replacement instanceof V1StatefulLocator[]) {
                return Arrays.copyOf(array, len, StatefulEJBLocator[].class);
            } else if (replacement instanceof V1StatelessLocator[]) {
                return Arrays.copyOf(array, len, StatelessEJBLocator[].class);
            } else if (replacement instanceof V1EJBLocator[]) {
                // this one has to be last
                return Arrays.copyOf(array, len, EJBLocator[].class);
            }
        }
        return replacement;
    }

    public Object writeReplace(final Object original) {
        return original;
    }
}
