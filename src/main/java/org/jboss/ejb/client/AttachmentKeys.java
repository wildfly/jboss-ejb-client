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

import java.lang.reflect.Method;
import java.util.Map;

import javax.transaction.Transaction;

import org.jboss.ejb.client.annotation.CompressionHint;

/**
 * Commonly-used attachment keys.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class AttachmentKeys {
    private AttachmentKeys() {
    }

    /**
     * The attachment key for transaction propagation.
     */
    public static final AttachmentKey<Transaction> TRANSACTION_KEY = new AttachmentKey<>();

    /**
     * The preferred node or cluster for invocations from this proxy.  Note that this name is only a
     * recommendation and is not required to be used, and if the node or cluster is not available then the invocation
     * may proceed to another node or cluster.  This key is normally associated with a proxy, and copied to an invocation.
     */
    public static final AttachmentKey<Affinity> WEAK_AFFINITY = new AttachmentKey<Affinity>();

    /**
     * The attachment key for legacy transaction IDs.  This key is normally associated with an invocation.
     */
    @Deprecated
    public static final AttachmentKey<TransactionID> TRANSACTION_ID_KEY = new AttachmentKey<TransactionID>();

    /**
     * An attachment key which specifies whether "hints" (like {@link org.jboss.ejb.client.annotation.CompressionHint}) are disabled
     */
    @Deprecated
    public static final AttachmentKey<Boolean> HINTS_DISABLED = new AttachmentKey<Boolean>();

    /**
     * A key to an attachment which contains the {@link org.jboss.ejb.client.annotation.CompressionHint}s specified on the remote view class level
     */
    @Deprecated
    public static final AttachmentKey<CompressionHint> VIEW_CLASS_DATA_COMPRESSION_HINT_ATTACHMENT_KEY = new AttachmentKey<CompressionHint>();

    /**
     * A key to an attachment which contains the {@link org.jboss.ejb.client.annotation.CompressionHint}s for methods which have been annotated with that data
     */
    @Deprecated
    public static final AttachmentKey<Map<Method, CompressionHint>> VIEW_METHOD_DATA_COMPRESSION_HINT_ATTACHMENT_KEY = new AttachmentKey<Map<Method, CompressionHint>>();

    /**
     * A key to an attachment which specifies whether the response payload data of an EJB invocation should be compressed
     */
    @Deprecated
    public static final AttachmentKey<Boolean> COMPRESS_RESPONSE = new AttachmentKey<Boolean>();

    /**
     * A key to an attachment which specifies the "compression level" of the response payload data of an EJB invocation
     */
    @Deprecated
    public static final AttachmentKey<Integer> RESPONSE_COMPRESSION_LEVEL = new AttachmentKey<Integer>();
}
