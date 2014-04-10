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

/**
 * An EJB client interceptor, possibly protocol-specific.  Client interceptors should not store any state locally since
 * they are shared between all threads.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface EJBClientInterceptor {

    /**
     * Handle the invocation.  Implementations may short-circuit the invocation by throwing an exception.  This method
     * should process any per-interceptor state and return.
     *
     * @param context the invocation context
     * @throws Exception if an invocation error occurs
     */
    void handleInvocation(EJBClientInvocationContext context) throws Exception;

    /**
     * Handle the invocation result.  The implementation should generally call {@link EJBClientInvocationContext#getResult()}
     * immediately and perform any post-invocation cleanup task in a finally block.
     *
     * @param context the invocation context
     * @return the invocation result, if any
     * @throws Exception if an invocation error occurred
     */
    Object handleInvocationResult(EJBClientInvocationContext context) throws Exception;
}
