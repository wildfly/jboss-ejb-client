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

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.security.PrivilegedAction;

import org.jboss.marshalling.ContextClassResolver;
import org.jboss.marshalling.Unmarshaller;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ProtocolClassResolver extends ContextClassResolver {
    static final ProtocolClassResolver INSTANCE = new ProtocolClassResolver();

    private ProtocolClassResolver() {
    }

    public Class<?> resolveProxyClass(final Unmarshaller unmarshaller, final String[] interfaces) throws IOException, ClassNotFoundException {
        final int length = interfaces.length;
        final Class<?>[] classes = new Class<?>[length];

        for (int i = 0; i < length; ++ i) {
            classes[i] = this.loadClass(interfaces[i]);
        }

        final ClassLoader classLoader;
        if (length == 1) {
            classLoader = doPrivileged((PrivilegedAction<ClassLoader>) classes[0]::getClassLoader);
        } else {
            classLoader = getClassLoader();
        }

        return Proxy.getProxyClass(classLoader, classes);
    }
}
