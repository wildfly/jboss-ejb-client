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

package org.jboss.ejb.client.test.common;

import org.jboss.ejb.client.ModuleID;
import org.jboss.ejb.client.protocol.MessageType;
import org.jboss.ejb.client.protocol.PackedInteger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jpai
 */
public class DummyVersionZeroServerProtocolHandler {

    private static final byte HEADER_SESSION_OPEN_REQUEST = 0x01;
    private static final byte HEADER_SESSION_OPEN_RESPONSE = 0x02;
    private static final byte HEADER_INVOCATION_REQUEST = 0x03;
    private static final byte HEADER_INVOCATION_CANCEL_REQUEST = 0x04;
    private static final byte HEADER_INVOCATION_RESPONSE = 0x05;
    private static final byte HEADER_INVOCATION_FAILURE = 0x06;
    private static final byte HEADER_MODULE_AVAILABLE = 0x08;
    private static final byte HEADER_MODULE_UNAVAILABLE = 0x09;

    private static final Map<Byte, MessageType> knownMessageTypes = new HashMap<Byte, MessageType>();

    static {
        knownMessageTypes.put(HEADER_SESSION_OPEN_REQUEST, MessageType.SESSION_OPEN_REQUEST);
        knownMessageTypes.put(HEADER_SESSION_OPEN_RESPONSE, MessageType.SESSION_OPEN_RESPONSE);
        knownMessageTypes.put(HEADER_INVOCATION_REQUEST, MessageType.INVOCATION_REQUEST);
        knownMessageTypes.put(HEADER_INVOCATION_CANCEL_REQUEST, MessageType.INVOCATION_CANCEL_REQUEST);
        knownMessageTypes.put(HEADER_INVOCATION_RESPONSE, MessageType.INVOCATION_RESPONSE);
        knownMessageTypes.put(HEADER_MODULE_AVAILABLE, MessageType.MODULE_AVAILABLE);
        knownMessageTypes.put(HEADER_MODULE_UNAVAILABLE, MessageType.MODULE_UNAVAILABLE);
    }

    private final String marshallerType;

    public DummyVersionZeroServerProtocolHandler(final String marshallerType) {
        this.marshallerType = marshallerType;
    }

    public MessageType getMessageType(DataInput input) throws IOException {
        final byte header = input.readByte();
        final MessageType messageType = knownMessageTypes.get(header);
        if (messageType != null) {
            return messageType;
        }
        throw new IOException("Unidentified message header 0x" + Integer.toHexString(header));
    }


    public void writeModuleAvailability(final DataOutput output, final ModuleID[] ejbModuleIdentifiers) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("Cannot write to null output");
        }
        if (ejbModuleIdentifiers == null) {
            throw new IllegalArgumentException("EJB module identifiers cannot be null");
        }
        // write the header
        output.write(HEADER_MODULE_AVAILABLE);
        // write the count
        PackedInteger.writePackedInteger(output, ejbModuleIdentifiers.length);
        // write the app/module names
        for (int i = 0; i < ejbModuleIdentifiers.length; i++) {
            // write the app name
            final String appName = ejbModuleIdentifiers[i].getAppName();
            if (appName == null) {
                // write out a empty string
                output.writeUTF("");
            } else {
                output.writeUTF(appName);
            }
            // write the module name
            output.writeUTF(ejbModuleIdentifiers[i].getModuleName());
            // write the distinct name
            final String distinctName = ejbModuleIdentifiers[i].getDistinctName();
            if (distinctName == null) {
                // write out an empty string
                output.writeUTF("");
            } else {
                output.writeUTF(distinctName);
            }
        }
    }

}
