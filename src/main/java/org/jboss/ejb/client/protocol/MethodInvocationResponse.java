/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client.protocol;

/**
 * User: jpai
 */
public class MethodInvocationResponse {

    private final Object result;

    private final Throwable failure;

    private final short invocationId;

    public MethodInvocationResponse(final short invocationId, final Object result, final Throwable throwable) {
        this.invocationId = invocationId;
        this.result = result;
        this.failure = throwable;
    }

    public boolean isException() {
        return this.failure != null;
    }

    public Object getResult() {
        return this.result;
    }

    public short getInvocationId() {
        return this.invocationId;
    }

    public Throwable getException() {
        return this.failure;
    }
}
