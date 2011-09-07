/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.ejb.client.naming;

import org.jboss.logging.Logger;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import java.net.URI;
import java.util.Hashtable;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class ServerObjectFactory implements ObjectFactory {
    private static final Logger log = Logger.getLogger(EJBObjectFactory.class);

    public static final String URI = "org.jboss.ejb.URI";

    public static Reference createReference() {
        final Reference ref = new Reference(Context.class.getName(), ServerObjectFactory.class.getName(), null);
        return ref;
    }

    public static Reference createReference(final URI uri) {
        final Reference ref = new Reference(Context.class.getName(), ServerObjectFactory.class.getName(), null);
        ref.add(new StringRefAddr("URI", uri.toString()));
        return ref;
    }

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        log.infof("env %s", environment);
        final Reference ref = (Reference) obj;
        final Reference refInfo = new Reference(Object.class.getName(), EJBObjectFactory.class.getName(), null);
        final RefAddr refAddr = ref.get("URI");
        final String uri;
        if (refAddr == null)
            uri = (String) environment.get(URI);
        else
            uri = (String) refAddr.getContent();
        if (uri == null)
            throw new NamingException("AS7-423: Badly configured ServerObjectFactory, missing URI");
        refInfo.add(new StringRefAddr("URI", uri));
        return new SimpleContext(refInfo, environment);
    }
}
