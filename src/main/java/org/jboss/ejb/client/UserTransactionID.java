/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client;

import java.io.UnsupportedEncodingException;

/**
 * @deprecated Retained only for protocol compatibility.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Deprecated
public final class UserTransactionID extends TransactionID {

    private static final long serialVersionUID = -791647046784989955L;

    private final transient String nodeName;

    UserTransactionID(final byte[] encodedForm) {
        super(encodedForm);
        // first byte is the header
        if (encodedForm[0] != 0x01) {
            throw wrongFormat();
        }
        final int totalEncodedLength = encodedForm.length;
        // second byte of the encoded form is the length of the nodename bytes
        final int nodeNameLength = encodedForm[1];
        if (nodeNameLength <= 0) {
            throw wrongFormat();
        }
        // next bytes are the nodename
        try {
            nodeName = new String(encodedForm, 2, nodeNameLength, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw wrongFormat();
        }
        if (totalEncodedLength < nodeNameLength + 4) {
            throw wrongFormat();
        }
        // the rest is the unique ID
    }

    UserTransactionID(final String name, final int uniqueId) {
        this(encode(name, uniqueId));
    }

    private static byte[] encode(final String name, final int uniqueId) {
        final byte[] nameBytes;
        try {
            nameBytes = name.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw wrongFormat();
        }
        final int length = nameBytes.length;
        if (length > 255) {
            throw wrongFormat();
        }
        final byte[] target = new byte[6 + length];
        target[0] = 0x01;
        target[1] = (byte) length;
        System.arraycopy(nameBytes, 0, target, 2, length);
        target[2 + length] = (byte) (uniqueId >> 24);
        target[3 + length] = (byte) (uniqueId >> 16);
        target[4 + length] = (byte) (uniqueId >> 8);
        target[5 + length] = (byte) uniqueId;
        return target;
    }

    private static IllegalArgumentException wrongFormat() {
        return new IllegalArgumentException("Wrong session ID format");
    }

    /**
     * Get the associated node name.
     *
     * @return the name
     */
    public String getNodeName() {
        return nodeName;
    }
}
