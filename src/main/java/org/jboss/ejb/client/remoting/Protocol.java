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

package org.jboss.ejb.client.remoting;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class Protocol {

    // version 1
    static final byte HEADER_SESSION_OPEN_REQUEST_MESSAGE = 0x01;
    static final byte HEADER_SESSION_OPEN_RESPONSE_MESSAGE = 0x02;
    static final byte HEADER_INVOCATION_REQUEST_MESSAGE = 0x03;
    static final byte HEADER_INVOCATION_CANCEL_MESSAGE = 0x04;
    static final byte HEADER_INVOCATION_RESPONSE_MESSAGE = 0x05;
    static final byte HEADER_INVOCATION_EXCEPTION_MESSAGE = 0x06;
    // 0x07
    static final byte HEADER_MODULE_AVAILABILITY1_MESSAGE = 0x08;
    static final byte HEADER_MODULE_AVAILABILITY2_MESSAGE = 0x09;
    static final byte HEADER_NO_SUCH_EJB_MESSAGE = 0x0A;
    static final byte HEADER_INVOCATION_GENERAL_FAILURE1_MESSAGE = 0x0B;
    static final byte HEADER_INVOCATION_GENERAL_FAILURE2_MESSAGE = 0x0C;
    static final byte HEADER_NON_STATEFUL_OPEN_MESSAGE = 0x0D;
    static final byte HEADER_ASYNC_METHOD_MESSAGE = 0x0E;
    static final byte HEADER_TX_COMMIT_MESSAGE = 0x0F;
    static final byte HEADER_TX_ROLLBACK_MESSAGE = 0x10;
    static final byte HEADER_TX_PREPARE_MESSAGE = 0x11;
    static final byte HEADER_TX_FORGET_MESSAGE = 0x12;
    static final byte HEADER_TX_BEFORE_COMPLETION_MESSAGE = 0x13;
    static final byte HEADER_TX_INVOCATION_RESPONSE_MESSAGE = 0x14;
    static final byte HEADER_CLUSTER_COMPLETE_MESSAGE = 0x15;
    static final byte HEADER_CLUSTER_REMOVAL_MESSAGE = 0x16;
    static final byte HEADER_CLUSTER_NODE_ADD_MESSAGE = 0x17;
    static final byte HEADER_CLUSTER_NODE_REMOVE_MESSAGE = 0x18;

    // version 2
    static final byte HEADER_TX_RECOVER_REQUEST_MESSAGE = 0x19;
    static final byte HEADER_TX_RECOVER_RESPONSE_MESSAGE = 0x1A;
    static final byte HEADER_COMPRESSED_INVOCATION_DATA_MESSAGE = 0x1B;

    static final char METHOD_PARAM_TYPE_SEPARATOR = ',';
}
