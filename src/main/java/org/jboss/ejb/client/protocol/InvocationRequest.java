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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class InvocationRequest implements Externalizable {
    public static final int INVOCATION_REQUEST = 0x01;

    private transient int invocationId;
    private transient String fqBeanName;
    private transient String view;
    private transient String methodName;
    private transient Object[] params;
    private transient Attachment[] attachments;

    @Deprecated
    public InvocationRequest() {
        // TODO: don't want to expose this constructor
    }

    @Deprecated
    public InvocationRequest(final int invocationId, final String fqBeanName, final String view, final String methodName, final Object[] params, final Attachment[] attachments) {
        // TODO: don't want to expose this constructor
        this.invocationId = invocationId;
        this.fqBeanName = fqBeanName;
        this.view = view;
        this.methodName = methodName;
        this.params = params;
        this.attachments = attachments;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeShort(invocationId);
        out.writeByte(0x07); // full ids
        out.writeUTF(fqBeanName);
        out.writeUTF(view);
        out.writeUTF(methodName);
        if (params != null) {
            out.writeByte(params.length);
            for (final Object param : params) {
                out.writeObject(param);
            }
        } else
            out.writeByte(0);
        if (attachments != null) {
            out.writeByte(attachments.length);
            for (final Attachment attachment : attachments) {
                // do not call writeObject, because we don't want serialization bits
                attachment.writeExternal(out);
            }
        } else
            out.writeByte(0);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

    }
}
