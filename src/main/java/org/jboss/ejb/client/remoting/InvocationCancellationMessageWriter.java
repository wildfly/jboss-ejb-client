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

package org.jboss.ejb.client.remoting;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Writes out a message to indicate that a prior invocation has to be cancelled
 *
 * @author Jaikiran Pai
 */
class InvocationCancellationMessageWriter extends AbstractMessageWriter {

    private static final byte HEADER_INVOCATION_CANCEL_MESSAGE = 0x04;

    /**
     * Writes out a invocation cancel request message to the passed <code>output</code>
     *
     * @param output       The {@link java.io.DataOutput} to which the message will be written
     * @param invocationId The id corresponding to the invocation which is being cancelled
     * @throws java.io.IOException If there's a problem writing out to the {@link java.io.DataOutput}
     */
    void writeMessage(final DataOutput output, final short invocationId) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Cannot write to null output");
        }
        // write the header
        output.writeByte(HEADER_INVOCATION_CANCEL_MESSAGE);
        // write the invocation id
        output.writeShort(invocationId);
    }
}
