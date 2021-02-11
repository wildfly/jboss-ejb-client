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

import static java.security.AccessController.doPrivileged;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.concurrent.Future;
import java.util.zip.Deflater;

import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

import org.jboss.ejb.client.annotation.ClientAsynchronous;
import org.jboss.ejb.client.annotation.ClientInterceptors;
import org.jboss.ejb.client.annotation.ClientTransaction;
import org.jboss.ejb.client.annotation.ClientTransactionPolicy;
import org.jboss.ejb.client.annotation.CompressionHint;
import org.jboss.ejb.client.annotation.Idempotent;

/**
 * Cached information about an EJB proxy.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class EJBProxyInformation<T> {

    static final boolean ENABLE_SCANNING = SecurityUtils.getBoolean(SystemProperties.VIEW_ANNOTATION_SCAN_ENABLED, true);

    static final Class<?>[] JUST_INV_HANDLER = new Class<?>[] { InvocationHandler.class };

    static final int MT_BUSINESS        = 0;
    static final int MT_EQUALS          = 1;
    static final int MT_HASH_CODE       = 2;
    static final int MT_TO_STRING       = 3;
    static final int MT_GET_PRIMARY_KEY = 4;
    static final int MT_GET_HANDLE      = 5;
    static final int MT_IS_IDENTICAL    = 6;
    static final int MT_GET_HOME_HANDLE = 7;

    private static final ClassValue<EJBProxyInformation<?>> PROXY_INFORMATION_CLASS_VALUE = new ClassValue<EJBProxyInformation<?>>() {
        protected EJBProxyInformation<?> computeValue(final Class<?> type) {
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                return doPrivileged((PrivilegedAction<EJBProxyInformation<?>>) () -> doCompute(type));
            } else {
                return doCompute(type);
            }
        }

        private <P> EJBProxyInformation<P> doCompute(final Class<P> type) {
            final Class<? extends P> proxyClass = Proxy.getProxyClass(type.getClassLoader(), type).asSubclass(type);
            final HashMap<Method, ProxyMethodInfo> fallbackMap = new HashMap<>();
            final IdentityHashMap<Method, ProxyMethodInfo> methodInfoMap = new IdentityHashMap<>();
            final HashMap<EJBMethodLocator, ProxyMethodInfo> methodLocatorMap = new HashMap<>();
            final CompressionHint classCompressionHint = ENABLE_SCANNING ? type.getAnnotation(CompressionHint.class) : null;
            final ClientTransaction classTransactionHint = ENABLE_SCANNING ? type.getAnnotation(ClientTransaction.class) : null;
            final ClientInterceptors classClientInterceptors = ENABLE_SCANNING ? type.getAnnotation(ClientInterceptors.class) : null;
            final EJBClientContext.InterceptorList classInterceptors = getInterceptorsFromAnnotation(classClientInterceptors);

            final int classCompressionLevel;
            final ProxyMethodInfo.CompressionHint classCompressRequest;
            final ProxyMethodInfo.CompressionHint classCompressResponse;
            if (classCompressionHint == null) {
                classCompressionLevel = -1;
                classCompressRequest = classCompressResponse = ProxyMethodInfo.CompressionHint.NONE;
            } else {
                classCompressionLevel = classCompressionHint.compressionLevel() == -1 ? Deflater.DEFAULT_COMPRESSION : classCompressionHint.compressionLevel();
                classCompressRequest = ProxyMethodInfo.CompressionHint.fromBoolean(classCompressionHint.compressRequest());
                classCompressResponse = ProxyMethodInfo.CompressionHint.fromBoolean(classCompressionHint.compressResponse());
            }
            final boolean classIdempotent = ENABLE_SCANNING && type.getAnnotation(Idempotent.class) != null;
            final boolean classAsync = ENABLE_SCANNING && type.getAnnotation(ClientAsynchronous.class) != null;
            final Field[] declaredFields = proxyClass.getDeclaredFields();
            for (Field declaredField : declaredFields) {
                declaredField.setAccessible(true);
                if (declaredField.getType() == Method.class && declaredField.getName().charAt(0) == 'm' && (declaredField.getModifiers() & Modifier.STATIC) != 0) {
                    // seems a likely match
                    try {
                        final Method method = (Method) declaredField.get(null);
                        final boolean alwaysAsync = method.getReturnType() == Future.class;
                        final boolean idempotent = classIdempotent || ENABLE_SCANNING && method.getAnnotation(Idempotent.class) != null;
                        final boolean clientAsync = alwaysAsync || classAsync || ENABLE_SCANNING && method.getAnnotation(ClientAsynchronous.class) != null;
                        final CompressionHint compressionHint = ENABLE_SCANNING ? method.getAnnotation(CompressionHint.class) : null;
                        final ClientTransaction transactionHint = ENABLE_SCANNING ? method.getAnnotation(ClientTransaction.class) : null;
                        final ClientInterceptors clientInterceptors = ENABLE_SCANNING ? method.getAnnotation(ClientInterceptors.class) : null;
                        final EJBClientContext.InterceptorList interceptors = getInterceptorsFromAnnotation(clientInterceptors);
                        final int compressionLevel;
                        final ProxyMethodInfo.CompressionHint compressRequest;
                        final ProxyMethodInfo.CompressionHint compressResponse;
                        final ClientTransactionPolicy transactionPolicy;
                        if (compressionHint == null) {
                            compressionLevel = classCompressionLevel;
                            compressRequest = classCompressRequest;
                            compressResponse = classCompressResponse;
                        } else {
                            compressionLevel = compressionHint.compressionLevel() == -1 ? Deflater.DEFAULT_COMPRESSION : compressionHint.compressionLevel();
                            compressRequest = ProxyMethodInfo.CompressionHint.fromBoolean(compressionHint.compressRequest());
                            compressResponse = ProxyMethodInfo.CompressionHint.fromBoolean(compressionHint.compressResponse());
                        }
                        transactionPolicy = transactionHint != null ? transactionHint.value() : clientAsync ? ClientTransactionPolicy.NOT_SUPPORTED : classTransactionHint != null ? classTransactionHint.value() : ClientTransactionPolicy.SUPPORTS;
                        // build the old signature format
                        final StringBuilder b = new StringBuilder();
                        final Class<?>[] methodParamTypes = method.getParameterTypes();
                        final String[] parameterTypeNames = new String[methodParamTypes.length];
                        if (parameterTypeNames.length > 0) {
                            b.append(parameterTypeNames[0] = methodParamTypes[0].getName());
                            for (int i = 1; i < methodParamTypes.length; i++) {
                                b.append(',');
                                b.append(parameterTypeNames[i] = methodParamTypes[i].getName());
                            }
                        }
                        final String methodName = method.getName();
                        final int methodType = getMethodType(type, methodName, methodParamTypes);
                        final EJBMethodLocator methodLocator = new EJBMethodLocator(methodName, parameterTypeNames);
                        final ProxyMethodInfo proxyMethodInfo = new ProxyMethodInfo(methodType, compressionLevel, compressRequest, compressResponse, idempotent, transactionPolicy, method, methodLocator, b.toString(), clientAsync, interceptors);
                        methodInfoMap.put(method, proxyMethodInfo);
                        fallbackMap.put(method, proxyMethodInfo);
                        methodLocatorMap.put(methodLocator, proxyMethodInfo);
                    } catch (IllegalAccessException e) {
                        throw new IllegalAccessError(e.getMessage());
                    }
                }
            }
            final Constructor<? extends P> constructor;
            try {
                constructor = proxyClass.getConstructor(JUST_INV_HANDLER);
            } catch (NoSuchMethodException e) {
                throw new NoSuchMethodError("No valid constructor found on proxy class");
            }
            return new EJBProxyInformation<>(proxyClass, constructor, methodInfoMap, fallbackMap, methodLocatorMap, classCompressionLevel, classIdempotent, classAsync, classInterceptors);
        }

        private EJBClientContext.InterceptorList getInterceptorsFromAnnotation(final ClientInterceptors classClientInterceptors) {
            if (classClientInterceptors != null) {
                final Class<?>[] interceptorClasses = classClientInterceptors.value();
                final int length = interceptorClasses.length;
                if (length == 0) {
                    return EJBClientContext.InterceptorList.EMPTY;
                } else if (length == 1) {
                    return EJBClientInterceptorInformation.forClass(interceptorClasses[0]).getSingletonList();
                } else {
                    final EJBClientInterceptorInformation[] interceptors = new EJBClientInterceptorInformation[length];
                    for (int i = 0; i < length; i++) {
                        interceptors[i] = EJBClientInterceptorInformation.forClass(interceptorClasses[i]);
                    }
                    return new EJBClientContext.InterceptorList(interceptors);
                }
            } else {
                return EJBClientContext.InterceptorList.EMPTY;
            }
        }

        private int getMethodType(final Class<?> interfaceClass, final String name, final Class<?>[] methodParamTypes) {
            switch (name) {
                case "equals": {
                    if (methodParamTypes.length == 1 && methodParamTypes[0] == Object.class) {
                        return MT_EQUALS;
                    }
                    break;
                }
                case "hashCode": {
                    if (methodParamTypes.length == 0) {
                        return MT_HASH_CODE;
                    }
                    break;
                }
                case "toString": {
                    if (methodParamTypes.length == 0) {
                        return MT_TO_STRING;
                    }
                    break;
                }
                case "getPrimaryKey": {
                    if (methodParamTypes.length == 0 && EJBObject.class.isAssignableFrom(interfaceClass)) {
                        return MT_GET_PRIMARY_KEY;
                    }
                    break;
                }
                case "getHandle": {
                    if (methodParamTypes.length == 0 && EJBObject.class.isAssignableFrom(interfaceClass)) {
                        return MT_GET_HANDLE;
                    }
                    break;
                }
                case "isIdentical": {
                    if (methodParamTypes.length == 1 && EJBObject.class.isAssignableFrom(interfaceClass) && methodParamTypes[0] == EJBObject.class) {
                        return MT_IS_IDENTICAL;
                    }
                    break;
                }
                case "getHomeHandle": {
                    if (methodParamTypes.length == 0 && EJBHome.class.isAssignableFrom(interfaceClass)) {
                        return MT_GET_HOME_HANDLE;
                    }
                    break;
                }
            }
            return MT_BUSINESS;
        }
    };

    EJBProxyInformation(final Class<? extends T> proxyClass, final Constructor<? extends T> proxyConstructor, final IdentityHashMap<Method, ProxyMethodInfo> methodInfoMap, final HashMap<Method, ProxyMethodInfo> fallbackMap, final HashMap<EJBMethodLocator, ProxyMethodInfo> methodLocatorMap, final int classCompressionHint, final boolean classIdempotent, final boolean classAsync, final EJBClientContext.InterceptorList classInterceptors) {
        this.proxyClass = proxyClass;
        this.proxyConstructor = proxyConstructor;
        this.methodInfoMap = methodInfoMap;
        this.fallbackMap = fallbackMap;
        this.methodLocatorMap = methodLocatorMap;
        this.classCompressionHint = classCompressionHint;
        this.classIdempotent = classIdempotent;
        this.classAsync = classAsync;
        this.classInterceptors = classInterceptors;
    }

    @SuppressWarnings("unchecked")
    static <T> EJBProxyInformation<T> forViewType(Class<T> clazz) {
        return (EJBProxyInformation<T>) PROXY_INFORMATION_CLASS_VALUE.get(clazz);
    }

    private final Class<? extends T> proxyClass;
    private final Constructor<? extends T> proxyConstructor;
    private final IdentityHashMap<Method, ProxyMethodInfo> methodInfoMap;
    private final HashMap<Method, ProxyMethodInfo> fallbackMap;
    private final HashMap<EJBMethodLocator, ProxyMethodInfo> methodLocatorMap;
    private final int classCompressionHint;
    private final boolean classIdempotent;
    private final boolean classAsync;
    private final EJBClientContext.InterceptorList classInterceptors;

    boolean hasCompressionHint(Method proxyMethod) {
        final ProxyMethodInfo proxyMethodInfo = getProxyMethodInfo(proxyMethod);
        return proxyMethodInfo != null && proxyMethodInfo.compressionLevel != -1;
    }

    boolean isIdempotent(Method proxyMethod) {
        final ProxyMethodInfo proxyMethodInfo = getProxyMethodInfo(proxyMethod);
        return proxyMethodInfo != null && (classIdempotent || proxyMethodInfo.idempotent);
    }

    Class<? extends T> getProxyClass() {
        return proxyClass;
    }

    Constructor<? extends T> getProxyConstructor() {
        return proxyConstructor;
    }

    int getClassCompressionHint() {
        return classCompressionHint;
    }

    boolean isClassIdempotent() {
        return classIdempotent;
    }

    boolean isClassAsync() {
        return classAsync;
    }

    ProxyMethodInfo getProxyMethodInfo(Method method) {
        final ProxyMethodInfo info = methodInfoMap.get(method);
        return info == null ? fallbackMap.get(method) : info;
    }

    ProxyMethodInfo getProxyMethodInfo(EJBMethodLocator locator) {
        return methodLocatorMap.get(locator);
    }

    EJBClientContext.InterceptorList getClassInterceptors() {
        return classInterceptors;
    }

    Collection<ProxyMethodInfo> getMethods() {
        return methodInfoMap.values();
    }

    static final class ProxyMethodInfo {

        enum CompressionHint {
            NONE, TRUE, FALSE;

            static CompressionHint fromBoolean(final boolean hint){
                return hint ? TRUE : FALSE;
            }
        }

        final int methodType;
        final int compressionLevel;
        final CompressionHint compressRequest;
        final CompressionHint compressResponse;
        final boolean idempotent;
        final ClientTransactionPolicy transactionPolicy;
        final Method method;
        final EJBMethodLocator methodLocator;
        final String signature;
        final boolean clientAsync;
        final EJBClientContext.InterceptorList interceptors;

        ProxyMethodInfo(final int methodType, final int compressionLevel, final CompressionHint compressRequest, final CompressionHint compressResponse, final boolean idempotent, final ClientTransactionPolicy transactionPolicy, final Method method, final EJBMethodLocator methodLocator, final String signature, final boolean clientAsync, final EJBClientContext.InterceptorList interceptors) {
            this.methodType = methodType;
            this.compressionLevel = compressionLevel;
            this.compressRequest = compressRequest;
            this.compressResponse = compressResponse;
            this.idempotent = idempotent;
            this.transactionPolicy = transactionPolicy;
            this.method = method;
            this.methodLocator = methodLocator;
            this.signature = signature;
            this.clientAsync = clientAsync;
            this.interceptors = interceptors;
        }

        public int getMethodType() {
            return methodType;
        }

        int getCompressionLevel() {
            return compressionLevel;
        }

        boolean isIdempotent() {
            return idempotent;
        }

        ClientTransactionPolicy getTransactionPolicy() {
            return transactionPolicy;
        }

        Method getMethod() {
            return method;
        }

        String getSignature() {
            return signature;
        }

        boolean isClientAsync() {
            return clientAsync;
        }

        CompressionHint getCompressRequestHint() {
            return compressRequest;
        }

        CompressionHint getCompressResponseHint() {
            return compressResponse;
        }

        EJBMethodLocator getMethodLocator() {
            return methodLocator;
        }

        EJBClientContext.InterceptorList getInterceptors() {
            return interceptors;
        }

        boolean isSynchronous() {
            final Class<?> returnType = method.getReturnType();
            return returnType != void.class && returnType != Future.class;
        }
    }
}
