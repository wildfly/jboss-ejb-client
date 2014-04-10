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

import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

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

    /**
     * Returns a {@link IoFuture} to a {@link Connection} which is established to the destination host.
     * <p/>
     * This method takes care of any necessary formatting of the passed <code>destinationHost</code> in case
     * it's a IPv6 address.
     *
     * @param endpoint                  The {@link Endpoint} that will be used to establish the connection
     * @param protocol                  The protocol to use
     * @param destinationHost           The destination host to connect to. This can either be a host name or a IP address
     * @param destinationPort           The destination port to connect to.
     * @param sourceBindAddress         An optional source bind address to be used while connecting.
     * @param connectionCreationOptions The connection creations options to use while connecting
     * @param callbackHandler           The {@link CallbackHandler} to use for authenticating the connection creation
     * @param sslContext                The SSL context to use for SSL connections. Can be null.
     * @return
     * @throws IOException
     */
    public static IoFuture<Connection> connect(final Endpoint endpoint, final String protocol, final String destinationHost, final int destinationPort,
                                               final InetSocketAddress sourceBindAddress, final OptionMap connectionCreationOptions,
                                               final CallbackHandler callbackHandler, final SSLContext sslContext) throws IOException {

        InetSocketAddress destinationSocketAddress = new InetSocketAddress(formatPossibleIpv6Address(destinationHost), destinationPort);
        return connect(endpoint, protocol, destinationSocketAddress, sourceBindAddress, connectionCreationOptions, callbackHandler, sslContext);
    }

    /**
     * Returns a {@link IoFuture} to a {@link Connection} which is established to the destination host.
     * <p/>
     *
     * @param endpoint                  The {@link Endpoint} that will be used to establish the connection
     * @param protocol                  The protocol to use
     * @param destination               The {@link InetSocketAddress} destination to connect to
     * @param sourceBindAddress         An optional source bind address to be used while connecting.
     * @param connectionCreationOptions The connection creations options to use while connecting
     * @param callbackHandler           The {@link CallbackHandler} to use for authenticating the connection creation
     * @param sslContext                The SSL context to use for SSL connections. Can be null.
     * @return
     * @throws IOException
     */
    public static IoFuture<Connection> connect(final Endpoint endpoint, final String protocol, final InetSocketAddress destination,
                                               final InetSocketAddress sourceBindAddress, final OptionMap connectionCreationOptions,
                                               final CallbackHandler callbackHandler, final SSLContext sslContext) throws IOException {
        return endpoint.connect(protocol, sourceBindAddress, destination, connectionCreationOptions, callbackHandler, sslContext);
    }
}
