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

import org.jboss.ejb.client.SessionID;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.common.function.ExceptionSupplier;

/**
 * An EJB session-open request.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface SessionOpenRequest extends Request {
    /**
     * Execute the session open request.  The given exception supplier is called as per normal, but the result must be
     * {@code null}.  Use {@link #convertToStateful(SessionID)} to establish the new EJB session.  The default implementation
     * delegates to {@link #execute(ExceptionRunnable)} and ignores the return value.
     *
     * @param resultSupplier the result supplier (must not be {@code null})
     */
    default void execute(@NotNull ExceptionSupplier<?, Exception> resultSupplier) {
        execute((ExceptionRunnable<Exception>) resultSupplier::get);
    }

    /**
     * Execute the session open request.  Use {@link #convertToStateful(SessionID)} to establish the new EJB session.
     *
     * @param resultRunnable the result runnable which executes any session create method (must not be {@code null})
     */
    void execute(@NotNull ExceptionRunnable<Exception> resultRunnable);
}
