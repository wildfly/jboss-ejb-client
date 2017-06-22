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

import java.io.Serializable;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.wildfly.common.Assert;

/**
 * The base class of invocation contexts consumed by interceptors and receivers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractInvocationContext extends Attachable {
    protected final EJBClientContext ejbClientContext;
    // selected target receiver
    protected EJBReceiver receiver;
    protected EJBLocator<?> locator;
    protected Affinity weakAffinity = Affinity.NONE;
    private URI destination;
    private Affinity targetAffinity;
    private Map<String, Object> contextData;

    AbstractInvocationContext(final EJBLocator<?> locator, final EJBClientContext ejbClientContext) {
        this.locator = locator;
        this.ejbClientContext = ejbClientContext;
    }

    /**
     * Get the EJB client context associated with this invocation.
     *
     * @return the EJB client context
     */
    public EJBClientContext getClientContext() {
        return ejbClientContext;
    }

    /**
     * Get the context data.  This same data will be made available verbatim to
     * server-side interceptors via the {@code InvocationContext.getContextData()} method, and thus
     * can be used to pass data from the client to the server (as long as all map values are
     * {@link Serializable}).
     *
     * @return the context data
     */
    public Map<String, Object> getContextData() {
        final Map<String, Object> contextData = this.contextData;
        if (contextData == null) {
            return this.contextData = new LinkedHashMap<String, Object>();
        } else {
            return contextData;
        }
    }

    /**
     * Get the locator for the invocation target.
     *
     * @return the locator
     */
    public EJBLocator<?> getLocator() {
        return locator;
    }

    /**
     * Set the locator for the invocation target.
     *
     * @param locator the locator for the invocation target
     */
    public <T> void setLocator(final EJBLocator<T> locator) {
        this.locator = locator;
    }

    /**
     * Get the resolved destination of this invocation.  If the destination is not yet decided, {@code null} is
     * returned.
     *
     * @return the resolved destination of this invocation, or {@code null} if it is not yet known
     */
    public URI getDestination() {
        return destination;
    }

    /**
     * Set the resolved destination of this invocation.  The destination must be decided by the end of the interceptor
     * chain, otherwise an exception will result.
     *
     * @param destination the resolved destination of this invocation
     */
    public void setDestination(final URI destination) {
        this.destination = destination;
    }

    /**
     * Get the resolved target affinity of this invocation.  If the target affinity is not yet decided, {@code null}
     * is returned.  The target affinity is retained only for the lifetime of the invocation; it may be used to aid
     * in resolving the {@linkplain #setDestination(URI) destination to set}.
     *
     * @return the resolved target affinity of this invocation, or {@code null} if it is not yet known
     */
    public Affinity getTargetAffinity() {
        return targetAffinity;
    }

    /**
     * Set the resolved target affinity of this invocation.
     *
     * @param targetAffinity the resolved target affinity of this invocation
     */
    public void setTargetAffinity(final Affinity targetAffinity) {
        this.targetAffinity = targetAffinity;
    }

    /**
     * Get the EJB receiver associated with this invocation.
     *
     * @return the EJB receiver
     */
    EJBReceiver getReceiver() {
        return receiver;
    }

    /**
     * Set the EJB receiver associated with this invocation.
     *
     * @param receiver the EJB receiver associated with this invocation
     */
    void setReceiver(final EJBReceiver receiver) {
        this.receiver = receiver;
    }

    /**
     * Get the invocation weak affinity.
     *
     * @return the invocation weak affinity, or {@link Affinity#NONE} if none (not {@code null})
     */
    public Affinity getWeakAffinity() {
        return weakAffinity;
    }

    /**
     * Set the invocation weak affinity.
     *
     * @param weakAffinity the invocation weak affinity (must not be {@code null})
     */
    public void setWeakAffinity(final Affinity weakAffinity) {
        Assert.checkNotNullParam("weakAffinity", weakAffinity);
        this.weakAffinity = weakAffinity;
    }

    /**
     * Get the invoked view class.
     *
     * @return the invoked view class
     */
    public Class<?> getViewClass() {
        return locator.getViewType();
    }
}
