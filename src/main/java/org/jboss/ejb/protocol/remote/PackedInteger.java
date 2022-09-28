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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A {@link PackedInteger} is a variable-length integer. The most-significant bit of each byte of a
 * {@link PackedInteger} value indicates whether that byte is the final (lowest-order) byte of the value.
 * If the bit is 0, then this is the last byte; if the bit is 1, then there is at least one more subsequent
 * byte pending, and the current value should be shifted to the left by 7 bits to accommodate the next byte's data.
 * <p/>
 * Note: {@link PackedInteger} cannot hold signed integer values.
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
class PackedInteger {


    /**
     * Reads a {@link PackedInteger} value from the passed {@link DataInput input} and returns the
     * value of the integer.
     *
     * @param input The {@link DataInput} from which the {@link PackedInteger} value will be read
     * @return
     * @throws IOException
     * @throws IllegalArgumentException If the passed <code>input</code> is null
     */
    public static int readPackedInteger(final DataInput input) throws IOException {
        int b = input.readByte();
        if ((b & 0x80) == 0x80) {
            return readPackedInteger(input) << 7 | (b & 0x7F);
        }
        return b;
    }

    /**
     * Converts the passed <code>value</code> into a {@link PackedInteger} and writes it to the
     * {@link DataOutput output}
     *
     * @param output The {@link DataOutput} to which the {@link PackedInteger} is written to
     * @param value  The integer value which will be converted to a {@link PackedInteger}
     * @throws IOException
     * @throws IllegalArgumentException If the passed <code>value</code> is < 0. {@link PackedInteger} doesn't
     *                                  allow signed integer
     * @throws IllegalArgumentException If the passed <code>output</code> is null
     */
    public static void writePackedInteger(final DataOutput output, int value) throws IOException {
        if (value < 0)
            throw new IllegalArgumentException("Only unsigned integer can be packed");
        if (value > 127) {
            output.writeByte(value & 0x7F | 0x80);
            writePackedInteger(output, value >> 7);
        } else {
            output.writeByte(value & 0xFF);
        }
    }

}
