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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
abstract class Attachable {
    private final Map<Object, Object> attachments;

    protected Attachable(Map<Object, Object> attachments) {
        // for now the attachments map has to be a synchronized identity hash map
        assert attachments.getClass() == Collections.synchronizedMap(Collections.emptyMap()).getClass();
        this.attachments = attachments;
    }

    protected Attachable() {
        this(Collections.synchronizedMap(new IdentityHashMap<Object, Object>()));
    }

    protected Attachable(final Attachable attachable) {
        this(attachable.attachments);
    }

    @SuppressWarnings("unchecked")
    <T> T getAttachment(AttachmentKey<T> key) {
        return (T) attachments.get(key);
    }

    @SuppressWarnings("unchecked")
    <T> T putAttachment(AttachmentKey<T> key, T value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        return (T) attachments.put(key, value);
    }

    @SuppressWarnings("unchecked")
    <T> T removeAttachment(AttachmentKey<T> key) {
        return (T) attachments.remove(key);
    }

    void clearAttachments() {
        attachments.clear();
    }
}
