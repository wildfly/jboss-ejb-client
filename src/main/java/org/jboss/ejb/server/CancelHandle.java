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

/**
 * A handle which may be used to request the cancellation an invocation request.  A cancel request may or may not be
 * honored by the server implementation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@FunctionalInterface
public interface CancelHandle {

    /**
     * Attempt to cancel the in-progress invocation.  If the invocation is past the point where cancellation is
     * possible, the method has no effect.  The invocation may not support cancellation, in which case the method
     * has no effect.
     *
     * @param aggressiveCancelRequested {@code false} to only cancel if the method invocation has not yet begun, {@code true} to
     * attempt to cancel even if the method is running
     */
    void cancel(boolean aggressiveCancelRequested);

    /**
     * A null cancel handle which does nothing.
     */
    CancelHandle NULL = ignored -> {};
}
