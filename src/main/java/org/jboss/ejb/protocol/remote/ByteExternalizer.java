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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class ByteExternalizer extends AbstractWritingExternalizer {

    private static final long serialVersionUID = 2582968267716345788L;

    private final int b;
    private final Object object;

    ByteExternalizer(final Object object, final int b) {
        this.b = b & 0xff;
        this.object = object;
    }

    public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
        output.writeByte(b);
    }

    public Object createExternal(final Class<?> subjectType, final ObjectInput input) throws IOException, ClassNotFoundException {
        return object;
    }

    int getByte() {
        return b;
    }

    Object getObject() {
        return object;
    }
}
