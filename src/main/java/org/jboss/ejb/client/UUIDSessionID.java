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

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * A UUID-based session ID object.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class UUIDSessionID extends SessionID {

    private static final long serialVersionUID = -7306257085240447972L;

    private final UUID uuid;

    UUIDSessionID(final byte[] encodedForm) {
        super(encodedForm);
        if (encodedForm[0] != 0x09) {
            throw wrongFormat();
        }
        final ByteBuffer bb = ByteBuffer.wrap(encodedForm);
        bb.get();
        // big-endian
        uuid = new UUID(bb.getLong(), bb.getLong());
    }

    /**
     * Construct a new instance.
     *
     * @param uuid the UUID to use (must not be {@code null})
     */
    public UUIDSessionID(final UUID uuid) {
        super(encode(uuid));
        this.uuid = uuid;
    }

    private static byte[] encode(final UUID uuid) {
        final ByteBuffer bb = ByteBuffer.wrap(new byte[17]);
        bb.put((byte) 0x09);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    /**
     * Get the UUID.
     *
     * @return the UUID (must not be {@code null})
     */
    public UUID getUuid() {
        return uuid;
    }

    private static IllegalArgumentException wrongFormat() {
        return new IllegalArgumentException("Wrong session ID format");
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", getClass().getSimpleName(), uuid);
    }
}
