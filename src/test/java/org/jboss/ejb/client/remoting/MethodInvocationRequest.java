/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
