/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client.test.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.logging.Logger;

/**
 * @author Jaikiran Pai
 */
public class ResourceSwitchingClassLoader extends ClassLoader {

    private static final Logger logger = Logger.getLogger(ResourceSwitchingClassLoader.class);

    private final String resourceName;
    private final String altResourceName;

    public ResourceSwitchingClassLoader(final String resourceName, final String altResourceName) {
        if (resourceName == null || resourceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource name cannot be null or empty");
        }
        if (altResourceName == null || altResourceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Alt resource name cannot be null or empty");
        }
        this.resourceName = resourceName;
        this.altResourceName = altResourceName;
    }

    @Override
    public URL getResource(String name) {
        if (this.resourceName.equals(name)) {
            final URL url = super.getResource(this.altResourceName);
            logger.info("getResource was called for " + name + " ,returning " + url + " for " + this.altResourceName + " resource instead");
            return url;
        }
        return super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if (this.resourceName.equals(name)) {
            final InputStream is = super.getResourceAsStream(this.altResourceName);
            logger.info("getResourceAsStream was called for " + name + " ,returning " + is + " for " + this.altResourceName + " resource instead");
            return is;
        }
        return super.getResourceAsStream(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (this.resourceName.equals(name)) {
            final Enumeration<URL> resources = super.getResources(this.altResourceName);
            logger.info("getResources was called for " + name + " ,returning " + resources + " for " + this.altResourceName + " resource instead");
            return resources;
        }
        return super.getResources(name);
    }
}
