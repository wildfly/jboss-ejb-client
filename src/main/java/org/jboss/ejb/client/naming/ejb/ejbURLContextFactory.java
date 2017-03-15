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
package org.jboss.ejb.client.naming.ejb;


import javax.naming.Context;
import javax.naming.Name;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;

import org.jboss.ejb._private.Logs;
import org.wildfly.naming.client.WildFlyInitialContextFactory;

/**
 * Compatibility class.
 *
 * @deprecated Use {@link WildFlyInitialContextFactory} instead.
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class ejbURLContextFactory implements ObjectFactory {
    static {
        Logs.MAIN.ejbURLContextFactoryDeprecated();
    }

    private final WildFlyInitialContextFactory delegate = new WildFlyInitialContextFactory();

    public Object getObjectInstance(final Object obj, final Name name, final Context nameCtx, final Hashtable<?, ?> environment) throws Exception {
        return delegate.getInitialContext(environment);
    }
}

