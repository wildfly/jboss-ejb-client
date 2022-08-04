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

package org.jboss.ejb.protocol.remote;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class Protocol {

    public static final int LATEST_VERSION = 3;

    // flags field (v3 and up)
    public static final int COMPRESS_RESPONSE = 0b0000_1111;

    public static final int OPEN_SESSION_REQUEST   = 0x01; // c → s
    public static final int OPEN_SESSION_RESPONSE  = 0x02; // s → c
    public static final int INVOCATION_REQUEST     = 0x03; // c → s
    public static final int CANCEL_REQUEST         = 0x04; // c → s
    public static final int INVOCATION_RESPONSE    = 0x05; // s → c
    public static final int APPLICATION_EXCEPTION  = 0x06; // s → c
    public static final int CANCEL_RESPONSE        = 0x07; // s → c
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

    public static final int TXN_RECOVERY_REQUEST  = 0x19; // c → s
    public static final int TXN_RECOVERY_RESPONSE = 0x1A; // s → c

    public static final int COMPRESSED_INVOCATION_MESSAGE = 0x1B; // s → c & c → s

    // v3 and up
    public static final int BAD_VIEW_TYPE         = 0x1C; // s → c

    static final int UPDATE_BIT_STRONG_AFFINITY = 0b100;
    static final int UPDATE_BIT_WEAK_AFFINITY   = 0b010;
    static final int UPDATE_BIT_SESSION_ID      = 0b001;

    private Protocol() {
    }
}
