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

package org.jboss.ejb._private;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * @author Jaikiran Pai
 */
public class NetworkUtil {

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

    public static String formatPossibleIpv6Address(String address) {
        if (address == null) {
            return null;
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
