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

import java.util.Arrays;

import javax.transaction.xa.Xid;

/**
 * A transaction ID for an XID, used to propagate transactions from a transaction controller running on this or
 * a calling node.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class XidTransactionID extends TransactionID {

    private static final long serialVersionUID = -1895745528459825578L;

    private final int formatId;
    private final byte gtidLen;
    private final Xid xid = new XidImpl();

    XidTransactionID(final byte[] encodedBytes) {
        super(encodedBytes);
        // Format ID, big-endian
        formatId = (encodedBytes[1] & 0xff) << 24 | (encodedBytes[2] & 0xff) << 16 | (encodedBytes[3] & 0xff) << 8 | (encodedBytes[4] & 0xff);
        if ((gtidLen = encodedBytes[5]) > Xid.MAXGTRIDSIZE) {
            throw new IllegalArgumentException("Invalid global transaction ID length");
        }
        final byte bqalLen = encodedBytes[6 + gtidLen];
        if (bqalLen > Xid.MAXBQUALSIZE || bqalLen != encodedBytes.length - gtidLen - 7) {
            throw new IllegalArgumentException("Invalid branch qualifier length");
        }
    }

    XidTransactionID(final Xid original) {
        this(encode(original));
    }

    private static byte[] encode(final Xid original) {
        final byte[] gtid = original.getGlobalTransactionId();
        final byte[] bqal = original.getBranchQualifier();
        final int formatId = original.getFormatId();
        final byte[] target = new byte[gtid.length + bqal.length + 7];
        target[0] = 0x02;
        target[1] = (byte) (formatId >>> 24);
        target[2] = (byte) (formatId >>> 16);
        target[3] = (byte) (formatId >>> 8);
        target[4] = (byte) (formatId);
        target[5] = (byte) gtid.length;
        System.arraycopy(gtid, 0, target, 6, gtid.length);
        target[6 + gtid.length] = (byte) bqal.length;
        System.arraycopy(bqal, 0, target, 7 + gtid.length, bqal.length);
        return target;
    }

    /**
     * Get the corresponding XID for this transaction.
     *
     * @return the XID
     */
    public Xid getXid() {
        return xid;
    }

    /**
     * Determine whether the given Xid is the same as this Xid.
     *
     * @param xid the xid to test
     * @return {@code true} if it is the same Xid
     */
    public boolean isSameXid(Xid xid) {
        return this.xid.equals(xid);
    }

    final class XidImpl implements Xid {

        public int getFormatId() {
            return formatId;
        }

        public byte[] getGlobalTransactionId() {
            final byte[] target = new byte[gtidLen];
            System.arraycopy(getEncodedFormRaw(), 6, target, 0, gtidLen);
            return target;
        }

        public byte[] getBranchQualifier() {
            final byte[] raw = getEncodedFormRaw();
            final byte[] target = new byte[raw.length - gtidLen - 6];
            System.arraycopy(raw, 6, target, 0, gtidLen);
            return target;
        }

        public boolean equals(Object other) {
            return other instanceof XidImpl && equals((XidImpl) other) || other instanceof Xid && equals((Xid) other);
        }

        private boolean equals(XidImpl other) {
            return this == other || other != null && XidTransactionID.this.equals(other.getXidTransactionID());
        }

        private boolean equals(Xid other) {
            return other != null && Arrays.equals(encode(other), getEncodedFormRaw());
        }

        public int hashCode() {
            return XidTransactionID.this.hashCode();
        }

        XidTransactionID getXidTransactionID() {
            return XidTransactionID.this;
        }
    }
}
