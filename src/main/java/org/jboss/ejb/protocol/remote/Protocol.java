/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class Protocol {

    public static final int LATEST_VERSION = 3;

    static final byte[] RIVER_BYTES = new byte[] {
        0, 5, (byte) 'r', (byte) 'i', (byte) 'v', (byte) 'e', (byte) 'r'
    };

    public static final int NEW_LOCATOR    = 0x00;
    public static final int CACHED_LOCATOR = 0x01;

    // flags field (v3 and up)
    public static final int COMPRESS_RESPONSE = 0b0000_1111;

    public static final int OPEN_SESSION_REQUEST   = 0x01; // c → s
    public static final int OPEN_SESSION_RESPONSE  = 0x02; // s → c
    public static final int INVOCATION_REQUEST     = 0x03; // c → s
    public static final int CANCEL_REQUEST         = 0x04; // c → s
    public static final int INVOCATION_RESPONSE    = 0x05; // s → c
    public static final int APPLICATION_EXCEPTION  = 0x06; // s → c
    // unused                                      = 0x07;
    public static final int MODULE_AVAILABLE       = 0x08; // s → c
    public static final int MODULE_UNAVAILABLE     = 0x09; // s → c
    public static final int NO_SUCH_EJB            = 0x0A; // s → c
    public static final int NO_SUCH_METHOD         = 0x0B; // s → c
    public static final int SESSION_NOT_ACTIVE     = 0x0C; // s → c
    public static final int EJB_NOT_STATEFUL       = 0x0D; // s → c
    public static final int PROCEED_ASYNC_RESPONSE = 0x0E; // s → c

    // TXN compat
    public static final int TXN_COMMIT_REQUEST            = 0x0F; // c → s
    public static final int TXN_ROLLBACK_REQUEST          = 0x10; // c → s
    public static final int TXN_PREPARE_REQUEST           = 0x11; // c → s
    public static final int TXN_FORGET_REQUEST            = 0x12; // c → s
    public static final int TXN_BEFORE_COMPLETION_REQUEST = 0x13; // c → s
    public static final int TXN_RESPONSE                  = 0x14; // s → c

    public static final int CLUSTER_TOPOLOGY_COMPLETE     = 0x15; // s → c
    public static final int CLUSTER_TOPOLOGY_REMOVAL      = 0x16; // s → c
    public static final int CLUSTER_TOPOLOGY_ADDITION     = 0x17; // s → c
    public static final int CLUSTER_TOPOLOGY_NODE_REMOVAL = 0x18; // s → c

    // unused                                     = 0x19

    public static final int TXN_RECOVERY_RESPONSE = 0x1A; // s → c

    public static final int COMPRESSED_INVOCATION_MESSAGE = 0x1B; // s → c & c → s

    private Protocol() {
    }
}
