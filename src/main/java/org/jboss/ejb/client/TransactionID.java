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
