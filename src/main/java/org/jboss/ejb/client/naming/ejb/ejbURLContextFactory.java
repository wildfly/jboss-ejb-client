/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.ejb.client.naming.ejb;


import javax.naming.Context;
import javax.naming.Name;
import javax.naming.spi.ObjectFactory;

import java.net.URI;
import java.util.Hashtable;

import org.jboss.ejb.client.Affinity;

/**
 * {@code ObjectFactory} for the {@code ejb:} namespace.  Note that this class must retain its odd capitalization due to the way that
 * JNDI locates its object factories.
 *
 * @author Stuart Douglas
 */
public class ejbURLContextFactory implements ObjectFactory {

    public Object getObjectInstance(final Object obj, final Name name, final Context nameCtx, final Hashtable<?, ?> environment) throws Exception {
        final Object providerUrl = environment.get(Context.PROVIDER_URL);
        if (providerUrl instanceof String) {
            return new EjbNamingContext(Affinity.forUri(new URI((String) providerUrl)));
        } else {
            return EjbNamingContext.NONE_ROOT;
        }
    }
}
