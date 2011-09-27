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

package org.jboss.ejb.client.protocol;

import org.jboss.ejb.client.ModuleID;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * A {@link ClientProtocolHandler} is responsible for writing out the underlying raw messages that need to be sent
 * to the server and for reading the incoming messages from the server. The {@link ClientProtocolHandler}
 * with the help of {@link org.jboss.ejb.client.protocol.marshalling.Marshaller} and {@link org.jboss.ejb.client.protocol.marshalling.UnMarshaller}
 * is the sole authority (on the client side) while dealing with read/write opeartions into the data stream(s) which will be used for communication
 * between the client and the server
 * <p/>
 * User: Jaikiran Pai
 */
public interface ClientProtocolHandler {

    /**
     * Writes out a version message packet to the passed {@link DataOutput output}. The version message written
     * out will include the version number of the protocol which this {@link ClientProtocolHandler} can handle and also
     * the passed <code>marshallerType</code>. This version message is supposed to be sent from the client to the
     * server to establish a contract for the subsequent communication between the server and the client
     *
     * @param output         The {@link DataOutput} to which the version message has to be written
     * @param marshallerType The type of marshaller that the protocol handler will use for marshalling/unmarshalling
     *                       data while writting/reading from the streams
     * @throws IOException If there's a problem while writing out the message
     */
    void writeVersionMessage(final DataOutput output, final String marshallerType) throws IOException;

    /**
     * Writes out a session open request into the passed {@link DataOutput output}. The session open request
     * will contain the EJB view identifiers and the invocation id, along with any attachments.
     * <p/>
     * The client will  use to this method to send a session open request to the server. A session open request is
     * applicable for  stateful session beans identified by the passed app, module, bean name.
     *
     * @param output        The {@link DataOutput} to which the message has to be written
     * @param invocationId  The invocation id to be associated with this messsage
     * @param appName       The app name used to identify the EJB
     * @param moduleName    The module name used to identify the EJB
     * @param beanName      The name of the bean
     * @param viewClassName The fully qualified classname of the remote view of the EJB
     * @param attachments   Any attachments which need to be passed along with the message. Can be null.
     * @throws IOException If there's a problem while writing out the messsage
     */
    void writeSessionOpenRequest(final DataOutput output, final short invocationId, final String appName,
                                 final String moduleName, final String beanName, final String viewClassName,
                                 final Attachment[] attachments) throws IOException;

    /**
     * Writes out a invocation request messsage for a method of the remote view of a EJB, into the {@link DataOutput output}.
     * The message will contain an <code>invocationId</code> and the EJB view identifiers, along with the method name
     * and parameters to be passed to the method during invocation.
     * <p/>
     * The client uses this method to invoke a EJB method exposed by a remote view of the EJB on the server.
     *
     * @param output        The {@link DataOutput} to which the message has to be written
     * @param invocationId  The invocation id to be associated with this message
     * @param appName       The app name used to identify the EJB
     * @param moduleName    The module name used to identify the EJB
     * @param beanName      The EJB name
     * @param viewClassName The fully qualified class name of the remote view of the EJB
     * @param method        The method to be invoked on the remote view of the bean
     * @param methodParams  The parameters to be passed to the EJB method upon invocation
     * @param attachments   Any attachments that need to be passed along with the message. Can be null.
     * @throws IOException If there's a problem while writing out the messsage
     */
    void writeMethodInvocationRequest(final DataOutput output, final short invocationId, final String appName,
                                      final String moduleName, final String beanName, final String viewClassName,
                                      final Method method, final Object[] methodParams,
                                      final Attachment[] attachments) throws IOException;

    /**
     * Writes out a messsage into the {@link DataOutput output} to cancel a (previous) invocation request. The
     * written message will contain the invocation id corresponding to the invocation which needs to be cancelled.
     * <p/>
     * s
     * The client sends this message to the server to indicate that the it wants an invocation request associated
     * with the <code>invocationId</code> to be cancelled.
     *
     * @param output       The {@link DataOutput} to which the message has to be written
     * @param invocationId The invocation id corresponding to the invocation to be cancelled
     * @throws IOException If there's a problem while writing out the message
     */
    void writeInvocationCancelRequest(final DataOutput output, final short invocationId) throws IOException;

    /**
     * Returns the {@link MessageType} associated with the data contained in the {@link DataInput input}. Each {@link MessageType}
     * has an unique byte header assoicated with this. This method looks for the byte header to identify the {@link MessageType}
     *
     * @param input The {@link DataInput} from which the message type has to be identified
     * @return The {@link MessageType} associated with the message in the {@link DataInput input}
     * @throws IOException If there's a problem while reading the message in the {@link DataInput} or if the byte header in the
     *                     {@link DataInput input} doesn't correspond to any known {@link MessageType}s
     */
    MessageType getMessageType(final DataInput input) throws IOException;


    /**
     * Reads the {@link ModuleID}s from the passed {@link DataInput input}. The server sends a message containing
     * the EJB module available on the server and the client uses this method to get that information
     *
     * @param input The {@link DataInput} from which the message has to be read
     * @return Returns the {@link ModuleID}s
     * @throws IOException If there's a problem reading the {@link DataInput input}
     */
    ModuleID[] readModuleAvailability(final DataInput input) throws IOException;
}
