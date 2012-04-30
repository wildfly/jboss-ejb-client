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
 * A {@link EJBClientPropertiesLoader} loads a EJB client properties file based on the following algorithm:
 * <ol>
 * <li>Checks if the <code>jboss.ejb.client.properties.file.path</code> system property is set. If it's set then this
 * {@link EJBClientPropertiesLoader} uses that as the file path to the properties file and loads and returns the properties.
 * </li>
 * <li>If the <code>jboss.ejb.client.properties.file.path</code> system property is <i>not</i> set then this {@link EJBClientPropertiesLoader}
 * then looks for a file named <code>jboss-ejb-client.properties</code> using an appropriate {@link ClassLoader}. If the
 * {@link Thread#getContextClassLoader() thread context classloader} is set, then it uses that to find the <code>jboss-ejb-client.properties</code>
 * file. Else it uses the {@link ClassLoader} which loaded the {@link EJBClientPropertiesLoader} class.
 * <p>
 * If such a file is found by the classloader, then this {@link EJBClientPropertiesLoader} loads and returns
 * those properties. Else it returns null</p>.
 * <p>
 * This step of looking for a <code>jboss-ejb-client.properties</code> file using a {@link ClassLoader} can be
 * completely skipped by setting the <code>jboss.ejb.client.properties.skip.classloader.scan</code> system property
 * to <code>true</code>
 * </p>
 * </li>
 * </ol>
 *
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
            InputStream fileStream = null;
            try {
                fileStream = new FileInputStream(ejbClientPropsFilePath);

                final Properties ejbClientProps = new Properties();
                ejbClientProps.load(fileStream);
                return ejbClientProps;

            } catch (FileNotFoundException e) {
                throw Logs.MAIN.failedToFindEjbClientConfigFileSpecifiedBySysProp(e, EJB_CLIENT_PROPS_FILE_SYS_PROPERTY);
            } catch (IOException e) {
                throw Logs.MAIN.failedToReadEjbClientConfigFile(e, ejbClientPropsFilePath);
            } finally {
                if (fileStream != null) {
                    try {
                        fileStream.close();
                    } catch (IOException e) {
                        logger.error("Failed to close file " + ejbClientPropsFilePath, e);
                    }
                }
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
                throw Logs.MAIN.failedToReadEjbClientConfigFile(e, EJB_CLIENT_PROPS_FILE_NAME);
            } finally {
                try {
                    clientPropsInputStream.close();
                } catch (IOException e) {
                    logger.error("Failed to close stream", e);
                }
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
