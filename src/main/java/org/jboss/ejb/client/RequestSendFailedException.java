/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
 * An exception (typically) thrown by {@link EJBReceiver}s if the receiver couldn't successfully handle a request.
 *
 * @author: Jaikiran Pai
 */
public class RequestSendFailedException extends RuntimeException {

    private static final long serialVersionUID = 4880994720537464175L;

    /**
     * The node name of the EJB receiver which failed to handle the request
     */
    private final String failedNodeName;

    /**
     * @param failedNodeName The node name of the EJB receiver which failed to handle the request
     * @param failureMessage The exception message
     * @param cause          The exception which caused this failure
     */
    public RequestSendFailedException(final String failedNodeName, final String failureMessage, final Throwable cause) {
        super(failureMessage, cause);
        this.failedNodeName = failedNodeName;
    }

    /**
     * Returns the node name of the EJB receiver which failed to handle the request
     *
     * @return
     */
    String getFailedNodeName() {
        return this.failedNodeName;
    }
}
