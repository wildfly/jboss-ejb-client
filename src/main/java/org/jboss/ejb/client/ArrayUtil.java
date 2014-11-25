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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ArrayUtil {

    private ArrayUtil() {}

    static String bytesToString(byte[] bytes, int offs, int len) {
        StringBuilder b = new StringBuilder(len * 2);
        int h, l;
        for (int i = 0; i < len; i ++) {
            h = bytes[i + offs] & 0xff;
            l = h & 0x0f;
            h >>= 4;
            if (h < 10) {
                b.append('0' + h);
            } else {
                b.append('A' + h - 10);
            }
            if (l < 10) {
                b.append('0' + l);
            } else {
                b.append('A' + l - 10);
            }
        }
        return b.toString();
    }
}
