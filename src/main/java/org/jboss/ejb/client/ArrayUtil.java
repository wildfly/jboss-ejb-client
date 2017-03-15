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
