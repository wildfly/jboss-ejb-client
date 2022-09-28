/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.protocol.remote;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jboss.ejb._private.NetworkUtil;
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
