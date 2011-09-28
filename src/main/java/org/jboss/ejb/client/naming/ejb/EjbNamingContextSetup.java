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

/**
 * Static class that sets up the ejb: JNDI namespace.
 *
 * @author Stuart Douglas
 */
public class EjbNamingContextSetup {

    private static boolean setup = false;

    private static final String PACKAGE = "org.jboss.ejb.client.naming";

    /**
     * Set up the EJB namespace by editing the {@code java.naming.factory.url.pkgs} system property.
     */
    public static synchronized void setupEjbNamespace() {
        if (setup) {
            return;
        }
        setup = true;
        final String packages = SecurityActions.getSystemProperty(Context.URL_PKG_PREFIXES);
        if (packages == null || packages.isEmpty()) {
            SecurityActions.setSystemProperty(Context.URL_PKG_PREFIXES, PACKAGE);
        } else {
            SecurityActions.setSystemProperty(Context.URL_PKG_PREFIXES, packages + ":" + PACKAGE);
        }
    }

    private EjbNamingContextSetup() {

    }
}
