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

/**
 * An interceptor context for EJB client proxy serialization.
 *
 * @param <A> the receiver attachment type
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientProxyContext<A> {
    private final EJBInvocationHandler invocationHandler;
    private final EJBClientContext ejbClientContext;
    private final A receiverSpecific;
    private final EJBReceiver<A> receiver;

    EJBClientProxyContext(final EJBInvocationHandler invocationHandler, final EJBClientContext ejbClientContext, final A receiverSpecific, final EJBReceiver<A> receiver) {
        this.invocationHandler = invocationHandler;
        this.ejbClientContext = ejbClientContext;
        this.receiverSpecific = receiverSpecific;
        this.receiver = receiver;
    }

    /**
     * Get a value attached to the proxy.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the value, or {@code null} if there is none
     */
    public <T> T getProxyAttachment(AttachmentKey<T> key) {
        return invocationHandler.getAttachment(key);
    }

    /**
     * Get a value attached to the EJB client context.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the value, or {@code null} if there is none
     */
    public <T> T getClientContextAttachment(AttachmentKey<T> key) {
        return ejbClientContext.getAttachment(key);
    }

    /**
     * Get the receiver-specific attachment for protocol-specific interceptors.
     *
     * @return the receiver attachment
     */
    public A getReceiverSpecific() {
        return receiverSpecific;
    }

    public <T> T putProxyAttachment(final AttachmentKey<T> key, final T value) {
        return invocationHandler.putAttachment(key, value);
    }

    protected EJBReceiver<A> getReceiver() {
        return receiver;
    }

    public EJBInvocationHandler getInvocationHandler() {
        return invocationHandler;
    }
}
