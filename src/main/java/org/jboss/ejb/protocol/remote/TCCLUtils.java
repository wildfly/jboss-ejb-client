/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.protocol.remote;

import static java.lang.System.getSecurityManager;
import static java.lang.Thread.currentThread;
import static java.security.AccessController.doPrivileged;

import org.wildfly.security.manager.action.GetContextClassLoaderAction;
import org.wildfly.security.manager.action.SetContextClassLoaderAction;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class TCCLUtils {

    private static final ClassLoader SAFE_CL;

    static {
        ClassLoader safeClassLoader = TCCLUtils.class.getClassLoader();
        if (safeClassLoader == null) {
            safeClassLoader = ClassLoader.getSystemClassLoader();
        }
        if (safeClassLoader == null) {
            safeClassLoader = new ClassLoader() {
            };
        }
        SAFE_CL = safeClassLoader;
    }

    private TCCLUtils() {
        // forbidden instantiation
    }

    /**
     * Sets safe TCCL and returns previous one.
     * @return previous TCCL
     */
    static ClassLoader getAndSetSafeTCCL() {
        final ClassLoader old;
        if (getSecurityManager() != null) {
            old = doPrivileged(GetContextClassLoaderAction.getInstance());
            doPrivileged(new SetContextClassLoaderAction(SAFE_CL));
        } else {
            old = currentThread().getContextClassLoader();
            currentThread().setContextClassLoader(SAFE_CL);
        }
        return old;
    }

    /**
     * Resets TCCL to previous one.
     */
    static void resetTCCL(final ClassLoader old) {
        if (getSecurityManager() != null) {
            doPrivileged(new SetContextClassLoaderAction(old));
        } else {
            currentThread().setContextClassLoader(old);
        }
    }

}
