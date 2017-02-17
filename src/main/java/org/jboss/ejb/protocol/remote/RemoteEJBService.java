/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.ejb.protocol.remote;

import static java.lang.Math.min;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;

import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.ListenerHandle;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.util.MessageTracker;
import org.jboss.remoting3.util.StreamUtils;
import org.wildfly.common.Assert;
import org.wildfly.transaction.client.provider.remoting.RemotingTransactionService;

/**
 * The remote EJB service.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteEJBService {
    private final Association association;
    private final RemotingTransactionService transactionService;
    private final OpenListener openListener;
    private final CallbackBuffer callbackBuffer = new CallbackBuffer();

    private RemoteEJBService(final Association association, final RemotingTransactionService transactionService) {
        this.association = association;
        this.transactionService = transactionService;
        openListener = new OpenListener() {
            public void channelOpened(final Channel channel) {
                final MessageTracker messageTracker = new MessageTracker(channel, channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue());
                channel.receiveMessage(new Channel.Receiver() {
                    public void handleError(final Channel channel, final IOException error) {
                    }

                    public void handleEnd(final Channel channel) {
                    }

                    public void handleMessage(final Channel channel, final MessageInputStream message) {
                        final int version;
                        try {
                            version = min(3, StreamUtils.readInt8(message));
                            // drain the rest of the message because it's just garbage really
                            while (message.read() != - 1) {
                                message.skip(Long.MAX_VALUE);
                            }
                        } catch (IOException e) {
                            safeClose(channel);
                            return;
                        }
                        final EJBServerChannel serverChannel = new EJBServerChannel(transactionService.getServerForConnection(channel.getConnection()), channel, version, messageTracker, callbackBuffer);
                        callbackBuffer.addListener((sc, a) -> {
                            final ListenerHandle handle1 = a.registerClusterTopologyListener(sc.createTopologyListener());
                            final ListenerHandle handle2 = a.registerModuleAvailabilityListener(sc.createModuleListener());
                            channel.receiveMessage(sc.getReceiver(a, handle1, handle2));
                        }, serverChannel, association);
                    }
                });
                try (MessageOutputStream mos = messageTracker.openMessage()) {
                    mos.writeByte(Protocol.LATEST_VERSION);
                    StreamUtils.writePackedUnsignedInt31(mos, 1);
                    mos.writeUTF("river");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    safeClose(channel);
                } catch (IOException e) {
                    safeClose(channel);
                }
            }

            public void registrationTerminated() {
            }
        };
    }

    /**
     * Create a new remote EJB service instance.
     *
     * @param association the association to use (must not be {@code null})
     * @param transactionService the Remoting transaction server to use (must not be {@code null})
     * @return the remote EJB service instance (not {@code null})
     */
    public static RemoteEJBService create(final Association association, final RemotingTransactionService transactionService) {
        Assert.checkNotNullParam("association", association);
        Assert.checkNotNullParam("transactionService", transactionService);
        return new RemoteEJBService(association, transactionService);
    }

    /**
     * Get the service open listener.
     *
     * @return the service open listener
     */
    public OpenListener getOpenListener() {
        return openListener;
    }

    /**
     * Indicate that the server is up, which will allow client invocations to proceed.  This method must be called
     * in order for invocations to flow through the server.
     */
    public void serverUp() {
        callbackBuffer.activate();
    }
}
