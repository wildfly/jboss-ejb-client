/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.ejb.client.proxy;

import org.jboss.ejb.client.ChannelCommunicator;
import org.jboss.ejb.client.protocol.InvocationRequest;
import org.jboss.ejb.client.protocol.InvocationResponse;
import org.jboss.remoting3.Endpoint;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class RemoteInvocationHandler implements InvocationHandler {

    private final String appName;
    private final String moduleName;
    private final String beanName;
    private final Class<?> view;
    private volatile short invocationId = 0;
    private final ChannelCommunicator channelCommunicator;

    public RemoteInvocationHandler(final Endpoint endpoint, final URI uri, final String appName, final String moduleName, final String beanName, final Class<?> view) {
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.view = view;
        this.channelCommunicator = new ChannelCommunicator(endpoint, uri, view.getClassLoader());
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final InvocationRequest invocationRequest = new InvocationRequest(this.invocationId++, this.appName,
                this.moduleName, this.beanName,
                this.view.getName(), method.getName(), method.getParameterTypes(), args, null);

        final Future<InvocationResponse> result = this.channelCommunicator.invoke(invocationRequest);
        // TODO: Externalize timeout
        final InvocationResponse response = result.get(20, TimeUnit.SECONDS);
        if (response.isException()) {
            throw response.getException();
        }
        return response.getResult();
    }

    private String[] toString(final Class[] classTypes) {
        final String[] types = new String[classTypes.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = classTypes[i].getName().toString();
        }
        return types;
    }
}
