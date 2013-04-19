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

import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.Logs;
import org.jboss.logging.Logger;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.FutureResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ChannelAssociation} keeps track of the association between the {@link EJBReceiverContext}
 * of a {@link RemotingConnectionEJBReceiver} and the {@link Channel} on which the communication is going to
 * happen for that particular association.
 * <p/>
 * The {@link RemotingConnectionEJBReceiver} uses a {@link ChannelAssociation} to send/receive messages to/from
 * the remote server
 * <p/>
 * User: Jaikiran Pai
 */
class ChannelAssociation {

    private static final Logger logger = Logger.getLogger(ChannelAssociation.class);

    private final RemotingConnectionEJBReceiver ejbReceiver;

    private final EJBReceiverContext ejbReceiverContext;

    private final Channel channel;

    private final byte protocolVersion;

    private final MarshallerFactory marshallerFactory;

    private final AtomicInteger nextInvocationId = new AtomicInteger(0);

    private final Map<Short, EJBReceiverInvocationContext> waitingMethodInvocations = Collections.synchronizedMap(new HashMap<Short, EJBReceiverInvocationContext>());

    private final Map<Short, FutureResult<EJBReceiverInvocationContext.ResultProducer>> waitingFutureResults = Collections.synchronizedMap(new HashMap<Short, FutureResult<EJBReceiverInvocationContext.ResultProducer>>());

    private final ReconnectHandler reconnectHandler;

    // A semaphore which will be used to acquire a lock while writing out to a channel
    // to make sure that only a limited number of simultaneous writes are allowed
    private final Semaphore channelWriteSemaphore;

    // Keeps track of the invocation ids for each of the EJB receiver invocation contexts
    private final Map<EJBReceiverInvocationContext, Short> invocationIdsPerReceiverInvocationCtx = Collections.synchronizedMap(new IdentityHashMap<EJBReceiverInvocationContext, Short>());

    private final MessageCompatibilityChecker messageCompatibilityChecker;

    private final String remotingProtocol;

    /**
     * Creates a channel association for the passed {@link EJBReceiverContext} and the {@link Channel}
     *
     * @param ejbReceiver        The EJB receiver
     * @param ejbReceiverContext The receiver context
     * @param channel            The channel that will be used for remoting communication
     * @param protocolVersion    The protocol version
     * @param marshallerFactory  The marshalling factory
     * @param reconnectHandler   The reconnect handler to use for broken connections/channels. Can be null.
     * @param remotingProtocol
     */
    ChannelAssociation(final RemotingConnectionEJBReceiver ejbReceiver, final EJBReceiverContext ejbReceiverContext,
                       final Channel channel, final byte protocolVersion, final MarshallerFactory marshallerFactory,
                       final ReconnectHandler reconnectHandler, final String remotingProtocol) {
        this.ejbReceiver = ejbReceiver;
        this.ejbReceiverContext = ejbReceiverContext;
        this.channel = channel;
        this.protocolVersion = protocolVersion;
        this.marshallerFactory = marshallerFactory;
        this.reconnectHandler = reconnectHandler;
        this.remotingProtocol = remotingProtocol;

        this.channel.addCloseHandler(new CloseHandler<Channel>() {
            @Override
            public void handleClose(Channel closed, IOException exception) {
                logger.debug("Closing channel " + closed, exception);
                // notify about the broken channel and do the necessary cleanups
                if (exception != null) {
                    ChannelAssociation.this.notifyBrokenChannel(exception);
                } else {
                    ChannelAssociation.this.notifyBrokenChannel(new IOException("Channel " + closed + " has been closed"));
                }
            }
        });
        // register a receiver for receiving messages on the channel
        this.channel.receiveMessage(new ResponseReceiver());

        // write semaphore
        Integer maxOutboundWrites = this.channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES);
        if (maxOutboundWrites == null) {
            maxOutboundWrites = 80;
        }
        this.channelWriteSemaphore = new Semaphore(maxOutboundWrites, true);

