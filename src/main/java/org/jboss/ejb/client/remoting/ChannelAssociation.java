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

import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;
import org.xnio.FutureResult;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
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

    private final String marshallingStrategy;

    private final AtomicInteger nextInvocationId = new AtomicInteger(0);

    private Map<Short, EJBReceiverInvocationContext> waitingMethodInvocations = Collections.synchronizedMap(new HashMap<Short, EJBReceiverInvocationContext>());

    private Map<Short, FutureResult<EJBReceiverInvocationContext.ResultProducer>> waitingFutureResults = Collections.synchronizedMap(new HashMap<Short, FutureResult<EJBReceiverInvocationContext.ResultProducer>>());

    /**
     * Creates a channel association for the passed {@link EJBReceiverContext} and the {@link Channel}
     *
     * @param ejbReceiver         The EJB receiver
     * @param ejbReceiverContext  The receiver context
     * @param channel             The channel that will be used for remoting communication
     * @param protocolVersion     The protocol version
     * @param marshallingStrategy The marshalling strategy
     */
    ChannelAssociation(final RemotingConnectionEJBReceiver ejbReceiver, final EJBReceiverContext ejbReceiverContext,
                       final Channel channel, final byte protocolVersion, final String marshallingStrategy) {
        this.ejbReceiver = ejbReceiver;
        this.ejbReceiverContext = ejbReceiverContext;
        this.channel = channel;
        this.protocolVersion = protocolVersion;
        this.marshallingStrategy = marshallingStrategy;
        // register a receiver for receiving messages on the channel
        this.channel.receiveMessage(new ResponseReceiver());
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
    Future<EJBReceiverInvocationContext.ResultProducer> receiveResponse(final short invocationId) {
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
                ejbReceiverInvocationContext.resultReady(resultProducer);
            }
        } else if (this.waitingFutureResults.containsKey(invocationId)) {
            final FutureResult<EJBReceiverInvocationContext.ResultProducer> future = this.waitingFutureResults.remove(invocationId);
            if (future != null) {
                future.setResult(resultProducer);
            }
        } else {
            logger.info("Discarding result for invocation id " + invocationId + " since no waiting context found");
        }
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
            case 0x02:
                return new SessionOpenResponseHandler(this);
            case 0x05:
                return new MethodInvocationResponseHandler(this, this.marshallingStrategy);
            case 0x06:
                return new MethodInvocationApplicationExceptionResponseHandler(this, this.marshallingStrategy);
            case 0x08:
                return new ModuleAvailabilityMessageHandler(this.ejbReceiver, ModuleAvailabilityMessageHandler.ModuleReportType.MODULE_AVAILABLE);
            case 0x09:
                return new ModuleAvailabilityMessageHandler(this.ejbReceiver, ModuleAvailabilityMessageHandler.ModuleReportType.MODULE_UNAVAILABLE);
            case 0x0A:
                return new GeneralInvocationFailureResponseHandler(this, GeneralInvocationFailureResponseHandler.FailureType.NO_SUCH_EJB);
            case 0x0B:
                return new GeneralInvocationFailureResponseHandler(this, GeneralInvocationFailureResponseHandler.FailureType.NO_SUCH_METHOD);
            case 0x0C:
                return new GeneralInvocationFailureResponseHandler(this, GeneralInvocationFailureResponseHandler.FailureType.SESSION_NOT_ACTIVE);

            default:
                return null;
        }
    }


    /**
     * A {@link Channel.Receiver} for receiving message on the remoting channel
     */
    private class ResponseReceiver implements Channel.Receiver {

        @Override
        public void handleError(Channel channel, IOException error) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void handleEnd(Channel channel) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void handleMessage(Channel channel, MessageInputStream messageInputStream) {

            try {
                final int header = messageInputStream.read();
                // TODO: Log at a lower level (once we have a bit of stability in the impl)
                logger.info("Received message with header 0x" + Integer.toHexString(header));
                final ProtocolMessageHandler messageHandler = ChannelAssociation.this.getProtocolMessageHandler((byte) header);
                if (messageHandler == null) {
                    logger.warn("Unsupported message received with header 0x" + Integer.toHexString(header));
                    return;
                }
                messageHandler.processMessage(messageInputStream);

            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                // receive next message
                channel.receiveMessage(this);
            }
        }

    }
}
