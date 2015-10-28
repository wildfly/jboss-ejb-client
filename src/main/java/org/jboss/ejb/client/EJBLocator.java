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

package org.jboss.ejb.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

import org.jboss.marshalling.FieldSetter;
import org.wildfly.common.Assert;

/**
 * An identifier for an EJB proxy invocation target instance, suitable for use as a hash key or a serialized token.
 *
 * @param <T> the interface type
 */
public abstract class EJBLocator<T> implements Serializable {
    private static final long serialVersionUID = -7306257085240447972L;

    private final Class<T> viewType;
    private final String appName;
    private final String moduleName;
    private final String beanName;
    private final String distinctName;
    private final Affinity affinity;
    private final transient int hashCode;
    private transient EJBProxyInformation<T> proxyInformation;

    private static final FieldSetter hashCodeSetter = FieldSetter.get(EJBLocator.class, "hashCode");

    EJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        Assert.checkNotNullParam("viewType", viewType);
        Assert.checkNotNullParam("appName", appName);
        Assert.checkNotNullParam("moduleName", moduleName);
        Assert.checkNotNullParam("beanName", beanName);
        Assert.checkNotNullParam("distinctName", distinctName);
        this.viewType = viewType;
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.distinctName = distinctName;
        this.affinity = affinity == null ? Affinity.NONE : affinity;
        hashCode = calcHashCode(viewType, appName, moduleName, beanName, distinctName, this.affinity);
    }

    EJBLocator(final EJBLocator<T> original, final Affinity newAffinity) {
        Assert.checkNotNullParam("original", original);
        this.viewType = original.viewType;
        this.appName = original.appName;
        this.moduleName = original.moduleName;
        this.beanName = original.beanName;
        this.distinctName = original.distinctName;
        this.affinity = newAffinity == null ? Affinity.NONE : newAffinity;
        hashCode = calcHashCode(viewType, appName, moduleName, beanName, distinctName, affinity);
    }

    private static int calcHashCode(final Class<?> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        return viewType.hashCode() + 13 * (appName.hashCode() + 13 * (moduleName.hashCode() + 13 * (beanName.hashCode() + 13 * (distinctName.hashCode() + 13 * affinity.hashCode()))));
    }

    /**
     * Create a copy of this locator, but with the new given affinity.
     *
     * @param affinity the new affinity
     * @return the new locator
     */
    public abstract EJBLocator<T> withNewAffinity(Affinity affinity);

    /**
     * Narrow this locator to the target type.
     *
     * @param type the target type class
     * @param <S> the target type
     * @return this instance, narrowed to the given type
     * @throws ClassCastException if the view type cannot be cast to the given type
     */
    @SuppressWarnings("unchecked")
    public <S> EJBLocator<? extends S> narrowTo(Class<S> type) {
        if (type.isAssignableFrom(viewType)) {
            return (EJBLocator<? extends S>) this;
        }
        throw new ClassCastException(type.toString());
    }

    /**
     * Narrow this locator to the target type as a home locator.
     *
     * @param type the target type class
     * @param <S> the target type
     * @return this instance, narrowed to the given type and cast as a home locator
     * @throws ClassCastException if the view type cannot be cast to the given type or if this locator is not a home locator
     */
    public <S extends EJBHome> EJBHomeLocator<? extends S> narrowAsHome(Class<S> type) {
        throw new ClassCastException(EJBHomeLocator.class.toString());
    }

    /**
     * Narrow this locator to the target type as a entity locator.
     *
     * @param type the target type class
     * @param <S> the target type
     * @return this instance, narrowed to the given type and cast as a entity locator
     * @throws ClassCastException if the view type cannot be cast to the given type or if this locator is not a entity locator
     */
    public <S extends EJBObject> EntityEJBLocator<? extends S> narrowAsEntity(Class<S> type) {
        throw new ClassCastException(EntityEJBLocator.class.toString());
    }

    /**
     * Narrow this locator to the target type as a stateful locator.
     *
     * @param type the target type class
     * @param <S> the target type
     * @return this instance, narrowed to the given type and cast as a stateful locator
     * @throws ClassCastException if the view type cannot be cast to the given type or if this locator is not a stateful locator
     */
    public <S> StatefulEJBLocator<? extends S> narrowAsStateful(Class<S> type) {
        throw new ClassCastException(StatefulEJBLocator.class.toString());
    }

    /**
     * Narrow this locator to the target type as a stateless locator.
     *
     * @param type the target type class
     * @param <S> the target type
     * @return this instance, narrowed to the given type and cast as a stateless locator
     * @throws ClassCastException if the view type cannot be cast to the given type or if this locator is not a stateless locator
     */
    public <S> StatelessEJBLocator<? extends S> narrowAsStateless(Class<S> type) {
        throw new ClassCastException(StatelessEJBLocator.class.toString());
    }

    /**
     * Return this locator as a stateless locator, if it is one.
     *
     * @return this instance, cast as a stateless locator
     * @throws ClassCastException if this locator is not a stateless locator
     */
    public StatelessEJBLocator<T> asStateless() {
        throw new ClassCastException(StatefulEJBLocator.class.toString());
    }

    /**
     * Return this locator as a stateful locator, if it is one.
     *
     * @return this instance, cast as a stateful locator
     * @throws ClassCastException if this locator is not a stateful locator
     */
    public StatefulEJBLocator<T> asStateful() {
        throw new ClassCastException(StatefulEJBLocator.class.toString());
    }

    /**
     * Determine if this is a stateless locator.  If so, calls to {@link #asStateless()} and {@link #narrowAsStateless(Class)}
     * will generally succeed.
     *
     * @return {@code true} if this locator is stateless, {@code false} otherwise
     */
    public boolean isStateless() {
        return false;
    }

    /**
     * Determine if this is a stateful locator.  If so, calls to {@link #asStateful()} and {@link #narrowAsStateful(Class)}
     * will generally succeed.
     *
     * @return {@code true} if this locator is stateful, {@code false} otherwise
     */
    public boolean isStateful() {
        return false;
    }

    /**
     * Determine if this is an entity locator.  If so, calls to {@link #narrowAsEntity(Class)} will generally succeed.
     *
     * @return {@code true} if this locator is an entity, {@code false} otherwise
     */
    public boolean isEntity() {
        return false;
    }

    /**
     * Determine if this is a home locator.  If so, calls to {@link #narrowAsHome(Class)} will generally succeed.
     *
     * @return {@code true} if this locator is a home, {@code false} otherwise
     */
    public boolean isHome() {
        return false;
    }

    /**
     * Get the view type of this locator.
     *
     * @return the view type
     */
    public Class<T> getViewType() {
        return viewType;
    }

    /**
     * Get the application name.
     *
     * @return the application name
     */
    public String getAppName() {
        return appName;
    }

    /**
     * Get the module name.
     *
     * @return the module name
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Get the EJB bean name.
     *
     * @return the EJB bean name
     */
    public String getBeanName() {
        return beanName;
    }

    /**
     * Get the module distinct name.
     *
     * @return the module distinct name
     */
    public String getDistinctName() {
        return distinctName;
    }

    /**
     * Get the locator affinity.
     *
     * @return the locator affinity
     */
    public Affinity getAffinity() {
        return affinity;
    }

    /**
     * Get the hash code for this instance.
     *
     * @return the hash code for this instance
     */
    public int hashCode() {
        return hashCode;
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof EJBLocator && equals((EJBLocator<?>) other);
    }

    /**
     * Get the proxy class for this locator.
     *
     * @return the proxy class
     */
    public Class<? extends T> getProxyClass() {
        return getProxyInformation().getProxyClass();
    }

    /**
     * Get the proxy class constructor for this locator.  A proxy class constructor accepts a single
     * argument of type {@link InvocationHandler}.
     *
     * @return the proxy constructor
     */
    public Constructor<? extends T> getProxyConstructor() {
        return getProxyInformation().getProxyConstructor();
    }

    EJBProxyInformation<T> getProxyInformation() {
        final EJBProxyInformation<T> i = proxyInformation;
        return i != null ? i : (proxyInformation = EJBProxyInformation.forViewType(viewType));
    }

    /**
     * Create a proxy instance using the cached proxy class.
     *
     * @param invocationHandler the invocation handler to use
     * @return the proxy instance
     */
    public T createProxyInstance(InvocationHandler invocationHandler) {
        Assert.checkNotNullParam("invocationHandler", invocationHandler);
        try {
            return getProxyConstructor().newInstance(invocationHandler);
        } catch (InstantiationException e) {
            throw new InstantiationError(e.getMessage());
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            throw new UndeclaredThrowableException(e.getCause());
        }
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(EJBLocator<?> other) {
        return this == other || other != null && hashCode == other.hashCode
                && appName.equals(other.appName)
                && moduleName.equals(other.moduleName)
                && beanName.equals(other.beanName)
                && distinctName.equals(other.distinctName)
                && affinity.equals(other.affinity);
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        hashCodeSetter.setInt(this, calcHashCode(viewType, appName, moduleName, beanName, distinctName, affinity));
    }

    @Override
    public String toString() {
        final String distinctName = getDistinctName();
        if (distinctName == null || distinctName.isEmpty()) {
            return String.format("%s for \"%s/%s/%s\", view is %s, affinity is %s", getClass().getSimpleName(), getAppName(), getModuleName(), getBeanName(), getViewType(), getAffinity());
        } else {
            return String.format("%s for \"%s/%s/%s/%s\", view is %s, affinity is %s", getClass().getSimpleName(), getAppName(), getModuleName(), getBeanName(), distinctName, getViewType(), getAffinity());
        }
    }
}
