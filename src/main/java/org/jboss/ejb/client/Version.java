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

package org.jboss.ejb.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Version {
    private Version() {
    }

    private static final String JAR_NAME;
    private static final String VERSION;

    static {
        Properties versionProps = new Properties();
        String jarName = "(unknown)";
        String versionString = "(unknown)";
        try {
            final InputStream stream = Version.class.getResourceAsStream("Version.properties");
            try {
                final InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                try {
                    versionProps.load(reader);
                    jarName = versionProps.getProperty("jarName", jarName);
                    versionString = versionProps.getProperty("version", versionString);
                } finally {
                    try {
                        reader.close();
                    } catch (Throwable ignored) {
                    }
                }
            } finally {
                try {
                    stream.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        JAR_NAME = jarName;
        VERSION = versionString;
    }

    /**
     * Get the name of the program JAR.
     *
     * @return the name
     */
    public static String getJarName() {
        return JAR_NAME;
    }

    /**
     * Get the version string.
     *
     * @return the version string
     */
    public static String getVersionString() {
        return VERSION;
    }

    /**
     * Print out the current version on {@code System.out}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.printf("JBoss EJB Client version %s\n", VERSION);
    }
}
