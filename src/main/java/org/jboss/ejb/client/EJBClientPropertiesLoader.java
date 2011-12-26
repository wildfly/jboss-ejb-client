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

package org.jboss.ejb.client;

import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Jaikiran Pai
 */
class EJBClientPropertiesLoader {

    private static final Logger logger = Logger.getLogger(EJBClientPropertiesLoader.class);

    private static final String EJB_CLIENT_PROPS_FILE_SYS_PROPERTY = "jboss.ejb.client.properties.file.path";
    private static final String EJB_CLIENT_PROPS_SKIP_CLASSLOADER_SCAN_SYS_PROPERTY = "jboss.ejb.client.properties.skip.classloader.scan";

    private static final String EJB_CLIENT_PROPS_FILE_NAME = "jboss-ejb-client.properties";


    static Properties loadEJBClientProperties() {
        // check system property
        final String ejbClientPropsFilePath = SecurityActions.getSystemProperty(EJB_CLIENT_PROPS_FILE_SYS_PROPERTY);
        if (ejbClientPropsFilePath != null) {
            //
            final InputStream fileStream;
            try {
                fileStream = new FileInputStream(ejbClientPropsFilePath);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Failed to find EJB client configuration file specified in " + EJB_CLIENT_PROPS_FILE_SYS_PROPERTY + " system property", e);
            }
            final Properties ejbClientProps = new Properties();
            try {
                ejbClientProps.load(fileStream);
                return ejbClientProps;

            } catch (IOException e) {
                throw new RuntimeException("Error reading EJB client properties file " + ejbClientPropsFilePath, e);
            }
        }
        // if classpath scan is disabled then skip looking for jboss-ejb-client.properties file in the classpath
        final String skipClasspathScan = SecurityActions.getSystemProperty(EJB_CLIENT_PROPS_SKIP_CLASSLOADER_SCAN_SYS_PROPERTY);
        if (skipClasspathScan != null && Boolean.valueOf(skipClasspathScan.trim())) {
            logger.debug(EJB_CLIENT_PROPS_SKIP_CLASSLOADER_SCAN_SYS_PROPERTY + " system property is set. " +
                    "Skipping classloader search for " + EJB_CLIENT_PROPS_FILE_NAME);
            return null;
        }
        final ClassLoader classLoader = getClientClassLoader();
        logger.debug("Looking for " + EJB_CLIENT_PROPS_FILE_NAME + " using classloader " + classLoader);
        // find from classloader
        final InputStream clientPropsInputStream = classLoader.getResourceAsStream(EJB_CLIENT_PROPS_FILE_NAME);
        if (clientPropsInputStream != null) {
            logger.debug("Found " + EJB_CLIENT_PROPS_FILE_NAME + " using classloader " + classLoader);
            final Properties clientProps = new Properties();
            try {
                clientProps.load(clientPropsInputStream);
                return clientProps;

            } catch (IOException e) {
                throw new RuntimeException("Could not load " + EJB_CLIENT_PROPS_FILE_NAME, e);
            }
        }
        return null;
    }

    /**
     * If {@link Thread#getContextClassLoader()} is null then returns the classloader which loaded
     * {@link EJBClientPropertiesLoader} class. Else returns the {@link Thread#getContextClassLoader()}
     *
     * @return
     */
    private static ClassLoader getClientClassLoader() {
        final ClassLoader tccl = SecurityActions.getContextClassLoader();
        if (tccl != null) {
            return tccl;
        }
        return EJBClientPropertiesLoader.class.getClassLoader();
    }

}
