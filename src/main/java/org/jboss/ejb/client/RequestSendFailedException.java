package org.jboss.ejb.client;

/**
 * An exception (typically) thrown by {@link EJBReceiver}s if the receiver couldn't successfully handle a request.
 *
 * @author: Jaikiran Pai
 */
public class RequestSendFailedException extends RuntimeException {

    /**
     * The node name of the EJB receiver which failed to handle the request
     */
    private final String failedNodeName;

    /**
     * @param failedNodeName The node name of the EJB receiver which failed to handle the request
     * @param failureMessage The exception message
     * @param cause          The exception which caused this failure
     */
    public RequestSendFailedException(final String failedNodeName, final String failureMessage, final Throwable cause) {
        super(failureMessage, cause);
        this.failedNodeName = failedNodeName;
    }

    /**
     * Returns the node name of the EJB receiver which failed to handle the request
     *
     * @return
     */
    String getFailedNodeName() {
        return this.failedNodeName;
    }
}
