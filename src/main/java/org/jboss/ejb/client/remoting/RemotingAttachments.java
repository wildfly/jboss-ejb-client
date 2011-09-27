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

package org.jboss.ejb.client.remoting;

import javax.transaction.xa.XAResource;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemotingAttachments {
    private final IntKeyMap<byte[]> payloadAttachments = new IntKeyMap<byte[]>();

    public byte[] getPayloadAttachment(int key) {
        if (key < 0 || key > 0xFFFF) {
            return null;
        }
        return payloadAttachments.get(key);
    }

    public byte[] putPayloadAttachment(int key, byte[] newValue) {
        if (newValue == null) {
            throw new IllegalArgumentException("newValue is null");
        }
        if (key < 0 || key > 0xFFFF) {
            throw new IllegalArgumentException("Attachment key is out of range (must be 0-65535)");
        }
        return payloadAttachments.put(key, newValue);
    }

    public byte[] removePayloadAttachment(int key) {
        if (key < 0 || key > 0xFFFF) {
            return null;
        }
        return payloadAttachments.remove(key);
    }

    void clearPayloadAttachments() {
        payloadAttachments.clear();
    }

    Iterable<IntKeyMap.Entry<byte[]>> entries() {
        return payloadAttachments;
    }

    public XAResource getXAResourceInstance() {
        throw new RuntimeException("not impl");
    }
}
