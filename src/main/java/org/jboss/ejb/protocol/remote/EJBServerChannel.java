/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.protocol.remote;

import java.io.IOException;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.util.StreamUtils;
import org.xnio.IoUtils;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class EJBServerChannel {

    private final Channel.Receiver receiver = new Channel.Receiver() {
        public void handleError(final Channel channel, final IOException error) {
            handleEnd(channel);
        }

        public void handleEnd(final Channel channel) {
        }

        public void handleMessage(final Channel channel, final MessageInputStream message) {

        }
    };

    private final OpenListener openListener = new OpenListener() {
        public void channelOpened(final Channel channel) {
            // send greeting
            try (MessageOutputStream os = channel.writeMessage()) {
                os.writeByte(Protocol.LATEST_VERSION);
                StreamUtils.writePackedUnsignedInt31(os, 1);
                os.write(Protocol.RIVER_BYTES);
            } catch (IOException e) {
                // forget about it!
                IoUtils.safeClose(channel);
                return;
            }
            // receive client greeting
            channel.receiveMessage(new Channel.Receiver() {
                public void handleError(final Channel channel, final IOException error) {

                }

                public void handleEnd(final Channel channel) {

                }

                public void handleMessage(final Channel channel, final MessageInputStream message) {

                }
            });
        }

        public void registrationTerminated() {
        }
    };
}
