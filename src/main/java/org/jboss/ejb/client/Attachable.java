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

import org.wildfly.common.Assert;

/**
 * An object which may have attachments.  Even if the object is serializable, its
 * attachment map is not and will always deserialize empty.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class Attachable {
    private final Map<AttachmentKey<?>, Object> attachments;

    private Attachable(Map<AttachmentKey<?>, Object> attachments) {
        this.attachments = attachments;
    }

    Attachable() {
        this(new IdentityHashMap<AttachmentKey<?>, Object>());
    }

    /**
     * Construct a new instance, sharing attachments with another instance.
     *
     * @param attachable the attachments to share
     */
    Attachable(final Attachable attachable) {
        this(attachable.attachments);
    }

    /**
     * Get an attachment from this object.
     *
     * @param key the attachment key
     * @param <T> the attachment type
     * @return the attachment value
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttachment(AttachmentKey<T> key) {
        if (key == null) return null;
        final Map<AttachmentKey<?>, Object> attachments = this.attachments;
        synchronized (attachments) {
            return (T) attachments.get(key);
        }
    }

    /**
     * Returns the attachments applicable for this {@link Attachable}. The returned {@link Map}
     * is an unmodifiable {@link Map}. If there are no attachments for this {@link Attachable}
     * then this method returns an empty {@link Map}
     *
     * @return a read-only copy of the attachments map
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<AttachmentKey<?>, ?> getAttachments() {
        if (this.attachments == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap((Map) this.attachments);
    }

    /**
     * Set an attachment on this object.
     *
     * @param key   the attachment key
     * @param value the attachment's new value (may not be {@code null})
     * @param <T>   the attachment type
     * @return the previous attachment value, or {@code null} if there was none
     */
    @SuppressWarnings("unchecked")
    public <T> T putAttachment(AttachmentKey<T> key, T value) {
        Assert.checkNotNullParam("key", key);
        Assert.checkNotNullParam("value", value);
        final Map<AttachmentKey<?>, Object> attachments = this.attachments;
        synchronized (attachments) {
            return (T) attachments.put(key, value);
        }
    }

    /**
     * Set an attachment on this object if an existing attachment does not already exist.
     *
     * @param key   the attachment key
     * @param value the attachment's new value (may not be {@code null})
     * @param <T>   the attachment type
     * @return the previous attachment value, or {@code null} if there was none
     */
    @SuppressWarnings("unchecked")
    public <T> T putAttachmentIfAbsent(AttachmentKey<T> key, T value) {
        Assert.checkNotNullParam("key", key);
        Assert.checkNotNullParam("value", value);
        final Map<AttachmentKey<?>, Object> attachments = this.attachments;
        synchronized (attachments) {
            return (T) (attachments.containsKey(key) ? attachments.get(key) : attachments.put(key, value));
        }
    }

    /**
     * Replace an attachment on this object if an existing attachment exists.
     *
     * @param key   the attachment key
     * @param value the attachment's new value (may not be {@code null})
     * @param <T>   the attachment type
     * @return the previous attachment value, or {@code null} if there was none
     */
    @SuppressWarnings("unchecked")
    public <T> T replaceAttachment(AttachmentKey<T> key, T value) {
        if (key == null) return null;
        Assert.checkNotNullParam("value", value);
        final Map<AttachmentKey<?>, Object> attachments = this.attachments;
        synchronized (attachments) {
            return (T) (attachments.containsKey(key) ? attachments.put(key, value) : null);
        }
    }

    /**
     * Replace an attachment on this object if an existing attachment exists with a certain value.
     *
     * @param key      the attachment key
     * @param oldValue the attachment's expected value (may not be {@code null})
     * @param newValue the attachment's new value (may not be {@code null})
     * @param <T>      the attachment type
     * @return {@code true} if the old value matched and the value was replaced; {@code false} otherwise
     */
    @SuppressWarnings("unchecked")
    public <T> boolean replaceAttachment(AttachmentKey<T> key, T oldValue, T newValue) {
        if (key == null) return false;
        if (oldValue == null) return false;
        Assert.checkNotNullParam("newValue", newValue);
        final Map<AttachmentKey<?>, Object> attachments = this.attachments;
        synchronized (attachments) {
            Object lhs = attachments.get(key);
            return attachments.containsKey(key) && oldValue.equals(lhs) && attachments.put(key, newValue) != null;
        }
    }

    /**
     * Remove and return an attachment value.
     *
     * @param key the attachment key
     * @param <T> the attachment type
     * @return the previous value of the attachment, or {@code null} if there was none
     */
    @SuppressWarnings("unchecked")
    public <T> T removeAttachment(AttachmentKey<T> key) {
        if (key == null) return null;
        final Map<AttachmentKey<?>, Object> attachments = this.attachments;
        synchronized (attachments) {
            return (T) attachments.remove(key);
        }
    }

    /**
     * Remove an attachment if it has a certain value.
     *
     * @param key   the attachment key
     * @param value the attachment's expected value (may not be {@code null})
     * @param <T>   the attachment type
     * @return {@code true} if the value was removed, {@code false} if there was no attachment
     */
    @SuppressWarnings("unchecked")
    public <T> boolean removeAttachment(AttachmentKey<T> key, T value) {
        if (key == null) return false;
        if (value == null) return false;
        final Map<AttachmentKey<?>, Object> attachments = this.attachments;
        synchronized (attachments) {
            Object lhs = attachments.get(key);
            return attachments.containsKey(key) && value.equals(lhs) && attachments.remove(key) != null;
        }
    }

    void clearAttachments() {
        attachments.clear();
    }
}
