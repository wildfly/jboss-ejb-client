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

package org.jboss.ejb.client.protocol;

import org.jboss.remoting3.MessageInputStream;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * User: jpai
 */
public class InvocationResponse implements Externalizable {

    public static final byte INVOCATION_RESPONSE_HEADER = 0x04;

    private transient Object result;

    private transient Throwable failure;

    private transient int invocationId;

    /**
     * @deprecated  This is here only for the purpose of marshalling/unmarshalling via Externalizable contract.
     */
    @Deprecated
    public InvocationResponse() {

    }

    public InvocationResponse(final int invocationId, final Object result, final Throwable throwable) {
        this.invocationId = invocationId;
        this.result = result;
        this.failure = throwable;
    }

    public boolean isException() {
        return this.failure != null;
    }

    public Object getResult() {
        return this.result;
    }

    public int getInvocationId() {
        return  this.invocationId;
    }

    public Throwable getException() {
        return this.failure;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeShort(this.invocationId);
        if (this.failure != null) {
            // exception indicator
            out.writeBoolean(true);
            out.writeObject(this.failure);
        } else {
            // no exception
            out.writeBoolean(false);
            out.writeObject(this.result);
        }

    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.invocationId = in.readShort();
        final boolean isException = in.readBoolean();
        if (isException) {
            this.failure = (Throwable) in.readObject();
        } else {
            this.result = in.readObject();
        }

    }
}
