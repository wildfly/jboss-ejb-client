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
 * User: jpai
 */
public class MethodParam implements Externalizable {

    private transient String paramType;

    private transient Object paramVal;

    // For Externalizable contract
    public MethodParam() {

    }

    public MethodParam(final String paramType, final Object paramVal) {
        if (paramType == null || paramType.trim().isEmpty()) {
            throw new IllegalArgumentException("Method param type cannot be null or empty string");
        }
        this.paramType = paramType;
        this.paramVal = paramVal;
    }

    public String getParamType() {
        return this.paramType;
    }

    public Object getParamValue() {
        return this.paramVal;
    }
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.paramType);
        out.writeObject(this.paramVal);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.paramType = in.readUTF();
        this.paramVal = in.readObject();
    }

}
