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
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import java.net.URI;
import java.util.Hashtable;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class EJBObjectFactory implements ObjectFactory {
    private static final Logger log = Logger.getLogger(EJBObjectFactory.class);

    public static Reference createReference(final URI uri, final String appName, final String moduleName, final String beanName, final Class<?> viewType) {
        final Reference ref = new Reference(viewType.getName(), EJBObjectFactory.class.getName(), null);
        ref.add(new StringRefAddr("URI", uri.toString()));
        ref.add(new StringRefAddr("appName", appName));
        ref.add(new StringRefAddr("moduleName", moduleName));
        ref.add(new StringRefAddr("beanName", beanName));
        return ref;
    }

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        final Reference ref = (Reference) obj;
        final URI uri = new URI(string(ref, "URI"));
        int posn = 0;
        final String appName;
        {
            final RefAddr refAddr = ref.get("appName");
            if (refAddr == null)
                appName = name.get(posn++);
            else
                appName = (String) refAddr.getContent();
        }
        final String moduleName;
        {
            final RefAddr refAddr = ref.get("moduleName");
            if (refAddr == null)
                moduleName = name.get(posn++);
            else
                moduleName = (String) refAddr.getContent();
        }
        final String beanName;
        final String viewName;
        {
            final RefAddr refAddr = ref.get("beanName");
            if (refAddr == null) {
                final String n = name.get(posn++);
                final int i = n.indexOf('#');
                beanName = n.substring(0, i);
                viewName = n.substring(i + 1);
            }
            else {
                beanName = (String) refAddr.getContent();
                viewName = ref.getClassName();
            }
        }
        // TODO: the real viewType is dependent upon the callers CL (/ TCCL)
        final Class<?> viewType = Class.forName(viewName);
        return null;// EJBClient.getProxy(uri, appName, moduleName,  viewType, beanName);
    }

    private static String string(final Reference ref, final String addrType) {
        final RefAddr refAddr = ref.get(addrType);
        assert refAddr != null : "can't find RefAddr " + addrType;
        return (String) refAddr.getContent();
    }
}
