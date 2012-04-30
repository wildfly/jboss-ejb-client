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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Cause;
import org.jboss.logging.LogMessage;
import org.jboss.logging.Logger;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;
import org.jboss.remoting3.Channel;

import static org.jboss.logging.Logger.Level.*;

/**
 * Primary logging for the main EJB client API.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "EJBCLIENT")
public interface Logs extends BasicLogger {
    Logs MAIN = Logger.getMessageLogger(Logs.class, "org.jboss.ejb.client");
    Logs REMOTING = Logger.getMessageLogger(Logs.class, "org.jboss.ejb.client.remoting");
    Logs TXN = Logger.getMessageLogger(Logs.class, "org.jboss.ejb.client.txn");

    // Greeting

    @LogMessage(level = INFO)
    @Message("JBoss EJB Client version %s")
    void greeting(String version);

    // Argument errors

    @Message(id = 0, value = "Module name cannot be null or empty")
    IllegalArgumentException emptyModuleName();

    @Message(id = 1, value = "Bean name cannot be null or empty")
    IllegalArgumentException emptyBeanName();

    @Message(id = 2, value = "Bean interface type cannot be null")
    IllegalArgumentException nullViewType();

    @LogMessage(level = INFO)
    @Message(id = 3, value = "Incorrect max-allowed-connected-nodes value %s specified for cluster named %s. Defaulting to %s")
    void incorrectMaxAllowedConnectedNodesValueForCluster(final String value, final String clusterName, final String fallbackDefaultValue);

    @LogMessage(level = INFO)
    @Message(id = 4, value = "Incorrect connection timeout value %s specified for cluster named %s. Defaulting to %s")
    void incorrectConnectionTimeoutValueForCluster(final String value, final String clusterName, final String fallbackDefaultValue);

    @LogMessage(level = INFO)
    @Message(id = 5, value = "Incorrect connection timeout value %s specified for node %s in cluster named %s. Defaulting to %s")
    void incorrectConnectionTimeoutValueForNodeInCluster(final String value, final String nodeName, final String clusterName, final String fallbackDefaultValue);

    @LogMessage(level = INFO)
    @Message(id = 6, value = "No host/port configured for connection named %s. Skipping connection creation")
    void skippingConnectionCreationDueToMissingHostOrPort(final String connectionName);

    @LogMessage(level = INFO)
    @Message(id = 7, value = "Incorrect port value %s specified for connection named %s. Skipping connection creation")
    void skippingConnectionCreationDueToInvalidPortNumber(final String port, final String connectionName);

    @LogMessage(level = INFO)
    @Message(id = 8, value = "Incorrect connection timeout value %s specified for connection named %s. Defaulting to %s")
    void incorrectConnectionTimeoutValueForConnection(final String value, final String connectionName, final String fallbackDefaultValue);

    @LogMessage(level = INFO)
    @Message(id = 9, value = "Incorrect invocation timeout value %s specified. Defaulting to %s")
    void incorrectInvocationTimeoutValue(final String value, final String fallbackDefaultValue);

    @LogMessage(level = INFO)
    @Message(id = 10, value = "Incorrect reconnect tasks timeout value %s specified. Defaulting to %s")
    void incorrectReconnectTasksTimeoutValue(final String value, final String fallbackDefaultValue);

    @LogMessage(level = INFO)
    @Message(id = 11, value = "Discarding result for invocation id %s since no waiting context found")
    void discardingInvocationResult(final short invocationId);

    @LogMessage(level = INFO)
    @Message(id = 12, value = "Cannot create a EJB receiver for %s since there was no match for a target destination")
    void cannotCreateEJBReceiverDueToUnknownTarget(final String clusterNode);

    @LogMessage(level = INFO)
    @Message(id = 13, value = "Successful version handshake completed for receiver context %s on channel %s")
    void successfulVersionHandshake(final EJBReceiverContext receiverContext, final Channel channel);

    @LogMessage(level = INFO)
    @Message(id = 14, value = "Version handshake not completed for receiver context %s. Closing receiver context")
    void versionHandshakeNotCompleted(final EJBReceiverContext receiverContext);

    @LogMessage(level = INFO)
    @Message(id = 15, value = "Initial module availability report for %s wasn't received during the receiver context association")
    void initialModuleAvailabilityReportNotReceived(final EJBReceiver ejbReceiver);

    @LogMessage(level = INFO)
    @Message(id = 16, value = "Channel %s can no longer process messages")
    void channelCanNoLongerProcessMessages(final Channel channel);

    @LogMessage(level = INFO)
    @Message(id = 17, value = "Received server version %s and marshalling strategies %s")
    void receivedServerVersionAndMarshallingStrategies(final String version, final String marshallingStrategies);

    // Proxy API errors

    @Message(id = 100, value = "Object '%s' is not a valid proxy object")
    IllegalArgumentException unknownProxy(Object proxy);

    @Message(id = 101, value = "Proxy object '%s' was not generated by %s")
    IllegalArgumentException proxyNotOurs(Object proxy, String className);

    @Message(id = 102, value = "No asynchronous operation in progress")
    IllegalStateException noAsyncInProgress();

    // Invocation result exceptions

    @Message(id = 400, value = "Remote invocation failed due to an exception")
    ExecutionException remoteInvFailed(@Cause Throwable cause);

    @Message(id = 401, value = "Result was discarded (one-way invocation)")
    IllegalStateException oneWayInvocation();

    @Message(id = 402, value = "Remote invocation request was cancelled")
    CancellationException requestCancelled();

    @Message(id = 403, value = "Timed out")
    TimeoutException timedOut();
}
