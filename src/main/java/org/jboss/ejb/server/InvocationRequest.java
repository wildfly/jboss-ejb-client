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

package org.jboss.ejb.server;

import java.io.IOException;
import java.util.Map;

import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBMethodLocator;
import org.wildfly.common.annotation.NotNull;

/**
 * An EJB method invocation request.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface InvocationRequest extends Request {

    /**
     * Get the resolved content of the request.
     *
     * @param classLoader the class loader to use to resolve the request
     * @return the request content
     * @throws IOException if the request content failed to be deserialized
     * @throws ClassNotFoundException if a class in the request content failed to be found in the given class loader
     */
    Resolved getRequestContent(ClassLoader classLoader) throws IOException, ClassNotFoundException;

    /**
     * Get the EJB method locator.
     *
     * @return the EJB method locator (must not be {@code null})
     */
    @NotNull
    EJBMethodLocator getMethodLocator();

    /**
     * Write a message indicating that the method is not found on the EJB.  The request should be abandoned after
     * invoking this method.
     */
    void writeNoSuchMethod();

    /**
     * Write a message indicating that the session is inactive.  The request should be abandoned after
     * invoking this method.
     */
    void writeSessionNotActive();


    /**
     * Write a message indicating that this is an async request. If this is not required by the underlying protocol then
     * this may be a noop
     */
    default void writeProceedAsync() {}

    /**
     * The resolved content of the request.
     */
    interface Resolved {
        /**
         * Get the invocation attachment map.  The serializable attachments will be returned to the client.
         *
         * @return the invocation attachment map (must not be {@code null})
         */
        @NotNull
        Map<String, Object> getAttachments();

        /**
         * Get the method invocation parameters.
         *
         * @return the method invocation parameters (must not be {@code null})
         */
        @NotNull
        Object[] getParameters();

        /**
         * Get the EJB locator of the request.  This contains the same identifier as is returned with
         * {@link #getEJBIdentifier()}, but of a type corresponding to the EJB type, and with a resolved EJB class
         * and affinity.
         *
         * @return the EJB locator (must not be {@code null})
         */
        @NotNull
        EJBLocator<?> getEJBLocator();

        /**
         * Get the weak affinity of the request.
         *
         * @return the weak affinity of the request (must not be {@code null})
         */
        @NotNull
        default Affinity getWeakAffinity() {
            return Affinity.NONE;
        }

        /**
         * Determine if the request has a transaction.
         *
         * @return {@code true} if there is a transaction context with this request
         */
        boolean hasTransaction();

        /**
         * Get the inflowed transaction of the request.  This should not be called unless it is desired to actually inflow
         * the transaction; doing so without using the transaction will cause needless work for the transaction coordinator.
         * To perform transaction checks, use {@link #hasTransaction()} first.  This method should only be called one time
         * as it will inflow the transaction when called.
         * <p>
         * If a transaction is present but transaction inflow has failed, a {@link SystemException} is thrown.  In this case,
         * the invocation should fail.
         * <p>
         * It is the caller's responsibility to check the status of the returned transaction to ensure that it is in an
         * active state; failure to do so can result in undesirable behavior.
         *
         * @return the transaction, or {@code null} if there is none for the request
         * @throws SystemException if inflowing the transaction failed
         * @throws IllegalStateException if this method is called more than one time
         */
        Transaction getTransaction() throws SystemException, IllegalStateException;

        /**
         * Write the invocation result message.
         *
         * @param result the invocation result
         */
        void writeInvocationResult(Object result);
    }
}
