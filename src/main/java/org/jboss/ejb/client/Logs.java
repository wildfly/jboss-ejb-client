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

import org.jboss.logging.BasicLogger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.remoting3.Channel;

import javax.naming.NamingException;
import javax.transaction.NotSupportedException;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.rmi.UnmarshalException;

import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

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
    @Message(id = 17, value = "Received server version %d and marshalling strategies %s")
    void receivedServerVersionAndMarshallingStrategies(final int version, final Set<String> marshallingStrategies);

    @Message(id = 18, value = "%s cannot be null")
    IllegalArgumentException paramCannotBeNull(final String paramName);

    @Message(id = 19, value = "Node name cannot be null or empty string, while adding a node to cluster named %s")
    IllegalArgumentException nodeNameCannotBeNullOrEmptyStringForCluster(final String clusterName);

    @Message(id = 20, value = "%s cannot be null or empty string")
    IllegalArgumentException paramCannotBeNullOrEmptyString(final String paramName);

    @Message(id = 21, value = "EJB client context selector may not be changed")
    SecurityException ejbClientContextSelectorMayNotBeChanged();

    @Message(id = 22, value = "No EJB client context is available")
    IllegalStateException noEJBClientContextAvailable();

    @Message(id = 23, value = "EJB client interceptor %s is already registered")
    IllegalArgumentException ejbClientInterceptorAlreadyRegistered(final EJBClientInterceptor interceptor);

    @Message(id = 24, value = "No EJB receiver available for handling [appName:%s, moduleName:%s, distinctName:%s] combination")
    IllegalStateException noEJBReceiverAvailableForDeployment(final String appName, final String moduleName, final String distinctName);

    @Message(id = 25, value = "No EJB receiver available for handling [appName:%s, moduleName:%s, distinctName:%s] combination for invocation context %s")
    IllegalStateException noEJBReceiverAvailableForDeploymentDuringInvocation(final String appName, final String moduleName, final String distinctName, final EJBClientInvocationContext invocationContext);

    @Message(id = 26, value = "%s has not been associated with %s")
    IllegalStateException receiverNotAssociatedWithClientContext(final EJBReceiver receiver, final EJBClientContext clientContext);

    @Message(id = 27, value = "No EJBReceiver available for node name %s")
    IllegalStateException noEJBReceiverForNode(final String nodeName);

    @Message(id = 28, value = "No EJB receiver contexts available in cluster %s")
    IllegalStateException noReceiverContextsInCluster(final String clusterName);

    @Message(id = 29, value = "No cluster context available for cluster named %s")
    IllegalStateException noClusterContextAvailable(final String clusterName);

    @Message(id = 30, value = "sendRequest() called during wrong phase")
    IllegalStateException sendRequestCalledDuringWrongPhase();

    @Message(id = 31, value = "No receiver associated with invocation")
    IllegalStateException noReceiverAssociatedWithInvocation();

    @Message(id = 32, value = "Cannot retry a request which hasn't previously been completed")
    IllegalStateException cannotRetryRequest();

    @Message(id = 33, value = "getResult() called during wrong phase")
    IllegalStateException getResultCalledDuringWrongPhase();

    @Message(id = 34, value = "discardResult() called during wrong phase")
    IllegalStateException discardResultCalledDuringWrongPhase();

    @Message(id = 35, value = "Not supported")
    NamingException unsupportedNamingOperation();

    @Message(id = 36, value = "Read only naming context, operation not supported")
    NamingException unsupportedNamingOperationForReadOnlyContext();

    @Message(id = 37, value = "Could not load ejb proxy class %s")
    NamingException couldNotLoadProxyClass(final String viewClassName);

    @Message(id = 38, value = "Transaction enlistment did not yield a transaction ID")
    IllegalStateException txEnlistmentDidNotYieldTxId();

    @Message(id = 39, value = "Cannot enlist transaction")
    IllegalStateException cannotEnlistTx();

    @Message(id = 40, value = "EJB communication channel %s is not yet ready to receive invocations (perhaps version handshake hasn't been completed), for receiver context %s")
    IllegalStateException channelNotReadyForCommunication(final String channelName, final EJBReceiverContext receiverContext);

    @Message(id = 41, value = "A session bean does not have a primary key class")
    RuntimeException primaryKeyNotRelevantForSessionBeans();

    @Message(id = 42, value = "Failed to find EJB client configuration file specified in %s system property")
    RuntimeException failedToFindEjbClientConfigFileSpecifiedBySysProp(@Cause Exception e, final String sysPropName);

    @Message(id = 43, value = "Error reading EJB client properties file %s")
    RuntimeException failedToReadEjbClientConfigFile(@Cause Exception e, String file);

    @Message(id = 44, value = "No transaction context available")
    IllegalStateException noTxContextAvailable();

    @Message(id = 45, value = "User transactions not supported by this context")
    IllegalStateException userTxNotSupportedByTxContext();

    @Message(id = 46, value = "A transaction is already associated with this thread")
    NotSupportedException txAlreadyAssociatedWithThread();

    @Message(id = 47, value = "A transaction is not associated with this thread")
    IllegalStateException noTxAssociatedWithThread();

    @Message(id = 48, value = "Transaction for this thread is not active")
    IllegalStateException txNotActiveForThread();

    @Message(id = 49, value = "Cannot proceed with invocation since transaction is pinned to node %s which has been excluded from handling invocation for the current invocation context %s")
    IllegalStateException txNodeIsExcludedForInvocation(String nodeName, EJBClientInvocationContext invocationContext);

    @Message(id = 50, value = "Node of the current transaction %s does not accept %s")
    IllegalStateException nodeDoesNotAcceptLocator(String nodeName, EJBLocator<?> locator);

    @Message(id = 51, value = "Cannot proceed with invocation since the locator %s has an affinity on node %s which has been excluded from current invocation context %s")
    IllegalStateException requiredNodeExcludedFromInvocation(EJBLocator<?> locator, String nodeName, EJBClientInvocationContext invocationContext);

    @Message(id = 52, value = "%s for cluster %s is not of type org.jboss.ejb.client.ClusterNodeSelector")
    RuntimeException unexpectedClusterNodeSelectorClassType(Class<?> nodeSelectorClass, String clusterName);

    @Message(id = 53, value = "Could not create the cluster node selector for cluster %s")
    RuntimeException couldNotCreateClusterNodeSelector(@Cause Exception e, String clusterName);

    @Message(id = 54, value = "Cannot specify both a callback handler and a username/password")
    IllegalStateException cannotSpecifyBothCallbackHandlerAndUserPass();

    @Message(id = 55, value = "Could not decode base64 encoded password")
    RuntimeException couldNotDecodeBase64Password(@Cause Exception e);

    @Message(id = 56, value = "Cannot specify both a plain text and base64 encoded password")
    IllegalStateException cannotSpecifyBothPlainTextAndEncodedPassword();

    @Message(id = 57, value = "%s not of type org.jboss.ejb.client.DeploymentNodeSelector")
    RuntimeException unexpectedDeploymentNodeSelectorClassType(Class<?> deploymentNodeSelector);

    @Message(id = 58, value = "Could not create the deployment node selector")
    RuntimeException couldNotCreateDeploymentNodeSelector(@Cause Exception e);

    @LogMessage(level = WARN)
    @Message(id = 59, value = "Could not send a message over remoting channel, to cancel invocation for invocation id %s")
    void failedToSendInvocationCancellationMessage(short invocationId, @Cause Exception e);

    @Message(id = 60, value = "Failed to create scoped EJB client context")
    RuntimeException failedToCreateScopedEjbClientContext(@Cause Exception e);

    @LogMessage(level = WARN)
    @Message(id = 61, value = "Cannot send a transaction recovery message to the server since the protocol version of EJBReceiver %s doesn't support it")
    void transactionRecoveryMessageNotSupported(EJBReceiver receiver);

    @LogMessage(level = INFO)
    @Message(id = 62, value = "Incorrect Reconnection interval value %s specified. Defaulting to %s")
    void incorrectConnectionCreationDueToInvalidReconnectIntervalNumber(final String taskInterval, final String defaultTaskInterval);

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

    @Message(id = 404, value = "Operation not allowed since this EJB client context %s has been closed")
    IllegalStateException ejbClientContextIsClosed(EJBClientContext ejbClientContext);

    @Message(id = 405, value = "An EJB client context is already registered for EJB client context identifier %s")
    IllegalStateException ejbClientContextAlreadyRegisteredForIdentifier(EJBClientContextIdentifier identifier);

    @LogMessage(level = INFO)
    @Message(id = 406, value = "Unexpected exception when discarding invocation result")
    void exceptionOnDiscardResult(@Cause IOException exception);

    @Message(id = 407, value = "Issue regarding unmarshalling of EJB parameters (possible Out of Memory issue).")
    UnmarshalException ejbClientInvocationParamsException(@Cause Exception e);
}
