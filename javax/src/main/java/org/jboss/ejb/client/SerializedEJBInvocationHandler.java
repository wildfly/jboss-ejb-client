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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;
import java.util.function.Supplier;

import org.wildfly.common.Assert;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * A serialized EJB invocation handler.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SerializedEJBInvocationHandler implements Externalizable {

    private static final long serialVersionUID = -2370168183054746652L;

    private EJBLocator<?> locator;
    private boolean async;

    /**
     * Construct a new instance.
     */
    public SerializedEJBInvocationHandler() {
    }

    /**
     * Construct a new instance.
     *
     * @param locator the locator for this invocation handler
     */
    public SerializedEJBInvocationHandler(final EJBLocator<?> locator) {
        this.locator = locator;
        this.async = false;
    }

    /**
     * Construct a new instance.
     *
     * @param locator the locator for this invocation handler
     */
    public SerializedEJBInvocationHandler(final EJBLocator<?> locator, final boolean async) {
        this.locator = locator;
        this.async = async;
    }

    /**
     * Get the invocation locator.
     *
     * @return the invocation locator
     */
    public EJBLocator<?> getLocator() {
        return locator;
    }

    /**
     * Set the invocation locator.
     *
     * @param locator the invocation locator
     */
    public void setLocator(final EJBLocator<?> locator) {
        Assert.checkNotNullParam("locator", locator);
        this.locator = locator;
    }

    /**
     * Write this object to the output stream.
     *
     * @param out the output stream
     * @throws IOException if a write error occurs
     */
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(locator);
        out.writeBoolean(async);
    }

    /**
     * Read this object from the input stream.
     *
     * @param in the input stream
     * @throws IOException            if a read error occurs
     * @throws ClassNotFoundException if a class cannot be resolved
     */
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        locator = (EJBLocator<?>) in.readObject();
        // ignore EOF
        int data = in.read();
        if (data > 0) {
            async = true;
        }
    }

    /**
     * Resolve the corresponding invocation handler.
     *
     * @return the invocation handler
     */
    @SuppressWarnings("unused")
    protected Object readResolve() {
        final EJBLocator<?> locator = this.locator;
        Assert.checkNotNullParam("locator", locator);
        return readResolve(locator);
    }

    /**
     * This method exists to create an invocation handler for a locator in a type-safe manner.
     *
     * @param locator the locator
     * @param <T> the view type
     * @return the invocation handler
     */
    private static <T> EJBInvocationHandler<T> readResolve(EJBLocator<T> locator) {
        final NamingProvider namingProvider = NamingProvider.getCurrentNamingProvider();
        final Supplier<AuthenticationContext> supplier = namingProvider != null ? namingProvider.getProviderEnvironment().getAuthenticationContextSupplier() : CaptureCurrentAuthCtxSupplier.INSTANCE;
        return new EJBInvocationHandler<T>(locator, supplier);
    }
}
