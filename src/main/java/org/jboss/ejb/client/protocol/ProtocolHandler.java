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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * User: jpai
 */
public interface ProtocolHandler {

    void writeVersionMessage(final DataOutput output, final String marshallerType) throws IOException;

    void writeSessionOpenRequest(final DataOutput output, final short invocationId, final String appName,
                                 final String moduleName, final String beanName, final String viewClassName,
                                 final Attachment[] attachments) throws IOException;

    void writeSessionOpenResponse(final DataOutput output, final short invocationId, final byte[] sessionId,
                                  final Attachment[] attachments) throws IOException;

    void writeMethodInvocationRequest(final DataOutput output, final short invocationId, final String appName,
                                      final String moduleName, final String beanName, final String viewClassName,
                                      final Method method, final Object[] methodParams,
                                      final Attachment[] attachments) throws IOException;

    void writeInvocationCancelRequest(final DataOutput output, final short invocationId) throws IOException;

    void writeMethodInvocationResponse(final DataOutput output, final short invocationId, final Object result,
                                       final Throwable error, final Attachment[] attachments) throws IOException;


    MessageType getMessageType(final DataInput input) throws IOException;

    //MethodInvocationRequest readMethodInvocationRequest(final DataInput input, final EJBViewResolver ejbViewResolver) throws IOException;

    MethodInvocationResponse readMethodInvocationResponse(final DataInput input) throws IOException;
}
