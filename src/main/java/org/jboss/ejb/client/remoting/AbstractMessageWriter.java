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

import java.io.DataOutput;
import java.io.IOException;

/**
 * User: jpai
 */
class AbstractMessageWriter {

    protected void writeAttachments(final DataOutput output, final RemotingAttachments attachments) throws IOException {
        if (attachments == null) {
            output.writeByte(0);
            return;
        }
        // write attachment count
        output.writeByte(attachments.size());
        for (IntKeyMap.Entry<byte[]> attachment : attachments.entries()) {
            // write attachment id
            output.writeShort(attachment.getKey());
            final byte[] data = attachment.getValue();
            // write data length
            PackedInteger.writePackedInteger(output, data.length);
            // write the data
            output.write(data);

        }
    }
}
