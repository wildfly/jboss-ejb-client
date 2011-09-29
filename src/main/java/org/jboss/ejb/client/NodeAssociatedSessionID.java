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

import java.io.UnsupportedEncodingException;

/**
 * A session ID which is affiliated with a particular node.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NodeAssociatedSessionID extends SessionID {

    private static final long serialVersionUID = -7306257085240447972L;
    private final transient boolean cluster;
    private final transient String nodeName;

    NodeAssociatedSessionID(final byte[] encodedForm) {
        super(encodedForm);
        if (encodedForm[0] != 0x07) {
            throw wrongFormat();
        }
        final int encLen = encodedForm.length;
        int end = -1;
        for (int i = 1; i < encLen; i++) {
            if (encodedForm[i] == 0) {
                end = i; break;
            }
        }
        if (end == -1 || end == 2) {
            throw wrongFormat();
        }
        try {
            nodeName = new String(encodedForm, 1, end, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw wrongFormat();
        }
        if (encLen < end + 17) {
            throw wrongFormat();
        }
        cluster = encodedForm[end + 1] != 0;
        // the rest is the unique ID
    }

    private static IllegalArgumentException wrongFormat() {
        return new IllegalArgumentException("Wrong session ID format");
    }

    /**
     * Get the associated node or cluster name.
     *
     * @return the name
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Determine whether the session ID refers to a node or a cluster.
     *
     * @return {@code true} if it is a cluster, {@code false} if it is a single node
     */
    public boolean isCluster() {
        return cluster;
    }
}
