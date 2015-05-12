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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.wildfly.common.Assert;

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
        return new EJBInvocationHandler<T>(locator);
    }
}
