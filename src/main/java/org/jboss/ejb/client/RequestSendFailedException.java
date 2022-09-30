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

package org.jboss.ejb.client;

import jakarta.ejb.EJBException;

/**
 * An exception (typically) thrown by {@link EJBReceiver}s if the receiver couldn't successfully handle a request.  If
 * this exception is received, the outcome of the request is unknown (and possible retries have also failed indeterminately).
 *
 * @author Jaikiran Pai
 */
public class RequestSendFailedException extends EJBException {

    private static final long serialVersionUID = 4880994720537464175L;

    private boolean canBeRetried;

    /**
     * Constructs a new {@code RequestSendFailedException} instance.  The message is left blank ({@code null}), and no
     * cause is specified.
     */
    public RequestSendFailedException() {
    }

    /**
     * Constructs a new {@code RequestSendFailedException} instance with an initial message.  No cause is specified.
     *
     * @param msg the message
     */
    public RequestSendFailedException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a new {@code RequestSendFailedException} instance with an initial cause.  If a non-{@code null} cause
     * is specified, its message is used to initialize the message of this {@code RequestSendFailedException}; otherwise
     * the message is left blank ({@code null}).
     *
     * @param cause the cause
     */
    public RequestSendFailedException(final Throwable cause) {
        super();
        initCause(cause);
    }

    /**
     * Constructs a new {@code RequestSendFailedException} instance with an initial message and cause.
     *
     * @param msg the message
     * @param cause the cause
     */
    public RequestSendFailedException(final String msg, final Throwable cause) {
        super(msg);
        initCause(cause);
    }

    /**
     * Constructs a new {@code RequestSendFailedException} instance.  The message is left blank ({@code null}), and no
     * cause is specified.
     *
     * @param canBeRetried the value of the can-be-retried flag
     */
    public RequestSendFailedException(final boolean canBeRetried) {
        this.canBeRetried = canBeRetried;
    }

    /**
     * Constructs a new {@code RequestSendFailedException} instance with an initial message.  No cause is specified.
     *
     * @param message the message
     * @param canBeRetried the value of the can-be-retried flag
     */
    public RequestSendFailedException(final String message, final boolean canBeRetried) {
        super(message);
        this.canBeRetried = canBeRetried;
    }

    /**
     * Constructs a new {@code RequestSendFailedException} instance with an initial message and cause.
     *
     * @param message the message
     * @param cause the cause
     * @param canBeRetried the value of the can-be-retried flag
     */
    public RequestSendFailedException(final String message, final Throwable cause, final boolean canBeRetried) {
        super(message);
        initCause(cause);
        this.canBeRetried = canBeRetried;
    }

    /**
     * Constructs a new {@code RequestSendFailedException} instance with an initial cause.  If a non-{@code null} cause
     * is specified, its message is used to initialize the message of this {@code RequestSendFailedException}; otherwise
     * the message is left blank ({@code null}).
     *
     * @param cause the cause
     * @param canBeRetried the value of the can-be-retried flag
     */
    public RequestSendFailedException(final Throwable cause, final boolean canBeRetried) {
        super();
        initCause(cause);
        this.canBeRetried = canBeRetried;
    }

    /**
     * Determine if this request can safely be retried.
     *
     * @return {@code true} if the request can safely be retried; {@code false} otherwise
     */
    public boolean canBeRetried() {
        return canBeRetried;
    }

    /**
     * Set the "can be retried" flag.
     *
     * @param canBeRetried the flag value
     */
    public RequestSendFailedException setCanBeRetried(final boolean canBeRetried) {
        this.canBeRetried = canBeRetried;
        return this;
    }
}
