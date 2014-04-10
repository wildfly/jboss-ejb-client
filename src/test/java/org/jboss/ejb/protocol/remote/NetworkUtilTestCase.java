/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NetworkUtilTestCase  {

    @Test
    public void testNetmask1() throws UnknownHostException {
        assertTrue(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 0, 0, 1 }), InetAddress.getByAddress("", new byte[] { 10, 64, 33, 17 }), 8));
        assertTrue(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 7, 0, (byte) 131 }), InetAddress.getByAddress("", new byte[] { 10, 64, 33, 17 }), 8));
        assertFalse(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 7, 0, (byte) 131 }), InetAddress.getByAddress("", new byte[] { 10, 64, 33, 17 }), 16));
        assertFalse(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 7, 0, (byte) 131 }), InetAddress.getByAddress("", new byte[] { 10, 64, 33, 17 }), 24));

        assertTrue(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 0, 0, 1 }), InetAddress.getByAddress("", new byte[] { 127, (byte) 192, 33, 17 }), 0));
        assertTrue(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 0, 0, 1 }), InetAddress.getByAddress("", new byte[] { 10, 0, 0, 1 }), 32));
        assertFalse(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 0, 0, 1 }), InetAddress.getByAddress("", new byte[] { 10, 0, 0, 2 }), 32));
        assertFalse(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 0, 0, 1 }), InetAddress.getByAddress("", new byte[] { (byte) 138, 0, 0, 1 }), 32));
        assertFalse(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { 10, 0, 0, 2 }), InetAddress.getByAddress("", new byte[] { 10, 0, 0, 1 }), 32));
        assertFalse(NetworkUtil.belongsToNetwork(InetAddress.getByAddress("", new byte[] { (byte) 138, 0, 0, 1 }), InetAddress.getByAddress("", new byte[] { 10, 0, 0, 1 }), 32));
    }
}
