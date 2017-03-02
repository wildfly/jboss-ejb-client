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

import java.lang.reflect.InvocationTargetException;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.annotation.ClientInterceptorPriority;
import org.wildfly.common.math.HashMath;

final class EJBClientInterceptorInformation {
    static final EJBClientInterceptorInformation[] NO_INTERCEPTORS = new EJBClientInterceptorInformation[0];

    private static final ClassValue<EJBClientInterceptorInformation> CLASS_VALUE = new ClassValue<EJBClientInterceptorInformation>() {
        protected EJBClientInterceptorInformation computeValue(final Class<?> type) {
            final Class<? extends EJBClientInterceptor> subclass = type.asSubclass(EJBClientInterceptor.class);
            final ClientInterceptorPriority annotation = subclass.getAnnotation(ClientInterceptorPriority.class);
            final int priority = annotation != null ? annotation.value() : 0;
            final EJBClientInterceptor instance;
            try {
                instance = subclass.getConstructor().newInstance();
            } catch (InstantiationException | NoSuchMethodException e) {
                throw Logs.MAIN.noInterceptorConstructor(type);
            } catch (IllegalAccessException e) {
                throw Logs.MAIN.interceptorConstructorNotAccessible(type);
            } catch (InvocationTargetException e) {
                throw Logs.MAIN.interceptorConstructorFailed(type, e.getCause());
            }
            return new EJBClientInterceptorInformation(instance, priority);
        }
    };

    private final EJBClientInterceptor interceptorInstance;
    private final int priority;
    private EJBClientContext.InterceptorList singletonList;

    private EJBClientInterceptorInformation(final EJBClientInterceptor interceptorInstance, final int priority) {
        this.interceptorInstance = interceptorInstance;
        this.priority = priority;
    }

    EJBClientInterceptor getInterceptorInstance() {
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
        if (classInfo.interceptorInstance == interceptor) {
            return classInfo;
        } else {
            return new EJBClientInterceptorInformation(interceptor, classInfo.priority);
        }
    }

    EJBClientContext.InterceptorList getSingletonList() {
        final EJBClientContext.InterceptorList singletonList = this.singletonList;
        if (singletonList == null) {
            return this.singletonList = new EJBClientContext.InterceptorList(new EJBClientInterceptorInformation[]{this});
        }
        return singletonList;
    }
}
