/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client;

import static java.lang.Integer.signum;
import static org.jboss.ejb.client.EJBProxyInformation.ENABLE_SCANNING;

import java.lang.reflect.InvocationTargetException;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientInterceptorPriority;
import org.wildfly.common.math.HashMath;

final class EJBClientInterceptorInformation implements Comparable<EJBClientInterceptorInformation> {
    static final EJBClientInterceptorInformation[] NO_INTERCEPTORS = new EJBClientInterceptorInformation[0];

    private static final ClassValue<EJBClientInterceptorInformation> CLASS_VALUE = new ClassValue<EJBClientInterceptorInformation>() {
        protected EJBClientInterceptorInformation computeValue(final Class<?> type) {
            final Class<? extends EJBClientInterceptor> subclass = type.asSubclass(EJBClientInterceptor.class);
            final ClientInterceptorPriority annotation = ENABLE_SCANNING ? subclass.getAnnotation(ClientInterceptorPriority.class) : null;
            final int priority = annotation != null ? annotation.value() : 0;
            return new EJBClientInterceptorInformation(subclass, priority);
        }
    };

    private volatile EJBClientInterceptor interceptorInstance;
    private final Class<? extends EJBClientInterceptor> interceptorClass;
    private final int priority;
    private EJBClientContext.InterceptorList singletonList;

    private EJBClientInterceptorInformation(final EJBClientInterceptor interceptorInstance, final int priority) {
        this.interceptorInstance = interceptorInstance;
        this.interceptorClass = interceptorInstance.getClass();
        this.priority = priority;
    }

    private EJBClientInterceptorInformation(final Class<? extends EJBClientInterceptor> interceptorClass, final int priority) {
        this.interceptorClass = interceptorClass;
        this.priority = priority;
    }

    EJBClientInterceptor getInterceptorInstance() {
        EJBClientInterceptor interceptorInstance = this.interceptorInstance;
        if (interceptorInstance == null) {
            synchronized (this) {
                interceptorInstance = this.interceptorInstance;
                if (interceptorInstance == null) {
                    final Class<? extends EJBClientInterceptor> type = this.interceptorClass;
                    try {
                        interceptorInstance = type.getConstructor().newInstance();
                    } catch (InstantiationException | NoSuchMethodException e) {
                        throw Logs.MAIN.noInterceptorConstructor(type);
                    } catch (IllegalAccessException e) {
                        throw Logs.MAIN.interceptorConstructorNotAccessible(type);
                    } catch (InvocationTargetException e) {
                        throw Logs.MAIN.interceptorConstructorFailed(type, e.getCause());
                    }
                }
                this.interceptorInstance = interceptorInstance;
            }
        }
        return interceptorInstance;
    }

    int getPriority() {
        return priority;
    }

    public int hashCode() {
        return HashMath.multiHashOrdered(System.identityHashCode(interceptorInstance), priority);
    }

    static EJBClientInterceptorInformation forClass(final Class<?> interceptorClass) {
        return CLASS_VALUE.get(interceptorClass);
    }

    static EJBClientInterceptorInformation forInstance(final EJBClientInterceptor interceptor) {
        final EJBClientInterceptorInformation classInfo = CLASS_VALUE.get(interceptor.getClass());
        try {
            if (classInfo.getInterceptorInstance() == interceptor) {
                return classInfo;
            }
        } catch (Exception ignored) {}
        return new EJBClientInterceptorInformation(interceptor, classInfo.priority);
    }

    EJBClientContext.InterceptorList getSingletonList() {
        final EJBClientContext.InterceptorList singletonList = this.singletonList;
        if (singletonList == null) {
            return this.singletonList = new EJBClientContext.InterceptorList(new EJBClientInterceptorInformation[]{this});
        }
        return singletonList;
    }

    public int compareTo(final EJBClientInterceptorInformation o) {
        return signum(priority - o.priority);
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("EJBClientInterceptorInformation(class=");
        buffer.append(interceptorClass);
        buffer.append(", priority=");
        buffer.append(priority);
        buffer.append(")");
        return buffer.toString();
    }
}
