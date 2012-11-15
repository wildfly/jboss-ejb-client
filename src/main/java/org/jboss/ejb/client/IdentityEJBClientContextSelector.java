/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
 * A {@link ContextSelector} which can select EJB client contexts based on a {@link EJBClientContextIdentifier}.
 *
 * @author Jaikiran Pai
 */
public interface IdentityEJBClientContextSelector extends ContextSelector<EJBClientContext> {

    /**
     * Associates the passed {@link EJBClientContext} to the {@link EJBClientContextIdentifier identifier}
     * <p/>
     * It's up to the individual implementations to decide whether to throw an exception
     * if there's already an {@link EJBClientContext} registered for the passed <code>identifier</code>
     *
     * @param identifier The EJB client context identifier
     * @param context    The EJB client context
     */
    void registerContext(EJBClientContextIdentifier identifier, EJBClientContext context);

    /**
     * Unregisters and returns a previously registered {@link EJBClientContext}, for the passed <code>identifier</code>.
     * If no {@link EJBClientContext} was registered for the passed <code>identifier</code>, then this method returns null.
     *
     * @param identifier The EJB client context identifier
     * @return Returns the previously registered {@link EJBClientContext} if any. Else returns null.
     */
    EJBClientContext unRegisterContext(EJBClientContextIdentifier identifier);

    /**
     * Returns a context for the passed <code>identifier</code>. If there's no such context, then null is returned.
     *
     * @param identifier Identity of the context. Cannot be null
     * @return The context or null if no such context exists
     * @throws IllegalArgumentException If the passed <code>identifier</code> is null.
     */
    EJBClientContext getContext(EJBClientContextIdentifier identifier);
}
