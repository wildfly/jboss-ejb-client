/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.server;

import java.util.Map;

import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBMethodLocator;
import org.wildfly.common.Assert;
import org.wildfly.common.annotation.NotNull;

/**
 * An EJB method invocation request.
 *
 * @param <T> the target EJB type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface InvocationRequest<T> extends Request {

    /**
     * Get the invocation attachment map.
     *
     * @return the invocation attachment map (must not be {@code null})
     */
    @NotNull
    Map<String, Object> getAttachments();

    /**
     * Get the EJB method locator.
     *
     * @return the EJB method locator (must not be {@code null})
     */
    @NotNull
    EJBMethodLocator getMethodLocator();

    /**
     * Get the method invocation parameters.
     *
     * @return the method invocation parameters (must not be {@code null})
     */
    @NotNull
    Object[] getParameters();

    /**
     * Get the identifier of the target EJB.  The default implementation delegates to {@link #getEJBLocator()} and
     * returns the identifier portion.
     *
     * @return the identifier of the target EJB (must not be {@code null})
     */
    @NotNull
    default EJBIdentifier getEJBIdentifier() {
        return getEJBLocator().getIdentifier();
    }

    /**
     * Get the EJB locator of the request.  This contains the same identifier as is returned with
     * {@link #getEJBIdentifier()}, but of a type corresponding to the EJB type, and with a resolved EJB class
     * and affinity.
     *
     * @return the EJB locator (must not be {@code null})
     */
    @NotNull
    EJBLocator<T> getEJBLocator();

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
     * Write the invocation result message.
     *
     * @param result the invocation result
     */
    void writeInvocationResult(Object result);

    /**
     * Safely narrow the invocation's generic type to the given type.  This is a convenience method and does not
     * create any new objects; it simply tests the argument and re-casts this instance as the narrowed type.
     *
     * @param clazz the type class instance (must not be {@code null})
     * @param <S> the type to narrow to
     * @return this instance, narrowed to the given type (must not be {@code null})
     * @throws ClassCastException if the narrow operation did not succeed
     */
    @SuppressWarnings("unchecked")
    @NotNull
    default <S> InvocationRequest<S> narrowTo(@NotNull Class<S> clazz) throws ClassCastException {
        Assert.checkNotNullParam("clazz", clazz);
        getEJBLocator().narrowTo(clazz);
        return (InvocationRequest<S>) this;
    }
}
