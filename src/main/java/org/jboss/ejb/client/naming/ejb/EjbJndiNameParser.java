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

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for parsing ejb: JNDI names.
 *
 * @author Stuart Douglas
 */
class EjbJndiNameParser {

    /**
     * Parses a ejb: jndi name, returning an EjbIdentifier.
     * <p/>
     * This is capable of parsing partial names, so a lookup of ejb:applicationName will
     * result in an EjbIdentifier with only the applicationName set.
     * <p/>
     * Names of the form ejb:a/b/c are taken to be a partial app/module/distinct name
     * lookup, rather than a lookup of an ejb without a view. Specifying the ejbName
     * without also specifying the view name will result in an {@link IllegalArgumentException}
     *
     * @param name The name to parse
     * @return The identifier
     */
    public static EjbJndiIdentifier parse(final String name) {
        if (!name.startsWith("ejb:")) {
            throw new IllegalArgumentException("Name must start with :ejb");
        }
        int pos = 3;
        while (pos < name.length() && name.charAt(pos) != '/') {
            ++pos;
        }
        final String application = name.substring(4, pos);
        if (pos >= name.length() - 1) {
            return new EjbJndiIdentifier(application, null, null, null, null, null);
        }
        return parse(application, name.substring(pos + 1));

    }

    /**
     * Parse the remaining JNDI name for the given application name
     *
     * @param applicationName The application name
     * @param remaining       The remaining name
     * @return The EjbIdentifier
     */
    public static final EjbJndiIdentifier parse(final String applicationName, final String remaining) {
        int pos = 0;
        while (pos < remaining.length() && remaining.charAt(pos) != '/') {
            ++pos;
        }
        final String moduleName = remaining.substring(0, pos);
        if (pos >= remaining.length() - 1) {
            return new EjbJndiIdentifier(applicationName, moduleName, null, null, null, null);
        }
        return parse(applicationName, moduleName, remaining.substring(pos + 1));
    }

    /**
     * Parse the remaining JNDI name, given the app and module name
     *
     * @param applicationName The application name
     * @param moduleName      The module name
     * @param remaining       The remaining name
     * @return The identifier
     */
    public static final EjbJndiIdentifier parse(final String applicationName, final String moduleName, final String remaining) {
        int pos = 0;
        while (pos < remaining.length() && remaining.charAt(pos) != '/' && remaining.charAt(pos) != '!') {
            ++pos;
        }
        final String part = remaining.substring(0, pos);
        if (pos >= remaining.length() - 1) {
            return new EjbJndiIdentifier(applicationName, moduleName, part, null, null, null);
        }
        if (remaining.charAt(pos) == '/') {
            return parse(applicationName, moduleName, part, remaining.substring(pos + 1));
        }
        return parse(applicationName, moduleName, "", part, remaining.substring(pos + 1));
    }

    /**
     * Parse the remaining JNDI name, given the app, module and distinct name
     *
     * @param applicationName The application name
     * @param moduleName      The module name
     * @param distinctName    The distinct name
     * @param remaining       The remaining name
     * @return The identifier
     */
    public static final EjbJndiIdentifier parse(final String applicationName, final String moduleName, final String distinctName, final String remaining) {
        int pos = 0;
        while (pos < remaining.length() && remaining.charAt(pos) != '!') {
            ++pos;
        }
        if (pos >= remaining.length() - 1) {
            throw new IllegalArgumentException("EjbName did not specify a view using ejbName!com.my.ViewClass syntax");
        }
        final String ejbName = remaining.substring(0, pos);
        return parse(applicationName, moduleName, distinctName, ejbName, remaining.substring(pos + 1));
    }

    /**
     * Parse the remaining JNDI name, given the app, module, distinct and ejb name
     *
     * @param applicationName The application name
     * @param moduleName      The module name
     * @param distinctName    The distinct name
     * @param ejbName         The ejb name
     * @param remaining       The remaining name
     * @return The identifier
     */
    public static final EjbJndiIdentifier parse(final String applicationName, final String moduleName, final String distinctName, final String ejbName, final String remaining) {
        int pos = 0;
        while (pos < remaining.length() && remaining.charAt(pos) != '?') {
            ++pos;
        }
        final String viewName;
        final Map<String, String> viewOptions = new HashMap<String, String>();
        if (pos >= remaining.length() - 1) {
            viewName = remaining;
        } else {
            viewName = remaining.substring(0, pos);
            //we don't handle escaping at all here, so you can't have special characters
            //in the view options. This should not be a problem in practice
            final String[] options = remaining.substring(pos + 1).split("&");
            for (final String option : options) {
                final String[] parts = option.split("=");
                if (parts.length == 1) {
                    viewOptions.put(parts[0], null);
                } else if (parts.length == 2) {
                    viewOptions.put(parts[0], parts[1]);
                } else {
                    throw new IllegalArgumentException("Invalid option " + option);
                }
            }
        }
        return new EjbJndiIdentifier(applicationName, moduleName, distinctName, ejbName, viewName, viewOptions);
    }

    /**
     * Returns true if the passed <code>name</code> represents the JNDI name for EJBClientContext. Else
     * returns false.
     *
     * @param name The name being checked
     * @return
     */
    public static final boolean isEJBClientContextJNDIName(final String name) {
        return "ejb:/EJBClientContext".equals(name);
    }
}
