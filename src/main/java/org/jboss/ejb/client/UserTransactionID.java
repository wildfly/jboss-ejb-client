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

    private final String nodeName;
    private final int id;

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
        //noinspection NumericOverflow
        this.id = (encodedForm[nodeNameLength + 2] & 0xff) << 24
                | (encodedForm[nodeNameLength + 3] & 0xff) << 16
                | (encodedForm[nodeNameLength + 4] & 0xff) << 8
                | encodedForm[nodeNameLength + 5] & 0xff;
    }

    public UserTransactionID(final String name, final int uniqueId) {
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
        return new IllegalArgumentException("Wrong transaction ID format");
    }

    /**
     * Get the associated node name.
     *
     * @return the name
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Get the unique ID.
     *
     * @return the unique ID
     */
    public int getId() {
        return id;
    }
}
