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

package org.jboss.ejb.client.protocol;

/**
 * TODO: This factory needs to be implemented correctly
 * User: jpai
 */
public class ProtocolHandlerFactory {

    public static ClientProtocolHandler getProtocolHandler(byte protocolVersion, String marshallingStrategy) {
        switch (protocolVersion) {
            case 0x00 :
                return new VersionZeroProtocolHandler(marshallingStrategy);
            default:
                throw new RuntimeException("Unsupported protocol version 0x" + Integer.toHexString(protocolVersion));
        }
    }
}
