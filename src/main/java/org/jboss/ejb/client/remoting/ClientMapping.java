/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.net.InetAddress;

/**
 * A client mapping specifies how external clients should connect to a
 * socket's port, provided that the client's outbound interface
 * match the specified source network value.
 *
 * @author Jason T. Greene
 */
class ClientMapping {
    private final InetAddress sourceNetworkAddress;
    private final byte sourceNetworkMaskBits;
    private final String destinationAddress;
    private final int destinationPort;
    private final String destinationProtocol;

    private final String cachedToString;


    /**
     * Construct a new client mapping.
     *
     * @param sourceNetworkAddress  The IP of the source network to match the outbound interface against
     * @param sourceNetworkMaskBits The masked portion of the source network to match the outbound interface against
     * @param destinationAddress    The destination host/ip the client should connect to.
     * @param destinationPort       The destination port the client should connect to.  A value of -1 indicates that
     * @param destinationProtocol
     */
    ClientMapping(InetAddress sourceNetworkAddress, int sourceNetworkMaskBits, String destinationAddress, int destinationPort, final String destinationProtocol) {
        this.sourceNetworkAddress = sourceNetworkAddress;
        this.destinationProtocol = destinationProtocol;
        this.sourceNetworkMaskBits = (byte) sourceNetworkMaskBits;
        this.destinationAddress = destinationAddress;
        this.destinationPort = destinationPort;

        this.cachedToString = this.generateToString();
    }


    /**
     * Source network the client connection binds on. A client should match this value with the mask returned by
     * {@link #getSourceNetworkMaskBits()} against the desired client host network interface, and if matched the
     * client should connect to the corresponding destination values..
     *
     * @return The IP to match with
     */
    InetAddress getSourceNetworkAddress() {
        return sourceNetworkAddress;
    }

    /**
     * Source network the client connection binds on. A client should match this value with the ip returned by
     * {@link #getSourceNetworkAddress()} against the desiered client host network interface,  and if matched the
     * client should connect to the corresponding destination values.
     *
     * @return the number of mask bits starting from the LSB
     */
    int getSourceNetworkMaskBits() {
        return sourceNetworkMaskBits & 0xFF;
    }


    /**
     * The destination host or IP that the client should connect to. Note this value only has meaning to the client,
     * which may be on a completely different network topology, with a client specific DNS, so the server SHOULD NOT
     * attempt to resolve this value.
     *
     * @return the host/ip to connect to should this mapping match
     */
    String getDestinationAddress() {
        return destinationAddress;
    }

    /**
     * The destination port that the client should connect to. If the vale is -1 the effect server side port, which
     * includes accounting for offsets should be passed to the client.
     *
     * @return the port to connect to, or -1 if the server side port should be used.
     */
    int getDestinationPort() {
        return destinationPort;
    }

    /**
     * The protocol that the client should use to connect.
     * @return
     */
    String getDestinationProtocol() {
        return destinationProtocol;
    }

    @Override
    public String toString() {
        return this.cachedToString;
    }

    private String generateToString() {
        return "ClientMapping{" +
                "sourceNetworkAddress=" + sourceNetworkAddress +
                ", sourceNetworkMaskBits=" + sourceNetworkMaskBits +
                ", destinationAddress='" + destinationAddress + '\'' +
                ", destinationPort=" + destinationPort +
                '}';

    }
}
