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

import org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver;
import org.jboss.remoting3.Connection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The public API for an EJB client context.  A thread may be associated with an EJB client context.  An EJB
 * client context may be associated with (and used by) one or more threads concurrently.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBClientContext extends Attachable {

    private static final InheritableThreadLocal<EJBClientContext> CURRENT = new InheritableThreadLocal<EJBClientContext>();

    static final GeneralEJBClientInterceptor[] GENERAL_INTERCEPTORS;

    static {
        final List<GeneralEJBClientInterceptor> interceptors = new ArrayList<GeneralEJBClientInterceptor>();
        for (GeneralEJBClientInterceptor interceptor : ServiceLoader.load(GeneralEJBClientInterceptor.class)) {
            interceptors.add(interceptor);
        }
        GENERAL_INTERCEPTORS = interceptors.toArray(new GeneralEJBClientInterceptor[interceptors.size()]);
    }

    private volatile EJBReceiver receiver;

    EJBClientContext() {
    }

    /**
     * Create a new client context and associate it with the current thread.
     */
    public static void create() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("The current EJB client context is already set");
        }
        CURRENT.set(new EJBClientContext());
    }

    /**
     * Set the current client context.
     *
     * @param context the client context
     * @throws IllegalStateException if there is already a client context for the current thread
     */
    public static void setCurrent(EJBClientContext context) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("The current EJB client context is already set");
        }
        CURRENT.set(context);
    }

    /**
     * Suspend the current client context.  The context is returned and the thread's current context is cleared.
     *
     * @return the suspended context
     * @throws IllegalStateException if there is no client context for the current thread
     */
    public static EJBClientContext suspendCurrent() {
        try {
            return requireCurrent();
        } finally {
            CURRENT.set(null);
        }
    }

    /**
     * Get and set the current client context for this thread.
     *
     * @param context the new current client context
     * @return the previous context, which should be restored in a {@code finally} block
     */
    public static EJBClientContext getAndSetCurrent(EJBClientContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context is null");
        }
        final InheritableThreadLocal<EJBClientContext> tl = CURRENT;
        try {
            return tl.get();
        } finally {
            tl.set(context);
        }
    }

    /**
     * Restore the current client context for this thread.  Used to restore the client context value after it was
     * saved with {@link #getAndSetCurrent(org.jboss.ejb.client.EJBClientContext)}.  The previous current context
     * is discarded.
     *
     * @param current the new current client context
     */
    public static void restoreCurrent(EJBClientContext current) {
        CURRENT.set(current);
    }

    /**
     * Get the current client context for this thread.
     *
     * @return the current client context
     */
    public static EJBClientContext getCurrent() {
        return CURRENT.get();
    }

    /**
     * Get the current client context for this thread, throwing an exception if none is set.
     *
     * @return the current client context
     * @throws IllegalStateException if the current client context is not set
     */
    public static EJBClientContext requireCurrent() throws IllegalStateException {
        final EJBClientContext clientContext = CURRENT.get();
        if (clientContext == null) {
            throw new IllegalStateException("No EJB client context is set for this thread");
        }
        return clientContext;
    }

    public void registerEJBReceiver(final EJBReceiver<?> receiver) {
        this.receiver = receiver;
    }

    /**
     * Register a Remoting connection with this client context.
     *
     * @param connection the connection to register
     */
    public void registerConnection(Connection connection) {
        registerEJBReceiver(new RemotingConnectionEJBReceiver(connection));
    }

    protected Collection<EJBReceiver<?>> getEJBReceivers(final String appName, final String moduleName, final String distinctName) {
        return Collections.<EJBReceiver<?>>singleton(receiver);
    }

    /**
     * Get the first EJB receiver which matches the given module name.
     *
     * @param appName the application name, or {@code null} for a top-level module
     * @param moduleName the module name
     * @param distinctName the distinct name, or {@code null} for none
     * @return the first EJB receiver to match, or {@code null} if none match
     */
    protected EJBReceiver<?> getEJBReceiver(final String appName, final String moduleName, final String distinctName) {
        final Iterator<EJBReceiver<?>> iterator = getEJBReceivers(appName, moduleName, distinctName).iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }
}
