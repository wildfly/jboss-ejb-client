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
package org.jboss.ejb.client.remoting;

import java.util.Map;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class MethodInvocationRequest {

    private final short invocationId;
    private final String appName;
    private final String moduleName;
    private final String beanName;
    private final String viewClassName;
    private final String methodName;
    private final String[] paramTypes;
    private final Object[] params;
    private final String distinctName;
    private final Map<String, Object> attachments;

    public MethodInvocationRequest(final short invocationId, final String appName, final String moduleName,
                                   final String distinctName, final String beanName, final String viewClassName,
                                   final String methodName, final String[] methodParamTypes,
                                   final Object[] methodParams, final Map<String, Object> attachments) {

        this.invocationId = invocationId;
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.viewClassName = viewClassName;
        this.methodName = methodName;
        this.params = methodParams;
        this.attachments = attachments;
        this.paramTypes = methodParamTypes;
        this.distinctName = distinctName;

    }

    public short getInvocationId() {
        return invocationId;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getViewClassName() {
        return this.viewClassName;
    }

    public Object[] getParams() {
        return params;
    }

    public String[] getParamTypes() {
        return this.paramTypes;
    }

    public Map<String, Object> getAttachments() {
        return attachments;
    }

    public String getAppName() {
        return this.appName;
    }

    public String getModuleName() {
        return this.moduleName;
    }

    public String getBeanName() {
        return this.beanName;
    }

    public String getDistinctName() {
        return this.distinctName;
    }

}
