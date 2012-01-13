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
 * @author Jaikiran Pai
 */
class NetworkUtil {

    /**
     * Returns true if the passed <code>address</code> is part of the network represented by the passed <code>networkAddress</code>
     * and <code>networkMask</code>. Else returns false
     *
     * @param address        The address being checked
     * @param networkAddress The network address
     * @param networkMask    The network mask bits
     * @return
     */
    static boolean belongsToNetwork(final InetAddress address, final InetAddress networkAddress, final byte networkMask) {
        if (address == null || networkAddress == null) {
            return false;
        }
        // a netmask of 0 means, it matches everything
        if (networkMask == 0) {
            return true;
        }
        // convert to bytes
        final byte[] addressBytes = address.getAddress();
        final byte[] networkAddressBytes = networkAddress.getAddress();
        // we can't match a IPv4 (4 byte) address with a IPv6 (16 byte) address
        if (addressBytes.length != networkAddressBytes.length) {
            return false;
        }
        // start processing each of those bytes
        int currentByte = 0;
        byte networkAddressByte = networkAddressBytes[currentByte];
        byte otherAddressByte = addressBytes[currentByte];
        // apply the masking
        for (int i = 0; i < networkMask; i++) {
            // The address bit and the network address bit, don't match, so the address doesn't belong to the
            // network
            if ((networkAddressByte & 128) != (otherAddressByte & 128)) {
                return false;
            }
            // switch to next byte if we have processed all bits of the current byte
            if ((i + 1) % 8 == 0) {
                // all bytes have been processed and they all matched
                if (currentByte == networkAddressBytes.length - 1) {
                    return true;
                }
                // move to next byte
                ++currentByte;
                networkAddressByte = networkAddressBytes[currentByte];
                otherAddressByte = addressBytes[currentByte];
            } else {
                // move to the next (lower order) bit in the byte
                networkAddressByte = (byte) (networkAddressByte << 1);
                otherAddressByte = (byte) (otherAddressByte << 1);
            }
        }
        return true;
    }

//    public static void main(String[] args) throws UnknownHostException {
//        final InetAddress ipAddress = InetAddress.getByName("10.10.10.15");
//        final InetAddress networkAddress = InetAddress.getByName("10.10.22.0");
//        byte netmask = 16;
//        System.out.println(ipAddress + " belongs to " + networkAddress + "/" + netmask + " -> " + belongsToNetwork(ipAddress, networkAddress, netmask));
//    }
}
