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

import java.io.Serializable;
import java.util.Arrays;

/**
 * A transaction ID for an invocation.  Transaction IDs can be stored in multiple formats with different
 * characteristics.
 *
 * @deprecated Retained only for protocol compatibility.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Deprecated
public abstract class TransactionID implements Serializable {

    private static final long serialVersionUID = 7711835471353644411L;

    // The transient flags aren't really respected since we use object replacement.

    private final byte[] encodedForm;
    private final transient int hashCode;

    public static final String PRIVATE_DATA_KEY = "jboss.transaction.id";

    /**
     * Construct a new instance.  The given array is used as-is, so if it comes from an untrusted source,
     * it should first be cloned.
     *
     * @param encodedForm the encoded form of this transaction ID
     */
    TransactionID(final byte[] encodedForm) {
        this.encodedForm = encodedForm;
        hashCode = Arrays.hashCode(encodedForm);
    }

    /**
     * Get a copy of the encoded form of this transaction ID.
     *
     * @return the copy of the encoded form
     */
    public byte[] getEncodedForm() {
        return encodedForm.clone();
    }

    /**
     * Get the encoded form of this transaction ID.  Note that callers must take care to avoid
     * modifying the encoded form.
     *
     * @return the encoded form
     */
    protected byte[] getEncodedFormRaw() {
        return encodedForm;
    }

    /**
     * Determine whether this object is equal to another.  Transaction IDs are equal if their encoded form is
     * equal and the class is equal.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    @Override
    public final boolean equals(Object other) {
        return other.getClass() == getClass() && equals((TransactionID) other);
    }

    private boolean equals(TransactionID other) {
        return this == other || other != null && Arrays.equals(encodedForm, other.encodedForm);
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", getClass().getSimpleName(), ArrayUtil.bytesToString(encodedForm, 0, encodedForm.length));
    }

    /**
     * Create a transaction ID object for the given encoded representation.
     *
     * @param encoded the encoded representation
     * @return the transaction ID object
     */
    public static TransactionID createTransactionID(byte[] encoded) {
        final int length = encoded.length;
        if (length > 0) switch (encoded[0]) {
            case 1: {
                // local transaction.
                return new UserTransactionID(encoded.clone());
            }
            case 2: {
                // XID.
                return new XidTransactionID(encoded.clone());
            }
            default: {
            }
        }
        throw new IllegalArgumentException("Unrecognized Transaction ID format");
    }

    /**
     * Substitute this transaction ID with a serialized representation.
     *
     * @return the serialized representation
     */
    protected final Object writeReplace() {
        return new Serialized(encodedForm);
    }

    /**
     * Serialized representation of a transaction ID.
     */
    public static final class Serialized implements Serializable {

        private static final long serialVersionUID = -8146407206244018476L;

        /**
         * The bytes of the transaction ID.
         *
         * @serial
         */
        private final byte[] id;

        Serialized(final byte[] id) {
            this.id = id;
        }

        /**
         * Reconstitute the transaction ID from this serialized representation.
         *
         * @return the transaction ID object
         */
        protected Object readResolve() {
            return createTransactionID(id);
        }
    }
}
