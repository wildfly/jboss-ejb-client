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

package org.jboss.ejb.protocol.remote;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * @author Jaikiran Pai
 */
class NetworkUtil {

    private static int getInt(byte[] b, int offs) {
        return (b[offs] & 0xff) << 24 | (b[offs + 1] & 0xff) << 16 | (b[offs + 2] & 0xff) << 8 | b[offs + 3] & 0xff;
    }

    private static long getLong(byte[] b, int offs) {
        return (getInt(b, offs) & 0xFFFFFFFFL) << 32 | getInt(b, offs + 4) & 0xFFFFFFFFL;
    }

    private static int nwsl(int arg, int places) {
        return places <= 0 ? arg : places >= 32 ? 0 : arg << places;
    }

    private static long nwsl(long arg, int places) {
        return places <= 0 ? arg : places >= 64 ? 0L : arg << places;
    }

    /**
     * Returns true if the passed <code>address</code> is part of the network represented by the passed <code>networkAddress</code>
     * and <code>networkMask</code>. Else returns false
     *
     * @param address        The address being checked
     * @param networkAddress The network address
     * @param networkMask    The network mask bits
     * @return
     */
    public static boolean belongsToNetwork(final InetAddress address, final InetAddress networkAddress, final int networkMask) {
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
        if (address instanceof Inet4Address && networkAddress instanceof Inet4Address) {
            final int maskBits = nwsl(0xFFFFFFFF, 32 - networkMask);
            final int addr = getInt(addressBytes, 0) & maskBits;
            final int netAddr = getInt(networkAddressBytes, 0) & maskBits;
            return addr == netAddr;
        } else if (address instanceof Inet6Address && networkAddress instanceof Inet6Address) {
            final long maskHigh = nwsl(0xFFFFFFFFFFFFFFFFL, 64 - networkMask);
            final long maskLow = nwsl(0xFFFFFFFFFFFFFFFFL, 128 - networkMask);
            final long addrHigh = getLong(addressBytes, 0) & maskHigh;
            final long addrLow = getLong(addressBytes, 8) & maskLow;
            final long netAddrHigh = getLong(networkAddressBytes, 0) & maskHigh;
            final long netAddrLow = getLong(networkAddressBytes, 8) & maskLow;
            return addrHigh == netAddrHigh && addrLow == netAddrLow;
        } else {
            return false;
        }
    }

    static String formatPossibleIpv6Address(String address) {
        if (address == null) {
            return address;
        }
        if (!address.contains(":")) {
            return address;
        }
        if (address.startsWith("[") && address.endsWith("]")) {
            return address;
        }
        return "[" + address + "]";
    }
}
