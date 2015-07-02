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

import javax.ejb.CreateException;

/**
 * A receiver for EJB invocations.  Receivers can be associated with one or more client contexts.  This interface is
 * implemented by providers for EJB invocation services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class EJBReceiver extends Attachable {

    /**
     * Construct a new instance.
     */
    protected EJBReceiver() {
    }

    /**
     * Process the invocation.  Implementations of this method should always execute the operation asynchronously.  The
     * operation result should be passed in to the receiver invocation context.  To ensure ideal GC behavior, the
     * receiver should discard any reference to the invocation context(s) once the result producer has been set.
     *
     * @param receiverContext         The EJB receiver invocation context
     * @throws Exception if the operation throws an exception
     */
    protected abstract void processInvocation(EJBReceiverInvocationContext receiverContext) throws Exception;

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
     * @param statelessLocator the stateless locator
     * @param <T> the view type
     * @return the EJB locator for the newly opened session
     * @throws IllegalArgumentException if the session creation request is made for a bean which is <i>not</i> a stateful
     *                                  session bean
     */
    protected abstract <T> StatefulEJBLocator<T> createSession(final StatelessEJBLocator<T> statelessLocator) throws Exception;
}
