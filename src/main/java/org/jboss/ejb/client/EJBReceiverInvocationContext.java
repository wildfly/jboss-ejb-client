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

import java.util.Arrays;

/**
 * The context used for an EJB receiver to return the result of an invocation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBReceiverInvocationContext {

    private final EJBClientInvocationContext clientInvocationContext;

    EJBReceiverInvocationContext(final EJBClientInvocationContext clientInvocationContext) {
        this.clientInvocationContext = clientInvocationContext;
    }

    /**
     * Indicate that the invocation should proceed asynchronously, if it isn't already.
     */
    public void proceedAsynchronously() {
        clientInvocationContext.proceedAsynchronously();
    }

    /**
     * Indicate that the invocation result is ready.  The given producer should either return the final
     * invocation result or throw an appropriate exception.  Any unmarshalling is expected to be deferred until
     * the result producer is called, in order to offload the work on the invoking thread even in the presence of
     * asynchronous invocation.
     *
     * @param resultProducer the result producer
     */
    public void resultReady(ResultProducer resultProducer) {
        clientInvocationContext.resultReady(resultProducer);
    }

    /**
     * Indicate that the request was successfully cancelled and that no result is forthcoming.
     */
    public void requestCancelled() {
        clientInvocationContext.cancelled();
    }

    /**
     * Get the client invocation context associated with this receiver invocation context.
     *
     * @return the client invocation context
     */
    public EJBClientInvocationContext getClientInvocationContext() {
        return clientInvocationContext;
    }

    /**
     * A result producer for invocation.
     */
    public interface ResultProducer {

        /**
         * Get the result.
         *
         * @return the result
         * @throws Exception if the result could not be acquired or the operation failed
         */
        Object getResult() throws Exception;

        /**
         * Discard the result, indicating that it will not be used.
         */
        void discardResult();

        /**
         * A result producer for failure cases.
         */
        class Failed implements ResultProducer {
            private final Exception cause;

            /**
             * Construct a new instance.
             *
             * @param cause the failure cause
             */
            public Failed(final Exception cause) {
                this.cause = cause;
            }

            public Object getResult() throws Exception {
                final Exception cause = this.cause;
                final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
                cause.setStackTrace(Arrays.copyOfRange(stackTrace, 1, stackTrace.length));
                throw cause;
            }

            public void discardResult() {
            }
        }

        class Immediate implements ResultProducer {
            private final Object result;

            public Immediate(final Object result) {
                this.result = result;
            }

            public Object getResult() throws Exception {
                return result;
            }

            public void discardResult() {
            }
        }
    }

}
