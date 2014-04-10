/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client;

import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

/**
 * EJB metadata for stateful EJBs.
 *
 * @param <T> the remote interface type
 * @param <H> the home interface type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StatefulEJBMetaData<T extends EJBObject, H extends EJBHome> extends AbstractEJBMetaData<T, H> {

    private static final long serialVersionUID = 8596216068022888027L;

    /**
     * Construct a new instance.
     *
     * @param remoteInterfaceClass the remote interface class
     * @param homeLocator the EJB home locator
     */
    StatefulEJBMetaData(final Class<T> remoteInterfaceClass, final EJBHomeLocator<H> homeLocator) {
        super(remoteInterfaceClass, homeLocator);
    }
}
