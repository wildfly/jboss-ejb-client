/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client;

import static java.security.AccessController.doPrivileged;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.PrivilegedAction;
import java.util.IdentityHashMap;
import java.util.zip.Deflater;

import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

import org.jboss.ejb.client.annotation.ClientAsynchronous;
import org.jboss.ejb.client.annotation.CompressionHint;
import org.jboss.ejb.client.annotation.Idempotent;

/**
 * Cached information about an EJB proxy.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class EJBProxyInformation<T> {

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
                return doPrivileged(new PrivilegedAction<EJBProxyInformation<?>>() {
                    public EJBProxyInformation<?> run() {
                        return doCompute(type);
                    }
                });
            } else {
                return doCompute(type);
            }
        }

        private <P> EJBProxyInformation<P> doCompute(final Class<P> type) {
            final Class<? extends P> proxyClass = Proxy.getProxyClass(type.getClassLoader(), type).asSubclass(type);
            final IdentityHashMap<Method, ProxyMethodInfo> methodInfoMap = new IdentityHashMap<Method, ProxyMethodInfo>();
            final CompressionHint classCompressionHint = type.getAnnotation(CompressionHint.class);
            final int classCompressionLevel = classCompressionHint == null ? -1 : classCompressionHint.compressionLevel() == -1 ? Deflater.DEFAULT_COMPRESSION : classCompressionHint.compressionLevel();
            final boolean classIdempotent = type.getAnnotation(Idempotent.class) != null;
            final boolean classAsync = type.getAnnotation(ClientAsynchronous.class) != null;
            final Field[] declaredFields = proxyClass.getDeclaredFields();
            for (Field declaredField : declaredFields) {
                declaredField.setAccessible(true);
                if (declaredField.getType() == Method.class && declaredField.getName().charAt(0) == 'm' && (declaredField.getModifiers() & Modifier.STATIC) != 0) {
                    // seems a likely match
                    try {
                        final Method method = (Method) declaredField.get(null);
                        final boolean idempotent = classIdempotent || method.getAnnotation(Idempotent.class) != null;
                        final boolean clientAsync = classAsync || method.getAnnotation(ClientAsynchronous.class) != null;
                        final CompressionHint compressionHint = method.getAnnotation(CompressionHint.class);
                        final int compressionLevel = compressionHint == null ? classCompressionLevel : compressionHint.compressionLevel() == -1 ? Deflater.DEFAULT_COMPRESSION : compressionHint.compressionLevel();
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
                        final int methodType = getMethodType(type, method.getName(), methodParamTypes);
                        final ProxyMethodInfo proxyMethodInfo = new ProxyMethodInfo(methodType, compressionLevel, idempotent, method, b.toString(), parameterTypeNames, clientAsync);
                        methodInfoMap.put(method, proxyMethodInfo);
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
            return new EJBProxyInformation<P>(proxyClass, constructor, methodInfoMap, classCompressionLevel, classIdempotent, classAsync);
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

    EJBProxyInformation(final Class<? extends T> proxyClass, final Constructor<? extends T> proxyConstructor, final IdentityHashMap<Method, ProxyMethodInfo> methodInfoMap, final int classCompressionHint, final boolean classIdempotent, final boolean classAsync) {
        this.proxyClass = proxyClass;
        this.proxyConstructor = proxyConstructor;
        this.methodInfoMap = methodInfoMap;
        this.classCompressionHint = classCompressionHint;
        this.classIdempotent = classIdempotent;
        this.classAsync = classAsync;
    }

    @SuppressWarnings("unchecked")
    static <T> EJBProxyInformation<T> forViewType(Class<T> clazz) {
        return (EJBProxyInformation<T>) PROXY_INFORMATION_CLASS_VALUE.get(clazz);
    }

    private final Class<? extends T> proxyClass;
    private final Constructor<? extends T> proxyConstructor;
    private final IdentityHashMap<Method, ProxyMethodInfo> methodInfoMap;
    private final int classCompressionHint;
    private final boolean classIdempotent;
    private final boolean classAsync;

    boolean hasCompressionHint(Method proxyMethod) {
        final ProxyMethodInfo proxyMethodInfo = methodInfoMap.get(proxyMethod);
        return proxyMethodInfo != null && proxyMethodInfo.compressionLevel != -1;
    }

    boolean isIdempotent(Method proxyMethod) {
        final ProxyMethodInfo proxyMethodInfo = methodInfoMap.get(proxyMethod);
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
        return methodInfoMap.get(method);
    }

    static final class ProxyMethodInfo {

        final int methodType;
        final int compressionLevel;
        final boolean idempotent;
        final Method method;
        final String signature;
        final String[] parameterTypeNames;
        final boolean clientAsync;

        ProxyMethodInfo(final int methodType, final int compressionLevel, final boolean idempotent, final Method method, final String signature, final String[] parameterTypeNames, final boolean clientAsync) {
            this.methodType = methodType;
            this.compressionLevel = compressionLevel;
            this.idempotent = idempotent;
            this.method = method;
            this.signature = signature;
            this.parameterTypeNames = parameterTypeNames;
            this.clientAsync = clientAsync;
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

        Method getMethod() {
            return method;
        }

        String getSignature() {
            return signature;
        }

        String[] getParameterTypeNames() {
            return parameterTypeNames;
        }

        boolean isClientAsync() {
            return clientAsync;
        }
    }
}
