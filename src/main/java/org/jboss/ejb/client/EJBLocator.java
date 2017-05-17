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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;

import javax.ejb.EJBHome;
import javax.ejb.EJBObject;

import org.jboss.ejb._private.Logs;
import org.wildfly.common.Assert;

/**
 * An identifier for an EJB proxy invocation target instance, suitable for use as a hash key or a serialized token.
 *
 * @param <T> the interface type
 */
public abstract class EJBLocator<T> implements Serializable {
    private static final long serialVersionUID = -7306257085240447972L;

    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("viewType", Class.class),
        new ObjectStreamField("appName", String.class),
        new ObjectStreamField("moduleName", String.class),
        new ObjectStreamField("beanName", String.class),
        new ObjectStreamField("distinctName", String.class),
        new ObjectStreamField("affinity", Affinity.class),
        new ObjectStreamField("identifier", EJBIdentifier.class), // may not be present (V3+ only)
    };

    private final Class<T> viewType;
    private final EJBIdentifier identifier;
    private final Affinity affinity;
    private int hashCode;
    private EJBProxyInformation<T> proxyInformation;

    private static final Field viewTypeSetter;
    private static final Field identifierSetter;
    private static final Field affinitySetter;

    static {
        final Class<?> clazz = EJBLocator.class;
        try {
            viewTypeSetter = clazz.getDeclaredField("viewType");
            secureSetAccessible(viewTypeSetter, true);
            identifierSetter = clazz.getDeclaredField("identifier");
            secureSetAccessible(identifierSetter, true);
            affinitySetter = clazz.getDeclaredField("affinity");
            secureSetAccessible(affinitySetter, true);
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    EJBLocator(final Class<T> viewType, final EJBIdentifier identifier, final Affinity affinity) {
        Assert.checkNotNullParam("viewType", viewType);
        Assert.checkNotNullParam("identifier", identifier);
        Assert.checkNotNullParam("affinity", affinity);
        this.viewType = viewType;
        this.identifier = identifier;
        this.affinity = affinity == null ? Affinity.NONE : affinity;
    }

    EJBLocator(final Class<T> viewType, final String appName, final String moduleName, final String beanName, final String distinctName, final Affinity affinity) {
        this(viewType, new EJBIdentifier(appName, moduleName, beanName, distinctName), affinity);
    }

    EJBLocator(final EJBLocator<T> original, final Affinity newAffinity) {
        this(Assert.checkNotNullParam("original", original).viewType, original.identifier, newAffinity);
    }

    /**
     * Create a copy of this locator, but with the new given affinity.
     *
     * @param affinity the new affinity
     * @return the new locator
     */
    public abstract EJBLocator<T> withNewAffinity(Affinity affinity);

    /**
     * Create a copy of this locator, but with the given stateful session ID.  If this locator cannot be converted,
     * an exception is thrown.
     *
     * @param sessionId the stateful session ID (must not be {@code null})
     * @return the new locator (not {@code null})
     */
    public StatefulEJBLocator<T> withSession(SessionID sessionId) {
        throw Logs.MAIN.cannotConvertToStateful(this);
    }

    /**
     * Create a copy of this locator, but with the given affinity and stateful session ID.  If this locator cannot be converted,
     * an exception is thrown.
     *
     * @param sessionId the stateful session ID (must not be {@code null})
     * @param affinity the new affinity (must not be {@code null})
     * @return the new locator (not {@code null})
     */
    public StatefulEJBLocator<T> withSessionAndAffinity(SessionID sessionId, Affinity affinity) {
        throw Logs.MAIN.cannotConvertToStateful(this);
    }

    /**
     * Determine whether a {@link #narrowTo(Class)} operation would succeed.
     *
     * @param type the type to narrow to
     * @return {@code true} if the narrow would succeed; {@code false} otherwise
     */
    public boolean canNarrowTo(Class<?> type) {
        return type != null && type.isAssignableFrom(viewType);
    }

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
        return identifier.getAppName();
    }

    /**
     * Get the module name.
     *
     * @return the module name
     */
    public String getModuleName() {
        return identifier.getModuleName();
    }

    /**
     * Get the EJB bean name.
     *
     * @return the EJB bean name
     */
    public String getBeanName() {
        return identifier.getBeanName();
    }

    /**
     * Get the module distinct name.
     *
     * @return the module distinct name
     */
    public String getDistinctName() {
        return identifier.getDistinctName();
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
     * Get the EJB identifier for this locator.
     *
     * @return the EJB identifier
     */
    public EJBIdentifier getIdentifier() {
        return identifier;
    }

    /**
     * Get the hash code for this instance.
     *
     * @return the hash code for this instance
     */
    public final int hashCode() {
        int hashCode = this.hashCode;
        if (hashCode != 0) {
            return hashCode;
        }
        hashCode = calculateHashCode();
        return this.hashCode = hashCode == 0 ? hashCode | 0x8000_0000 : hashCode;
    }

    int calculateHashCode() {
        return Objects.hashCode(viewType) + 13 * (Objects.hashCode(identifier) + 13 * Objects.hashCode(affinity));
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
                && identifier.equals(other.identifier)
                && affinity.equals(other.affinity);
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        final ObjectInputStream.GetField fields = ois.readFields();
        try {
            final Object identifierObj = fields.get("identifier", null);
            // if not null, the old *Name fields will share backreferences with the fields of the identifier at a cost of 4 bytes
            final EJBIdentifier identifier = identifierObj != null ? (EJBIdentifier) identifierObj : new EJBIdentifier(
                (String) fields.get("appName", null),
                (String) fields.get("moduleName", null),
                (String) fields.get("beanName", null),
                (String) fields.get("distinctName", null)
            );
            viewTypeSetter.set(this, fields.get("viewType", null));
            identifierSetter.set(this, identifier);
            affinitySetter.set(this, fields.get("affinity", Affinity.NONE));
        } catch ( IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        final ObjectOutputStream.PutField fields = oos.putFields();
        final EJBIdentifier identifier = this.identifier;
        fields.put("identifier", identifier);
        fields.put("viewType", viewType);
        fields.put("affinity", affinity);
        // now compat fields
        fields.put("appName", identifier.getAppName());
        fields.put("moduleName", identifier.getModuleName());
        fields.put("beanName", identifier.getBeanName());
        fields.put("distinctName", identifier.getDistinctName());
        oos.writeFields();
    }

    @Override
    public String toString() {
        return String.format("%s for \"%s\", view is %s, affinity is %s", getClass().getSimpleName(), identifier, getViewType(), getAffinity());
    }

    private static void secureSetAccessible(final Field field, final boolean flag) {
        if (System.getSecurityManager() == null) {
            field.setAccessible(flag);
        } else {
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                field.setAccessible(flag);
                return null;
            });
        }
    }
}
