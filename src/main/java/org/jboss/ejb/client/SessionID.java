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
 * A session ID for a stateful EJB.  Session IDs can be stored in multiple formats with different
 * characteristics.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class SessionID implements Serializable {

    private static final long serialVersionUID = 3872192729805797520L;

    // The transient flags aren't really respected since we use object replacement.

    private final byte[] encodedForm;
    private final String toString;
    private final transient int hashCode;

    /**
     * Construct a new instance.  The given array is used as-is, so if it comes from an untrusted source,
     * it should first be cloned.
     *
     * @param encodedForm the encoded form of this session ID
     */
    SessionID(final byte[] encodedForm) {
        this.encodedForm = encodedForm;
        hashCode = Arrays.hashCode(encodedForm);
	toString = "{" + Arrays.toString(encodedForm) + '}';
    }

    /**
     * Get a copy of the encoded form of this session ID.
     *
     * @return the copy of the encoded form
     */
    public byte[] getEncodedForm() {
        return encodedForm.clone();
    }

    /**
     * Get the encoded form of this session ID.  Note that callers must take care to avoid
     * modifying the encoded form.
     *
     * @return the encoded form
     */
    protected byte[] getEncodedFormRaw() {
        return encodedForm;
    }

    /**
     * Determine whether this object is equal to another.  Session IDs are equal if their encoded form is
     * equal and the class is equal.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public final boolean equals(Object other) {
        return this == other || other != null && other.getClass() == getClass() && equals((SessionID) other);
    }

    private boolean equals(SessionID other) {
        return Arrays.equals(encodedForm, other.encodedForm);
    }

    public final int hashCode() {
        return hashCode;
    }

    /**
     * Create a session ID object for the given encoded representation.
     *
     * @param encoded the encoded representation
     * @return the session ID object
     */
    public static SessionID createSessionID(byte[] encoded) {
        final int length = encoded.length;
        if (length >= 19 && encoded[0] == 0x07) {
            return new BasicSessionID(encoded.clone());
        }
        return new UnknownSessionID(encoded.clone());
    }

    /**
     * Substitute this session ID with a serialized representation.
     *
     * @return the serialized representation
     */
    protected final Object writeReplace() {
        return new Serialized(encodedForm);
    }

    /**
     * Serialized representation of a session ID..
     */
    public static final class Serialized implements Serializable {

        private static final long serialVersionUID = -6014782612354158572L;

        /**
         * The bytes of the session ID.
         *
         * @serial
         */
        private final byte[] id;

        Serialized(final byte[] id) {
            this.id = id;
        }

        /**
         * Reconstitute the session ID from this serialized representation.
         *
         * @return the session ID object
         */
        protected Object readResolve() {
            return createSessionID(id);
        }
    }

    @Override
    public String toString() {
        return toString; 
    }
}
