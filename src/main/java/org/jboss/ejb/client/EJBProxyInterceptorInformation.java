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

import static java.util.Collections.emptyMap;
import static org.jboss.ejb.client.EJBClientContext.InterceptorList.EMPTY;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.function.Function;

import org.jboss.ejb.client.EJBClientContext.InterceptorList;

final class EJBProxyInterceptorInformation<T> {
    private final EJBProxyInformation<T> proxyInformation;
    private final IdentityHashMap<Method, InterceptorList> interceptorsByMethod;
    private final HashMap<Method, InterceptorList> interceptorsByMethodFallback;
    private final InterceptorList classInterceptors;

    private EJBProxyInterceptorInformation(final EJBProxyInformation<T> proxyInformation, final IdentityHashMap<Method, InterceptorList> interceptorsByMethod, final InterceptorList classInterceptors) {
        this.proxyInformation = proxyInformation;
        this.interceptorsByMethod = interceptorsByMethod;
        this.interceptorsByMethodFallback = new HashMap<>(interceptorsByMethod);
        this.classInterceptors = classInterceptors;
    }

    static <T> EJBProxyInterceptorInformation<T> construct(Class<T> clazz, EJBClientContext clientContext) {
        final EJBProxyInformation<T> proxyInformation = EJBProxyInformation.forViewType(clazz);
        final Collection<EJBProxyInformation.ProxyMethodInfo> methods = proxyInformation.getMethods();
        final String className = clazz.getName();
        final InterceptorList list0 = EJBClientContext.defaultInterceptors;
        final InterceptorList list1 = clientContext.getGlobalInterceptors();
        final InterceptorList list2 = clientContext.getClassPathInterceptors();
        final InterceptorList list3 = clientContext.getConfiguredPerClassInterceptors().getOrDefault(className, EMPTY);
        final InterceptorList list5 = proxyInformation.getClassInterceptors();
        final IdentityHashMap<Method, InterceptorList> interceptorsByMethod = new IdentityHashMap<>(methods.size());
        final HashMap<InterceptorList, InterceptorList> cache = new HashMap<>();
        cache.computeIfAbsent(list0, Function.identity());
        final InterceptorList tailList = list3.combine(list2).combine(list1).combine(list0);
        cache.computeIfAbsent(tailList, Function.identity());
        for (EJBProxyInformation.ProxyMethodInfo method : methods) {
            // compile interceptor information
            final InterceptorList list4 = clientContext.getConfiguredPerMethodInterceptors().getOrDefault(className, emptyMap()).getOrDefault(method.getMethodLocator(), EMPTY);
            final InterceptorList list6 = method.getInterceptors();
            interceptorsByMethod.put(method.getMethod(), cache.computeIfAbsent(list6.combine(list5).combine(list4).combine(tailList), Function.identity()));
        }
        return new EJBProxyInterceptorInformation<T>(proxyInformation, interceptorsByMethod, cache.computeIfAbsent(list5.combine(tailList), Function.identity()));
    }

    EJBProxyInformation<T> getProxyInformation() {
        return proxyInformation;
    }

    InterceptorList getInterceptors(Method method) {
        final InterceptorList list = interceptorsByMethod.get(method);
        return list == null ? interceptorsByMethodFallback.get(method) : list;
    }

    InterceptorList getClassInterceptors() {
        return classInterceptors;
    }
}
