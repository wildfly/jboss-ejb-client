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
    public static final byte INVOCATION_REQUEST_HEADER = 0x01;

    private transient int invocationId;
    private transient String appName;
    private transient String moduleName;
    private transient String beanName;
    private transient String viewClassName;
    private transient String methodName;
    private transient String[] paramTypes;
    private transient Object[] params;
    private transient Attachment[] attachments;


    @Deprecated
    public InvocationRequest() {
        // TODO: don't want to expose this constructor
    }

    protected InvocationRequest(final int invocationId, final String appName, final String moduleName,
                              final String beanName, final String viewClassName,
                              final String methodName, final String[] paramTypes, final Object[] methodParams, final Attachment[] attachments) {

        this();
        this.invocationId = invocationId;
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.viewClassName = viewClassName;
        this.methodName = methodName;
        this.params = methodParams;
        this.attachments = attachments;
        this.paramTypes = paramTypes;

    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeShort(invocationId);
        out.writeByte(0x07); // full ids
        if (this.appName != null) {
            out.writeUTF(appName);
        }
        out.writeUTF(moduleName);
        out.writeUTF(beanName);
        out.writeUTF(viewClassName);
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
        this.invocationId = in.readShort();
        in.read(); // full ids
        this.appName = in.readUTF();
        this.moduleName = in.readUTF();
        this.beanName = in.readUTF();
        this.viewClassName = in.readUTF();
        this.methodName = in.readUTF();
        int paramLength = in.readByte();
        if (paramLength < 0) {
            // negative length isn't valid
            throw new IOException("Invalid input - Negative param length: " + paramLength + " found");
        }
        this.params = new Object[paramLength];
        if (paramLength > 0) {
            for (int i = 0; i < paramLength; i++) {
                this.params[i] = in.readObject();
            }
        }
        int attachmentLength = in.readByte();
        if (attachmentLength < 0) {
            // negative length isn't valid
            throw new IOException("Invalid input - Negative attachment length: " + attachmentLength + " found");
        }
        this.attachments = new Attachment[attachmentLength];
        if (attachmentLength > 0) {
            for (int i = 0; i < attachmentLength; i++) {
                this.attachments[i] = Attachment.readAttachment(in);
            }
        }
    }

    public int getInvocationId() {
        return invocationId;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getViewClassName() {
        return viewClassName;
    }

    public Object[] getParams() {
        return params;
    }

    public String[] getParamTypes() {
        return this.paramTypes;
    }
    
    public Attachment[] getAttachments() {
        return attachments;
    }

    public String getAppName() {
        return this.appName;
    }

    public String getModuleName() {
        return this.moduleName;
    }

    public String getBeanName() {
        return this.beanName;
    }
}
