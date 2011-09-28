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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * A receiver for EJB invocations.  Receivers can be associated with one or more client contexts.  This interface is
 * implemented by providers for EJB invocation services.
 *
 * @param <A> the invocation context attachment type specific to this receiver
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class EJBReceiver<A> extends Attachable {

    private final Set<ModuleID> accessibleModules = Collections.synchronizedSet(new HashSet<ModuleID>());

    /**
     * Register a new module to this receiver.
     *
     * @param appName the app name
     * @param moduleName the module name
     * @param distinctName the distinct name
     * @return {@code true} if this is a previously-unknown registration
     */
    public final boolean registerModule(String appName, String moduleName, String distinctName) {
        return accessibleModules.add(new ModuleID(appName, moduleName, distinctName));
    }

    final boolean acceptsModule(String appName, String moduleName, String distinctName) {
        return accessibleModules.contains(new ModuleID(appName, moduleName, distinctName));
    }

    /**
     * Handle the association of this EJB receiver with the EJB client context.  After this method is called,
     * the EJB receiver should notify the EJB receiver context of the available module identifiers that it can service,
     * and it should maintain that availability list for the life of the receiver association.
     *
     * @param context the receiver context
     */
    protected abstract void associate(EJBReceiverContext context);

    /**
     * Process the invocation.  Implementations of this method should always execute the operation asynchronously.
     *
     * @param clientInvocationContext the interceptor clientInvocationContext
     * @param receiverContext The EJB receiver context
     * @return the future result of this operation
     * @throws Exception if the operation throws an exception
     */
    protected abstract Future<?> processInvocation(EJBClientInvocationContext<A> clientInvocationContext, EJBReceiverContext receiverContext) throws Exception;

    /**
     * Open a session.  TODO: determine correct exception types.
     *
     * @param ejbReceiverContext The EJB receiver context
     * @param appName
     * @param moduleName
     * @param distinctName
     * @param beanName
     * @return the new session ID
     * @throws Exception if the target is not stateful or does not exist
     */
    protected abstract byte[] openSession(EJBReceiverContext ejbReceiverContext, String appName, String moduleName, String distinctName, String beanName) throws Exception;

    /**
     * Verify the existence of a remote EJB.
     *
     * @param appName
     * @param moduleName
     * @param distinctName
     * @param beanName
     * @throws Exception if the target does not exist
     */
    protected abstract void verify(String appName, String moduleName, String distinctName, String beanName) throws Exception;

    /**
     * Create the receiver-specific attachment for an invocation or serialization context.
     *
     * @return the attachment
     */
    protected abstract A createReceiverSpecific();

    static class ModuleID {
        private final String appName;
        private final String moduleName;
        private final String distinctName;
        private final int hashCode;

        ModuleID(final String appName, final String moduleName, final String distinctName) {
            if (moduleName == null) {
                throw new IllegalArgumentException("moduleName is null");
            }
            this.appName = appName == null ? moduleName : appName;
            this.moduleName = moduleName;
            this.distinctName = distinctName == null ? "" : distinctName;
            hashCode = this.appName.hashCode() + 31 * (this.moduleName.hashCode() + 31 * (this.distinctName.hashCode()));
        }

        public String getAppName() {
            return appName;
        }

        public String getModuleName() {
            return moduleName;
        }

        public String getDistinctName() {
            return distinctName;
        }

        public boolean equals(Object other) {
            return other instanceof ModuleID && equals((ModuleID)other);
        }

        public boolean equals(ModuleID other) {
            return this == other || other != null && appName.equals(other.appName) && moduleName.equals(other.moduleName) && distinctName.equals(other.distinctName);
        }

        public int hashCode() {
            return hashCode;
        }
    }
}
