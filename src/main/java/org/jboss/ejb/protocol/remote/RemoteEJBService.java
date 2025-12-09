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
import static org.jboss.ejb.protocol.remote.TCCLUtils.getAndSetSafeTCCL;
import static org.jboss.ejb.protocol.remote.TCCLUtils.resetTCCL;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.ListenerHandle;
import org.jboss.logging.Logger;
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
 * The remote Enterprise Beans service.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class RemoteEJBService {

    protected static final Logger log = Logger.getLogger(RemoteEJBService.class.getSimpleName());

    private final OpenListener openListener;
    private final CallbackBuffer callbackBuffer = new CallbackBuffer();
    private final Set<Channel> openChannels = ConcurrentHashMap.newKeySet();

    private RemoteEJBService(final Association association, final RemotingTransactionService transactionService, final Function<String, Boolean> classResolverFilter) {
        openListener = new OpenListener() {
            public void channelOpened(final Channel channel) {
                if (log.isTraceEnabled()) {
                    log.tracef("Channel opened on port: %s", channel.getConnection().getLocalAddress());
                }
                // keep track of the channels created
                openChannels.add(channel);
                final MessageTracker messageTracker = new MessageTracker(channel, channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue());
                channel.receiveMessage(new Channel.Receiver() {
                    public void handleError(final Channel channel, final IOException error) {
                        // does nothing - TODO: don't forget to reset TCCL to safe CL when implementing this method
                    }

                    public void handleEnd(final Channel channel) {
                        // does nothing - TODO: don't forget to reset TCCL to safe CL when implementing this method
                    }

                    public void handleMessage(final Channel channel, final MessageInputStream message) {
                        final ClassLoader oldCL = getAndSetSafeTCCL();
                        try {
                            final int version;
                            try {
                                version = min(Protocol.LATEST_VERSION, StreamUtils.readInt8(message));
                                // drain the rest of the message because it's just garbage really
                                while (message.read() != -1) {
                                    message.skip(Long.MAX_VALUE);
                                }
                            } catch (IOException e) {
                                openChannels.remove(channel);
                                safeClose(channel);
                                return;
                            }
                            if (log.isTraceEnabled()) {
                                log.tracef("Creating EJBServerChannel");
                            }
                            final EJBServerChannel serverChannel = new EJBServerChannel(transactionService.getServerForConnection(channel.getConnection()),
                                    channel, version, messageTracker, classResolverFilter);
                            callbackBuffer.addListener((sc, a) -> {
                                final ListenerHandle handle1 = a.registerClusterTopologyListener(sc.createTopologyListener());
                                final ListenerHandle handle2 = a.registerModuleAvailabilityListener(sc.createModuleListener());
                                channel.receiveMessage(sc.getReceiver(a, handle1, handle2));
                            }, serverChannel, association);
                        } finally {
                            resetTCCL(oldCL);
                        }
                    }
                });
                try (MessageOutputStream mos = messageTracker.openMessage()) {
                    mos.writeByte(Protocol.LATEST_VERSION);
                    StreamUtils.writePackedUnsignedInt31(mos, 1);
                    mos.writeUTF("river");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    openChannels.remove(channel);
                    safeClose(channel);
                } catch (IOException e) {
                    openChannels.remove(channel);
                    safeClose(channel);
                }
            }

            public void registrationTerminated() {
                if (log.isTraceEnabled()) {
                    log.tracef("Registration terminated for EJBReceiverService open listener");
                }
                for (Channel channel : openChannels.toArray(new Channel[0])) {
                    if (log.isTraceEnabled()) {
                        log.tracef("Closing channel %s", channel);
                    }
                    safeClose(channel);
                }
            }
        };
    }

    /**
     * Create a new remote Enterprise Bean service instance without any class resolution filter function.
     *
     * @param association the association to use (must not be {@code null})
     * @param transactionService the Remoting transaction server to use (must not be {@code null})
     * @return the remote Enterprise Beans service instance (not {@code null})
     */
    public static RemoteEJBService create(final Association association, final RemotingTransactionService transactionService) {
        return create(association, transactionService, null);
    }

    /**
     * Create a new remote Enterprise Bean service instance.
     *
     * @param association the association to use (must not be {@code null})
     * @param transactionService the Remoting transaction server to use (must not be {@code null})
     * @param classResolverFilter filter function to apply to class names before resolving them during unmarshalling.
     *                            Must return {@link Boolean#TRUE} for the classname to be resolved, else unmarshalling
     *                            will fail. May be {@code null} in which case no filtering is performed
     * @return the remote Enterprise Beans service instance (not {@code null})
     */
    public static RemoteEJBService create(final Association association, final RemotingTransactionService transactionService,
                                          final Function<String, Boolean>  classResolverFilter) {
        Assert.checkNotNullParam("association", association);
        Assert.checkNotNullParam("transactionService", transactionService);
        return new RemoteEJBService(association, transactionService, classResolverFilter);
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