        switch (protocolVersion) {
            case 0x01:
                this.messageCompatibilityChecker = new ProtocolVersionOneMessageCompatibilityChecker();
                break;
            case 0x02:
                this.messageCompatibilityChecker = new ProtocolVersionTwoMessageCompatibilityChecker();
                break;
            default:
                this.messageCompatibilityChecker = null;
        }
    }

    /**
     * Returns the channel on which the communication for this {@link ChannelAssociation association}
     * takes places
     *
     * @return
     */
    Channel getChannel() {
        return this.channel;
    }

    /**
     * Returns the {@link EJBReceiverContext} applicable for this {@link ChannelAssociation}
     *
     * @return
     */
    EJBReceiverContext getEjbReceiverContext() {
        return this.ejbReceiverContext;
    }

    /**
     * Returns the next invocation id that can be used for invocations on the channel corresponding to
     * this {@link ChannelAssociation association}
     *
     * @return
     */
    short getNextInvocationId() {
        return (short) nextInvocationId.getAndIncrement();
    }

    /**
     * Enroll a {@link EJBReceiverInvocationContext} to receive (an inherently asynchronous) response for a
     * invocation corresponding to the passed <code>invocationId</code>. This method does <b>not</b> block.
     * Whenever a result is available for the <code>invocationId</code>, the {@link EJBReceiverInvocationContext#resultReady(org.jboss.ejb.client.EJBReceiverInvocationContext.ResultProducer)}
     * will be invoked.
     *
     * @param invocationId                 The invocation id
     * @param ejbReceiverInvocationContext The receiver invocation context which will be used to send back a result when it's available
     */
    void receiveResponse(final short invocationId, final EJBReceiverInvocationContext ejbReceiverInvocationContext) {
        this.waitingMethodInvocations.put(invocationId, ejbReceiverInvocationContext);
        // keep track of the invocation id for the EJB receiver invocation context
        this.invocationIdsPerReceiverInvocationCtx.put(ejbReceiverInvocationContext, invocationId);

    }

    /**
     * Returns a {@link Future} {@link EJBReceiverInvocationContext.ResultProducer result producer} for the
     * passed <code>invocationId</code>. This method does <b>not</b> block. The caller can use the returned
     * {@link Future} to {@link java.util.concurrent.Future#get() wait} for a {@link org.jboss.ejb.client.EJBReceiverInvocationContext.ResultProducer}
     * to be available. Once available, the {@link org.jboss.ejb.client.EJBReceiverInvocationContext.ResultProducer#getResult()}
     * can be used to obtain the result for the invocation corresponding to the passed <code>invocationId</code>
     *
     * @param invocationId The invocation id
     * @return
     */
    Future<EJBReceiverInvocationContext.ResultProducer> enrollForResult(final short invocationId) {
        final FutureResult<EJBReceiverInvocationContext.ResultProducer> futureResult = new FutureResult<EJBReceiverInvocationContext.ResultProducer>();
        this.waitingFutureResults.put(invocationId, futureResult);
        return IoFutureHelper.future(futureResult.getIoFuture());
    }

    /**
     * Invoked when a result is ready for a (prior) invocation corresponding to the passed <code>invocationId</code>.
     *
     * @param invocationId   The invocation id
     * @param resultProducer The result producer which will be used to get hold of the result
     */
    void resultReady(final short invocationId, final EJBReceiverInvocationContext.ResultProducer resultProducer) {
        if (this.waitingMethodInvocations.containsKey(invocationId)) {
            final EJBReceiverInvocationContext ejbReceiverInvocationContext = this.waitingMethodInvocations.remove(invocationId);
            if (ejbReceiverInvocationContext != null) {
                // we no longer need to keep track of the invocation id that was used for
                // this EJB receiver invocation context, so remove it
                this.invocationIdsPerReceiverInvocationCtx.remove(ejbReceiverInvocationContext);
                // let the receiver invocation context know that the result is ready
                ejbReceiverInvocationContext.resultReady(resultProducer);
            }
        } else if (this.waitingFutureResults.containsKey(invocationId)) {
            final FutureResult<EJBReceiverInvocationContext.ResultProducer> future = this.waitingFutureResults.remove(invocationId);
            if (future != null) {
                future.setResult(resultProducer);
            }
        } else {
            Logs.REMOTING.discardingInvocationResult(invocationId);
        }
    }

    /**
     * Invoked when the server notifies the client that a (previous) method invocation corresponds to a method
     * marked as asynchronous. This method lets the {@link EJBReceiverInvocationContext} corresponding to the <code>invocationId</code>
     * know that it can {@link org.jboss.ejb.client.EJBReceiverInvocationContext#proceedAsynchronously() unblock the invocation}
     *
     * @param invocationId The invocation id
     */
    void handleAsyncMethodNotification(final short invocationId) {
        final EJBReceiverInvocationContext receiverInvocationContext = this.waitingMethodInvocations.get(invocationId);
        if (receiverInvocationContext == null) {
            logger.debug("No waiting context found for async method invocation with id " + invocationId);
            return;
        }
        receiverInvocationContext.proceedAsynchronously();
    }

    EJBReceiverInvocationContext getEJBReceiverInvocationContext(short invocationId) {
        return this.waitingMethodInvocations.get(invocationId);
    }

    /**
     * Opens and returns a new {@link MessageOutputStream} for the {@link Channel} represented
     * by this {@link ChannelAssociation}. Before opening the message outputstream, this method acquires a permit
     * to make sure only a limited number of simultaneous writes are allowed on the channel. The permit acquistion
     * is a blocking wait.
     *
     * @return
     * @throws Exception
     */
    MessageOutputStream acquireChannelMessageOutputStream() throws Exception {
        this.channelWriteSemaphore.acquire();
        try {
            return this.channel.writeMessage();
        } catch (Exception e) {
            // release
            this.channelWriteSemaphore.release();
            throw e;
        }
    }

    /**
     * Releases a previously held permit/lock on a message outputstream of a channel and also closes
     * the <code>messageOutputStream</code>
     *
     * @param messageOutputStream The message outputstream
     * @throws IOException
     */
    void releaseChannelMessageOutputStream(final MessageOutputStream messageOutputStream) throws IOException {
        try {
            messageOutputStream.close();
        } finally {
            this.channelWriteSemaphore.release();
        }
    }

    /**
     * Returns the invocation id that was used for a prior invocation on this channel, for the
     * passed {@link EJBReceiverInvocationContext ejbReceiverInvocationContext}. This method returns
     * null if there was no prior invocation on this channel for the passed {@link EJBReceiverInvocationContext}
     * or if the prior invocation has already completed
     *
     * @param ejbReceiverInvocationContext The EJB receiver invocation context
     * @return
     */
    Short getInvocationId(final EJBReceiverInvocationContext ejbReceiverInvocationContext) {
        return this.invocationIdsPerReceiverInvocationCtx.get(ejbReceiverInvocationContext);
    }

    /**
     * Returns a {@link ProtocolMessageHandler} for the passed message <code>header</code>. Returns
     * null if there's no such {@link ProtocolMessageHandler}.
     *
     * @param header The message header
     * @return
     */
    private ProtocolMessageHandler getProtocolMessageHandler(final byte header) {
        switch (header) {
            // Please try and maintain the case statements in the numerical order of the headers for the sake of quickly finding the highest known response header at present.
            case 0x02:
                return new SessionOpenResponseHandler(this, this.marshallerFactory);
            case 0x05:
                return new MethodInvocationResponseHandler(this, this.marshallerFactory);
            case 0x06:
                return new InvocationExceptionResponseHandler(this, this.marshallerFactory);
            case 0x08:
                return new ModuleAvailabilityMessageHandler(this.ejbReceiver, this.ejbReceiverContext, ModuleAvailabilityMessageHandler.ModuleReportType.MODULE_AVAILABLE);
            case 0x09:
                return new ModuleAvailabilityMessageHandler(this.ejbReceiver, this.ejbReceiverContext, ModuleAvailabilityMessageHandler.ModuleReportType.MODULE_UNAVAILABLE);
            case 0x0A:
                return new NoSuchEJBExceptionResponseHandler(this);
            case 0x0B:
                return new GeneralInvocationFailureResponseHandler(this, GeneralInvocationFailureResponseHandler.FailureType.NO_SUCH_METHOD);
            case 0x0C:
                return new GeneralInvocationFailureResponseHandler(this, GeneralInvocationFailureResponseHandler.FailureType.SESSION_NOT_ACTIVE);
            case 0x0D:
                return new NonStatefulBeanSessionOpenFailureHandler(this);
            case 0x0E:
                return new AsyncMethodNotificationHandler(this);
            case 0x14:
                return new TransactionInvocationResponseHandler(this);
            case 0x15:
                // complete cluster topology message handler
                return new ClusterTopologyMessageHandler(this, true);
            case 0x16:
                return new ClusterRemovalMessageHandler(this.ejbReceiverContext);
            case 0x17:
                // new node additions message handler
                return new ClusterTopologyMessageHandler(this, false);
            case 0x18:
                // node removal message handler
                return new ClusterNodeRemovalHandler(this);
            case 0x1A:
                // transaction recovery response
                return new TransactionRecoveryResponseHandler(this, this.marshallerFactory);
            case 0x1B:
                // compressed data (response)
                return new CompressedMessageHandler(this);
            default:
                return null;
        }
    }

    void processResponse(final InputStream inputStream) throws IOException {
        // get the header in the message
        final int header = inputStream.read();
        if (logger.isTraceEnabled()) {
            logger.trace("Received message with header 0x" + Integer.toHexString(header));
        }
        // get the protocol message handler for the header
        final ProtocolMessageHandler messageHandler = getProtocolMessageHandler((byte) header);
        if (messageHandler == null) {
            logger.debug("Unsupported message received with header 0x" + Integer.toHexString(header));
            return;
        }
        // let the protocol handler process the stream
        messageHandler.processMessage(inputStream);
    }

    boolean isMessageCompatibleForNegotiatedProtocolVersion(final byte messageHeader) {
        if (messageCompatibilityChecker == null) {
            return false;
        }
        return messageCompatibilityChecker.isMessageCompatible(messageHeader);
    }

    byte getNegotiatedProtocolVersion() {
        return this.protocolVersion;
    }

    private void notifyBrokenChannel(final IOException ioException) {
        if (ioException == null) {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        try {
            final EJBReceiverInvocationContext.ResultProducer unusableChannelResultProducer = new UnusableChannelResultProducer(ioException);
            // notify waiting method invocations
            final Collection<EJBReceiverInvocationContext> receiverInvocationContexts = this.waitingMethodInvocations.values();
            // @see javadoc of Collections.synchronizedMap()
            synchronized (this.waitingMethodInvocations) {
                for (final EJBReceiverInvocationContext receiverInvocationContext : receiverInvocationContexts) {
                    receiverInvocationContext.resultReady(unusableChannelResultProducer);
                }
            }
            // notify the other waiting invocations
            final Collection<FutureResult<EJBReceiverInvocationContext.ResultProducer>> futureResults = this.waitingFutureResults.values();
            // @see javadoc of Collections.synchronizedMap()
            synchronized (this.waitingFutureResults) {
                for (final FutureResult<EJBReceiverInvocationContext.ResultProducer> resultProducerFutureResult : futureResults) {
                    resultProducerFutureResult.setResult(unusableChannelResultProducer);
                }
            }
        } finally {
            // close the receiver context
            this.ejbReceiverContext.close();
            // register a re-connect handler (if available) to the EJB client context
            if (this.reconnectHandler != null) {
                final EJBClientContext ejbClientContext = this.ejbReceiverContext.getClientContext();
                logger.debug("Registering a re-connect handler " + this.reconnectHandler + " for broken channel " + this.channel + " in EJB client context " + ejbClientContext);
                ejbClientContext.registerReconnectHandler(this.reconnectHandler);
            }
        }

    }


    /**
     * A {@link Channel.Receiver} for receiving message on the remoting channel
     */
    private class ResponseReceiver implements Channel.Receiver {

        @Override
        public void handleError(Channel channel, IOException error) {
            logger.error("Error on channel " + channel, error);
            // close the channel and let the CloseHandler handle the cleanup
            try {
                channel.close();
            } catch (IOException ioe) {
                // couldn't properly close, so let's do the cleanup that the CloseHandler was expected to do

                // notify about the broken channel and do the necessary cleanups
                if (error != null) {
                    ChannelAssociation.this.notifyBrokenChannel(error);
                } else {
                    ChannelAssociation.this.notifyBrokenChannel(new IOException("Channel " + channel + " received error notification"));
                }
            }

        }

        @Override
        public void handleEnd(Channel channel) {
            Logs.REMOTING.channelCanNoLongerProcessMessages(channel);
            // close the channel and let the CloseHandler handle the cleanup
            try {
                channel.close();
            } catch (IOException ioe) {
                // couldn't properly close, so let's do the cleanup that the CloseHandler was expected to do

                // notify about the broken channel and do the necessary cleanups
                ChannelAssociation.this.notifyBrokenChannel(new IOException("Channel " + channel + " is no longer readable"));
            }
        }

        @Override
        public void handleMessage(Channel channel, MessageInputStream messageInputStream) {

            try {
                processResponse(messageInputStream);
            } catch (IOException e) {
                this.handleError(channel, e);
            } finally {
                // receive next message
                channel.receiveMessage(this);
            }
        }

    }

    /**
     * A {@link org.jboss.ejb.client.EJBReceiverInvocationContext.ResultProducer} which in its
     * {@link org.jboss.ejb.client.EJBReceiverInvocationContext.ResultProducer#getResult()} throws the {@link IOException}
     * which resulted in the channel being unusable
     */
    private class UnusableChannelResultProducer implements EJBReceiverInvocationContext.ResultProducer {

        private final IOException ioException;

        UnusableChannelResultProducer(final IOException ioexception) {
            this.ioException = ioexception;
        }

        @Override
        public Object getResult() throws Exception {
            throw this.ioException;
        }

        @Override
        public void discardResult() {
        }
    }

    private interface MessageCompatibilityChecker {
        /**
         * Returns true if the message represented by the <code>messageHeader</code> is compatible in the protocol version represented by the {@link MessageCompatibilityChecker}. Else
         * returns false.
         *
         * @param messageHeader The message header
         * @return
         */
        boolean isMessageCompatible(final byte messageHeader);
    }

    private class ProtocolVersionOneMessageCompatibilityChecker implements MessageCompatibilityChecker {

        @Override
        public boolean isMessageCompatible(final byte messageHeader) {
            switch (messageHeader) {
                case 0x01: // session open request
                case 0x03: // method invocation request
                case 0x0F: // tx commit request
                case 0x10: // tx rollback request
                case 0x11: // tx prepare request
                case 0x12: // tx forget request
                case 0x13: // tx before completion request
                case 0x04: // method invocation cancel request
                    return true;
                default:
                    return false;

            }
        }
    }

    private class ProtocolVersionTwoMessageCompatibilityChecker extends ProtocolVersionOneMessageCompatibilityChecker {
        @Override
        public boolean isMessageCompatible(final byte messageHeader) {
            switch (messageHeader) {
                case 0x19: // tx recover request
                case 0x1B: // compressed message request
                    return true;
                default:
                    return super.isMessageCompatible(messageHeader);
            }
        }
    }

    String getRemotingProtocol() {
        return remotingProtocol;
    }
}
