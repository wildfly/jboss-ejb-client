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

import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.TransactionID;
import org.jboss.logging.Logger;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import javax.transaction.xa.XAException;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A {@link EJBReceiver} which uses JBoss Remoting to communicate with the server for EJB invocations
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemotingConnectionEJBReceiver extends EJBReceiver {

    private static final Logger logger = Logger.getLogger(RemotingConnectionEJBReceiver.class);

    private static final String EJB_CHANNEL_NAME = "jboss.ejb";

    private final Connection connection;

    private final Map<EJBReceiverContext, ChannelAssociation> channelAssociations = new IdentityHashMap<EJBReceiverContext, ChannelAssociation>();

    // TODO: The version and the marshalling strategy shouldn't be hardcoded here
    private final byte clientProtocolVersion = 0x01;
    private final String clientMarshallingStrategy = "river";

    /**
     * A latch which will be used to wait for the initial module availability report from the server
     * after the version handshake between the server and the client is successfully completed.
     */
    private final Map<EJBReceiverContext, CountDownLatch> moduleAvailabilityReportLatches = new IdentityHashMap<EJBReceiverContext, CountDownLatch>();

    private final String cachedToString;

    private final MarshallerFactory marshallerFactory;

    /**
     * Construct a new instance.
     *
     * @param connection the connection to associate with
     */
    public RemotingConnectionEJBReceiver(final Connection connection) {
        super(connection.getRemoteEndpointName());
        this.connection = connection;

        this.cachedToString = new StringBuffer("Remoting connection EJB receiver [connection=").append(this.connection)
                .append(",channel=").append(EJB_CHANNEL_NAME).append(",nodename=").append(this.getNodeName())
                .append("]").toString();

        this.marshallerFactory = Marshalling.getProvidedMarshallerFactory(this.clientMarshallingStrategy);
        if (this.marshallerFactory == null) {
            throw new RuntimeException("Could not find a marshaller factory for " + this.clientMarshallingStrategy + " marshalling strategy");
        }
    }

    @Override
    public void associate(final EJBReceiverContext context) {
        // a latch for waiting a version handshake
        final CountDownLatch versionHandshakeLatch = new CountDownLatch(1);
        // setup a latch which will be used for waiting initial module availability report from the server
        final CountDownLatch initialModuleAvailabilityLatch = new CountDownLatch(1);
        synchronized (this.moduleAvailabilityReportLatches) {
            this.moduleAvailabilityReportLatches.put(context, initialModuleAvailabilityLatch);
        }

        final VersionReceiver versionReceiver = new VersionReceiver(versionHandshakeLatch, this.clientProtocolVersion, this.clientMarshallingStrategy);
        final IoFuture<Channel> futureChannel = connection.openChannel(EJB_CHANNEL_NAME, OptionMap.EMPTY);
        futureChannel.addNotifier(new IoFuture.HandlingNotifier<Channel, EJBReceiverContext>() {
            public void handleCancelled(final EJBReceiverContext context) {
                logger.debug("Channel open requested cancelled for context " + context);
                context.close();
            }

            public void handleFailed(final IOException exception, final EJBReceiverContext context) {
                logger.error("Failed to open channel for context " + context, exception);
                context.close();
            }

            public void handleDone(final Channel channel, final EJBReceiverContext context) {
                channel.addCloseHandler(new CloseHandler<Channel>() {
                    public void handleClose(final Channel closed, final IOException exception) {
                        logger.debug("Closing channel" + closed, exception);
                        context.close();
                    }
                });
                logger.debug("Channel " + channel + " opened for context " + context + " Waiting for version handshake message from server");
                // receive version message from server
                channel.receiveMessage(versionReceiver);
            }
        }, context);

        boolean successfulHandshake = false;
        try {
            // wait for the handshake to complete
            // TODO: Think about externalizing this timeout
            successfulHandshake = versionHandshakeLatch.await(5, TimeUnit.SECONDS);
            if (successfulHandshake) {
                final Channel compatibleChannel = versionReceiver.getCompatibleChannel();
                final ChannelAssociation channelAssociation = new ChannelAssociation(this, context, compatibleChannel, this.clientProtocolVersion, this.marshallerFactory);
                synchronized (this.channelAssociations) {
                    this.channelAssociations.put(context, channelAssociation);
                }
                logger.info("Successful version handshake completed for receiver context " + context + " on channel " + compatibleChannel);
            } else {
                // no version handshake done. close the context
                logger.info("Version handshake not completed for receiver context " + context + " by receiver " + this + " . Closing the receiver context");
                context.close();
            }
        } catch (InterruptedException e) {
            context.close();
        }

        if (successfulHandshake) {
            // Now that the version handshake has been completed, let's await the initial module report
            // from the server. This initial wait is necessary to ensure that any immediate invocation on the receiver
            // doesn't fail due to non-availability of the module report (which effectively means this receiver won't
            // know whether it can handle an invocation on a appname/modulename/distinctname combination
            try {
                final boolean initialReportAvailable = initialModuleAvailabilityLatch.await(5, TimeUnit.SECONDS);
                if (!initialReportAvailable) {
                    // let's log a message and just return back. Don't close the context since it's *not* an error
                    // that the module report wasn't available in that amount of time.
                    logger.info("Initial module availability report for " + this + " wasn't received during the receiver context association");
                }
            } catch (InterruptedException e) {
                logger.debug("Caught InterruptedException while waiting for initial module availability report for " + this, e);
            }
        }
    }

    @Override
    public void processInvocation(final EJBClientInvocationContext clientInvocationContext, final EJBReceiverInvocationContext ejbReceiverInvocationContext) throws Exception {
        final ChannelAssociation channelAssociation = this.requireChannelAssociation(ejbReceiverInvocationContext.getEjbReceiverContext());
        final Channel channel = channelAssociation.getChannel();
        final MethodInvocationMessageWriter messageWriter = new MethodInvocationMessageWriter(this.clientProtocolVersion, this.marshallerFactory);
        final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
        final short invocationId = channelAssociation.getNextInvocationId();
        channelAssociation.receiveResponse(invocationId, ejbReceiverInvocationContext);

        try {
            messageWriter.writeMessage(dataOutputStream, invocationId, clientInvocationContext);
        } finally {
            dataOutputStream.close();
        }
    }

    @Override
    protected <T> StatefulEJBLocator<T> openSession(final EJBReceiverContext receiverContext, final Class<T> viewType, final String appName, final String moduleName, final String distinctName, final String beanName) throws IllegalArgumentException {
        final ChannelAssociation channelAssociation = this.requireChannelAssociation(receiverContext);
        final Channel channel = channelAssociation.getChannel();
        final SessionOpenRequestWriter sessionOpenRequestWriter = new SessionOpenRequestWriter(this.clientProtocolVersion);
        final DataOutputStream dataOutputStream;
        try {
            dataOutputStream = new DataOutputStream(channel.writeMessage());
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        final short invocationId = channelAssociation.getNextInvocationId();
        final Future<EJBReceiverInvocationContext.ResultProducer> futureResultProducer = channelAssociation.enrollForResult(invocationId);
        try {
            sessionOpenRequestWriter.writeMessage(dataOutputStream, invocationId, appName, moduleName, distinctName, beanName);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            try {
                dataOutputStream.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        final EJBReceiverInvocationContext.ResultProducer resultProducer;
        try {
            resultProducer = futureResultProducer.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final SessionOpenResponseHandler.SessionOpenResponse sessionOpenResponse;
        try {
            sessionOpenResponse = (SessionOpenResponseHandler.SessionOpenResponse) resultProducer.getResult();
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new StatefulEJBLocator<T>(viewType, appName, moduleName, beanName, distinctName, sessionOpenResponse.getSessionID(), sessionOpenResponse.getAffinity());
    }

    @Override
    public void verify(final String appName, final String moduleName, final String distinctName, final String beanName) throws Exception {
        // TODO: Implement
        logger.warn("Not yet implemented RemotingConnectionEJBReceiver#verify");
    }

    @Override
    protected void sendCommit(final EJBReceiverContext receiverContext, final TransactionID transactionID, final boolean onePhase) throws XAException {
        final ChannelAssociation channelAssociation = this.requireChannelAssociation(receiverContext);
        final short invocationId = channelAssociation.getNextInvocationId();
        final Channel channel = channelAssociation.getChannel();
        final Future<EJBReceiverInvocationContext.ResultProducer> futureResultProducer = channelAssociation.enrollForResult(invocationId);
        try {
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            final TransactionMessageWriter transactionMessageWriter = new TransactionMessageWriter();
            try {
                // write the tx commit message
                transactionMessageWriter.writeTxCommit(dataOutputStream, invocationId, transactionID, onePhase);
            } finally {
                dataOutputStream.close();
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Error sending transaction commit message", ioe);
        }
        try {
            // wait for the result
            final EJBReceiverInvocationContext.ResultProducer resultProducer = futureResultProducer.get();
            resultProducer.getResult();
        } catch (XAException xae) {
            throw xae;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void sendRollback(EJBReceiverContext receiverContext, TransactionID transactionID) throws XAException {
        final ChannelAssociation channelAssociation = this.requireChannelAssociation(receiverContext);
        final short invocationId = channelAssociation.getNextInvocationId();
        final Channel channel = channelAssociation.getChannel();
        final Future<EJBReceiverInvocationContext.ResultProducer> futureResultProducer = channelAssociation.enrollForResult(invocationId);
        try {
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            final TransactionMessageWriter transactionMessageWriter = new TransactionMessageWriter();
            try {
                // write the tx rollback message
                transactionMessageWriter.writeTxRollback(dataOutputStream, invocationId, transactionID);
            } finally {
                dataOutputStream.close();
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Error sending transaction rollback message", ioe);
        }
        try {
            // wait for the result
            final EJBReceiverInvocationContext.ResultProducer resultProducer = futureResultProducer.get();
            resultProducer.getResult();
        } catch (XAException xae) {
            throw xae;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected int sendPrepare(final EJBReceiverContext receiverContext, final TransactionID transactionID) throws XAException {
        final ChannelAssociation channelAssociation = this.requireChannelAssociation(receiverContext);
        final short invocationId = channelAssociation.getNextInvocationId();
        final Channel channel = channelAssociation.getChannel();
        final Future<EJBReceiverInvocationContext.ResultProducer> futureResultProducer = channelAssociation.enrollForResult(invocationId);
        try {
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            final TransactionMessageWriter transactionMessageWriter = new TransactionMessageWriter();
            try {
                // write the tx prepare message
                transactionMessageWriter.writeTxPrepare(dataOutputStream, invocationId, transactionID);
            } finally {
                dataOutputStream.close();
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Error sending transaction prepare message", ioe);
        }
        try {
            // wait for result
            final EJBReceiverInvocationContext.ResultProducer resultProducer = futureResultProducer.get();
            final Object result = resultProducer.getResult();
            if (result instanceof Integer) {
                return (Integer) result;
            }
            throw new RuntimeException("Unexpected result for transaction prepare: " + result);
        } catch (XAException xae) {
            throw xae;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void sendForget(final EJBReceiverContext receiverContext, final TransactionID transactionID) throws XAException {
        final ChannelAssociation channelAssociation = this.requireChannelAssociation(receiverContext);
        final short invocationId = channelAssociation.getNextInvocationId();
        final Channel channel = channelAssociation.getChannel();
        final Future<EJBReceiverInvocationContext.ResultProducer> futureResultProducer = channelAssociation.enrollForResult(invocationId);
        try {
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            final TransactionMessageWriter transactionMessageWriter = new TransactionMessageWriter();
            try {
                // write the tx forget message
                transactionMessageWriter.writeTxForget(dataOutputStream, invocationId, transactionID);
            } finally {
                dataOutputStream.close();
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Error sending transaction forget message", ioe);
        }
        try {
            // wait for result
            final EJBReceiverInvocationContext.ResultProducer resultProducer = futureResultProducer.get();
            resultProducer.getResult();
        } catch (XAException xae) {
            throw xae;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void beforeCompletion(final EJBReceiverContext receiverContext, final TransactionID transactionID) {
        final ChannelAssociation channelAssociation = this.requireChannelAssociation(receiverContext);
        final short invocationId = channelAssociation.getNextInvocationId();
        final Channel channel = channelAssociation.getChannel();
        final Future<EJBReceiverInvocationContext.ResultProducer> futureResultProducer = channelAssociation.enrollForResult(invocationId);
        try {
            final DataOutputStream dataOutputStream = new DataOutputStream(channel.writeMessage());
            final TransactionMessageWriter transactionMessageWriter = new TransactionMessageWriter();
            try {
                // write the beforeCompletion message
                transactionMessageWriter.writeTxBeforeCompletion(dataOutputStream, invocationId, transactionID);
            } finally {
                dataOutputStream.close();
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Error sending transaction beforeCompletion message", ioe);
        }
        try {
            // wait for result
            final EJBReceiverInvocationContext.ResultProducer resultProducer = futureResultProducer.get();
            resultProducer.getResult();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void moduleAvailable(final EJBReceiverContext receiverContext, final String appName, final String moduleName, final String distinctName) {
        logger.debug("Received module availability message for appName: " + appName + " moduleName: " + moduleName + " distinctName: " + distinctName + " for receiver context " + receiverContext);
        this.registerModule(appName, moduleName, distinctName);
        // notify of module availability if anyone's waiting on the latch
        final CountDownLatch moduleAvailabilityReportLatch;
        synchronized (this.moduleAvailabilityReportLatches) {
            moduleAvailabilityReportLatch = this.moduleAvailabilityReportLatches.remove(receiverContext);
        }
        if (moduleAvailabilityReportLatch != null) {
            moduleAvailabilityReportLatch.countDown();
        }
    }

    void moduleUnavailable(final EJBReceiverContext receiverContext, final String appName, final String moduleName, final String distinctName) {
        logger.debug("Received module un-availability message for appName: " + appName + " moduleName: " + moduleName + " distinctName: " + distinctName + " for receiver context " + receiverContext);
        this.deregisterModule(appName, moduleName, distinctName);
    }

    private ChannelAssociation requireChannelAssociation(final EJBReceiverContext ejbReceiverContext) {
        ChannelAssociation channelAssociation;
        synchronized (this.channelAssociations) {
            channelAssociation = this.channelAssociations.get(ejbReceiverContext);
        }
        if (channelAssociation == null) {
            throw new IllegalStateException("EJB communication channel " + EJB_CHANNEL_NAME + " is not yet ready to receive invocations (perhaps version handshake hasn't been completed), for receiver context " + ejbReceiverContext);
        }
        return channelAssociation;
    }

    @Override
    public String toString() {
        return this.cachedToString;
    }
}
