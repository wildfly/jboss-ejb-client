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

import javax.transaction.xa.XAException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A receiver for EJB invocations.  Receivers can be associated with one or more client contexts.  This interface is
 * implemented by providers for EJB invocation services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class EJBReceiver extends Attachable {

    private final Set<ModuleID> accessibleModules = Collections.synchronizedSet(new HashSet<ModuleID>());

    private final String nodeName;

    public EJBReceiver(final String nodeName) {
        if (nodeName == null) {
            throw Logs.MAIN.paramCannotBeNull("Node name");
        }
        this.nodeName = nodeName;
    }

    /**
     * Register a new module to this receiver.
     *
     * @param appName      the app name
     * @param moduleName   the module name
     * @param distinctName the distinct name
     * @return {@code true} if this is a previously-unknown registration
     */
    protected final boolean registerModule(String appName, String moduleName, String distinctName) {
        return accessibleModules.add(new ModuleID(appName, moduleName, distinctName));
    }

    /**
     * Deregister a module from this receiver.
     *
     * @param appName      the app name
     * @param moduleName   the module name
     * @param distinctName the distinct name
     * @return {@code true} if the registration was present
     */
    protected final boolean deregisterModule(String appName, String moduleName, String distinctName) {
        return accessibleModules.remove(new ModuleID(appName, moduleName, distinctName));
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
     * Process the invocation.  Implementations of this method should always execute the operation asynchronously.  The
     * operation result should be passed in to the receiver invocation context.  To ensure ideal GC behavior, the
     * receiver should discard any reference to the invocation context(s) once the result producer has been set.
     *
     * @param clientInvocationContext the interceptor clientInvocationContext
     * @param receiverContext         The EJB receiver invocation context
     * @throws Exception if the operation throws an exception
     */
    protected abstract void processInvocation(EJBClientInvocationContext clientInvocationContext, EJBReceiverInvocationContext receiverContext) throws Exception;

    /**
     * Attempt to cancel an invocation.  Implementations should make a reasonable effort to determine whether
     * the operation was actually cancelled; however it is permissible to fall back to returning {@code false} if
     * it cannot be discovered.
     *
     * @param clientInvocationContext the original clientInvocationContext
     * @param receiverContext         the EJB receiver invocation context
     * @return {@code true} if the operation was definitely cancelled immediately, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    protected boolean cancelInvocation(EJBClientInvocationContext clientInvocationContext, EJBReceiverInvocationContext receiverContext) {
        return false;
    }

    /**
     * Creates a session for a stateful session bean represented by the passed app name, module name, distinct name
     * and bean name combination. Returns a {@link StatefulEJBLocator} representing the newly created session.
     *
     * @param context      The receiver context
     * @param viewType     View class
     * @param appName      The application name
     * @param moduleName   The module name
     * @param distinctName The distinct name
     * @param beanName     The name of the bean
     * @param <T>
     * @return
     * @throws IllegalArgumentException If the session creation request is made for a bean which is <i>not</i> a stateful
     *                                  session bean.
     */
    protected abstract <T> StatefulEJBLocator<T> openSession(final EJBReceiverContext context, final Class<T> viewType, final String appName, final String moduleName, final String distinctName, final String beanName) throws IllegalArgumentException;

    /**
     * Verify the existence of a remote EJB. Returns true if a bean identified by the passed appname, module name,
     * distinct name and bean name combination exists. Else returns false.
     *
     * @param appName      The application name
     * @param moduleName   The module name
     * @param distinctName The distinct name
     * @param beanName     The bean name
     */
    protected abstract boolean exists(String appName, String moduleName, String distinctName, String beanName);

    /**
     * Send a transaction-prepare message for the given transaction ID.
     *
     * @param context       the receiver context
     * @param transactionID the transaction ID
     * @return a value indicating the resource manager's vote on the outcome of the transaction; the possible values are: {@code XA_RDONLY}
     *         or {@code XA_OK}
     * @throws XAException to roll back the transaction
     */
    @SuppressWarnings("unused")
    protected int sendPrepare(final EJBReceiverContext context, final TransactionID transactionID) throws XAException {
        throw new XAException(XAException.XA_RBOTHER);
    }

    /**
     * Send a transaction-commit message for the given transaction ID.
     *
     * @param context       the receiver context
     * @param transactionID the transaction ID
     * @param onePhase      {@code true} to perform a one-phase commit
     * @throws XAException if the transaction commit failed
     */
    @SuppressWarnings("unused")
    protected void sendCommit(final EJBReceiverContext context, final TransactionID transactionID, final boolean onePhase) throws XAException {
        throw new XAException(XAException.XA_RBOTHER);
    }

    /**
     * Send a transaction-rollback message for the given transaction ID.
     *
     * @param context       the receiver context
     * @param transactionID the transaction ID
     * @throws XAException if the transaction rollback failed
     */
    @SuppressWarnings("unused")
    protected void sendRollback(final EJBReceiverContext context, final TransactionID transactionID) throws XAException {
        throw new XAException(XAException.XA_RBOTHER);
    }

    /**
     * Send a transaction-forget message for the given transaction ID.
     *
     * @param context       the receiver context
     * @param transactionID the transaction ID
     * @throws XAException if the forget message failed
     */
    @SuppressWarnings("unused")
    protected void sendForget(final EJBReceiverContext context, final TransactionID transactionID) throws XAException {
        throw new XAException(XAException.XA_RBOTHER);
    }

    /**
     * Returns the node name corresponding to this receiver. This method will <i>not</i> return a null value.
     *
     * @return
     */
    protected final String getNodeName() {
        return this.nodeName;
    }

    /**
     * The before-completion hook.  Cause all connected subordinate transaction managers to invoke their beforeCompletion
     * methods.  This method should not return until all remote beforeCompletions have been called.
     *
     * @param context       the receiver context
     * @param transactionID the transaction ID
     */
    @SuppressWarnings("unused")
    protected void beforeCompletion(final EJBReceiverContext context, final TransactionID transactionID) {
    }

    static class ModuleID {
        private final String appName;
        private final String moduleName;
        private final String distinctName;
        private final int hashCode;

        ModuleID(final String appName, final String moduleName, final String distinctName) {
            if (moduleName == null) {
                Logs.MAIN.paramCannotBeNull("Module name");
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
            return other instanceof ModuleID && equals((ModuleID) other);
        }

        public boolean equals(ModuleID other) {
            return this == other || other != null && appName.equals(other.appName) && moduleName.equals(other.moduleName) && distinctName.equals(other.distinctName);
        }

        public int hashCode() {
            return hashCode;
        }
    }
}
