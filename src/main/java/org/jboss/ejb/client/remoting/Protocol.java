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
    static final byte HEADER_SESSION_OPEN_REQUEST = 0x01;
    static final byte HEADER_METHOD_INVOCATION_MESSAGE = 0x03;
    static final byte HEADER_INVOCATION_CANCEL_MESSAGE = 0x04;
    static final byte HEADER_TX_COMMIT_MESSAGE = 0x0F;
    static final byte HEADER_TX_ROLLBACK_MESSAGE = 0x10;
    static final byte HEADER_TX_PREPARE_MESSAGE = 0x11;
    static final byte HEADER_TX_FORGET_MESSAGE = 0x12;
    static final byte HEADER_TX_BEFORE_COMPLETION_MESSAGE = 0x13;

    // version 2
    static final byte HEADER_TX_RECOVER_MESSAGE = 0x19;

    static final char METHOD_PARAM_TYPE_SEPARATOR = ',';
}
