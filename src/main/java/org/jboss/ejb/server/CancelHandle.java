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
