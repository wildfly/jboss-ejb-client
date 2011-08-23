/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client.protocol;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class PackedInteger {
    static int readPackedInteger(final DataInput in) throws IOException {
        int b = in.readByte();
        if ((b & 0x80) == 0x80) {
            return readPackedInteger(in) << 7 | (b & 0x7F);
        }
        return b;
    }

    static void writePackedInteger(final DataOutput out, int value) throws IOException {
        if (value < 0)
            throw new IllegalArgumentException("Only unsigned integer can be packed");
        if (value > 127) {
            out.writeByte(value & 0x7F | 0x80);
            writePackedInteger(out, value >> 7);
        } else {
            out.writeByte(value & 0xFF);
        }
    }
}
