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
 * Responsible for writing out a session open request message, as per the EJB remoting client protocol specification to a stream.
 * <p/>
 * User: Jaikiran Pai
 */
class SessionOpenRequestWriter extends AbstractMessageWriter {

    private static final byte HEADER_SESSION_OPEN_REQUEST = 0x01;

    private final byte protocolVersion;

    SessionOpenRequestWriter(final byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    /**
     * Writes out a session open request message, corresponding to the EJB identified by the passed parameters, to the passed <code>output</code>.
     *
     * @param output       The {@link DataOutput} to which the message will be written
     * @param invocationId The invocation id
     * @param appName      The application name of the EJB for which the session is to be opened. Can be null
     * @param moduleName   The module name of the EJB. Cannot be null.
     * @param distinctName The distinct name of the EJB. Can be null.
     * @param beanName     The EJB name. Cannot be null.
     * @throws IOException If there's a problem while writing to the {@link DataOutput}
     */
    void writeMessage(final DataOutput output, final short invocationId, final String appName, final String moduleName, final String distinctName, final String beanName) throws IOException {
        // write the header
        output.writeByte(HEADER_SESSION_OPEN_REQUEST);
        // write the invocation id
        output.writeShort(invocationId);
        // ejb identifier
        if (appName == null) {
            output.writeUTF("");
        } else {
            output.writeUTF(appName);
        }
        output.writeUTF(moduleName);
        if (distinctName == null) {
            output.writeUTF("");
        } else {
            output.writeUTF(distinctName);
        }
        output.writeUTF(beanName);
    }

}
