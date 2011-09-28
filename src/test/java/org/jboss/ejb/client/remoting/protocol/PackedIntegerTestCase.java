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
package org.jboss.ejb.client.remoting.protocol;

import org.jboss.ejb.client.remoting.PackedInteger;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class PackedIntegerTestCase {
    private static void test(final int value, final int bufsize) throws IOException {
        final byte array[] = new byte[bufsize];
        final DataOutput out = new DataOutputStream(new OutputStream() {
            private int count = 0;
            @Override
            public void write(int b) throws IOException {
                if (count >= array.length)
                    throw new EOFException("bufsize " + bufsize + " exceeded");
                assert b >= 0;
                array[count++] = (byte) b;
            }
        });
        PackedInteger.writePackedInteger(out, value);

        final int result = PackedInteger.readPackedInteger(new DataInputStream(new InputStream() {
            private int count = 0;

            @Override
            public int read() throws IOException {
                return array[count++] & 0xFF;
            }
        }));
        assertEquals(value, result);
    }

    @Test
    public void test1() throws IOException {
        test(1, 1);
    }

    @Test
    public void test128() throws IOException {
        try {
            test(128, 1);
            fail("Did not receive a EOFException for value = 128 and number of bytes = 1");
        } catch (EOFException e) {
            // good
        }
        test(128, 2);
    }

    @Test
    public void test255() throws IOException {
        try {
            test(255, 1);
            fail("Did not receive a EOFException for value = 255 and number of bytes = 1");
        } catch (EOFException e) {
            // good
        }
        test(255, 2);
    }

    @Test
    public void test32768() throws IOException {
        try {
            test(32768, 2);
            fail("Did not receive a EOFException for value = 32768 and number of bytes = 2");
        } catch (EOFException e) {
            // good
        }
        test(32768, 3);
    }

    @Test
    public void testZ() throws IOException {
        for (int i = 0; i < 40000; i++) {
            test(i, 3);
        }
    }
}
