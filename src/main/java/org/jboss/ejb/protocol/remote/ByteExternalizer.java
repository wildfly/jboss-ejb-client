/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.protocol.remote;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jboss.marshalling.Creator;

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

    public Object createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
        return object;
    }

    int getByte() {
        return b;
    }

    Object getObject() {
        return object;
    }
}
