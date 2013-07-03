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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.jboss.ejb.client.remoting.ReconnectHandler;
import org.jboss.ejb.client.remoting.RemotingConnectionEJBReceiver;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;

/**
 * The public API for an EJB client context.  An EJB client context may be associated with (and used by) one or more threads concurrently.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings({"UnnecessaryThis"})
public final class EJBClientContext extends Attachable implements Closeable {

    private static final Logger logger = Logger.getLogger(EJBClientContext.class);

    private static final RuntimePermission SET_SELECTOR_PERMISSION = new RuntimePermission("setEJBClientContextSelector");
    private static final RuntimePermission ADD_INTERCEPTOR_PERMISSION = new RuntimePermission("registerInterceptor");
    private static final RuntimePermission CREATE_CONTEXT_PERMISSION = new RuntimePermission("createEJBClientContext");
    private static final AtomicReferenceFieldUpdater<EJBClientContext, EJBClientInterceptor.Registration[]> registrationsUpdater = AtomicReferenceFieldUpdater.newUpdater(EJBClientContext.class, EJBClientInterceptor.Registration[].class, "registrations");

    private static final EJBClientInterceptor.Registration[] NO_INTERCEPTORS = new EJBClientInterceptor.Registration[0];

    /**
     * EJB client context selector. By default the {@link ConfigBasedEJBClientContextSelector} is used.
     */
    private static volatile ContextSelector<EJBClientContext> SELECTOR;

    static {
        final Properties ejbClientProperties = EJBClientPropertiesLoader.loadEJBClientProperties();
        if (ejbClientProperties == null) {
            SELECTOR = new ConfigBasedEJBClientContextSelector(null);
        } else {
            final EJBClientConfiguration clientConfiguration = new PropertiesBasedEJBClientConfiguration(ejbClientProperties);
            SELECTOR = new ConfigBasedEJBClientContextSelector(clientConfiguration);
        }
    }

    private static volatile boolean SELECTOR_LOCKED;

    private final Map<EJBReceiver, ReceiverAssociation> ejbReceiverAssociations = new IdentityHashMap<EJBReceiver, ReceiverAssociation>();
    private final Map<EJBReceiverContext, EJBReceiverContextCloseHandler> receiverContextCloseHandlers = Collections.synchronizedMap(new IdentityHashMap<EJBReceiverContext, EJBReceiverContextCloseHandler>());
    private volatile EJBClientInterceptor.Registration[] registrations = NO_INTERCEPTORS;
    private Set<EJBClientInterceptor> clientInterceptorsInClasspath;

    /**
     * Cluster contexts mapped against their cluster name
     */
    private final Map<String, ClusterContext> clusterContexts = Collections.synchronizedMap(new HashMap<String, ClusterContext>());

    private final EJBClientConfiguration ejbClientConfiguration;

    private final ClusterFormationNotifier clusterFormationNotifier = new ClusterFormationNotifier();
    private final DeploymentNodeSelector deploymentNodeSelector;

    private final ExecutorService ejbClientContextTasksExecutorService = Executors.newCachedThreadPool(new DaemonThreadFactory("ejb-client-context-tasks"));
    private final List<ReconnectHandler> reconnectHandlers = new ArrayList<ReconnectHandler>();

    private final Collection<EJBClientContextListener> ejbClientContextListeners = Collections.synchronizedSet(new HashSet<EJBClientContextListener>());
    private volatile boolean closed;

    private EJBClientContext(final EJBClientConfiguration ejbClientConfiguration) {
        this.ejbClientConfiguration = ejbClientConfiguration;
        if (ejbClientConfiguration != null && ejbClientConfiguration.getDeploymentNodeSelector() != null) {
            this.deploymentNodeSelector = ejbClientConfiguration.getDeploymentNodeSelector();
        } else {
            this.deploymentNodeSelector = new RandomDeploymentNodeSelector();
        }
    }

    private void init(ClassLoader classLoader) {
        if (classLoader == null) {
            classLoader = EJBClientContext.class.getClassLoader();
        }
        for (final EJBClientContextInitializer contextInitializer : SecurityActions.loadService(EJBClientContextInitializer.class, classLoader)) {
            try {
                contextInitializer.initialize(this);
            } catch (Throwable ignored) {
                logger.debug("EJB client context initializer " + contextInitializer + " failed to initialize context " + this, ignored);
            }
        }
        // TODO: Perhaps have system property which disables scanning the classpath for client interceptors?
        // load any EJBClientInterceptor(s) from classpath using the ServiceLoader. These interceptors will be added to the end of the chain
        try {
            for (final EJBClientInterceptor interceptor : SecurityActions.loadService(EJBClientInterceptor.class, classLoader)) {
                if (this.clientInterceptorsInClasspath == null) {
                    // we need to maintain order, so the LinkedHashSet
                    this.clientInterceptorsInClasspath = new LinkedHashSet<EJBClientInterceptor>();
                }
                this.clientInterceptorsInClasspath.add(interceptor);
            }
        } catch (Throwable t) {
            // just log a message and don't cause the application to fail, perhaps due to a rogue library in the classpath
            logger.debug("Failed to load EJB client interceptor(s) from the classpath via classloader " + classLoader + " for EJB client context " + this, t);
        }

    }

    /**
     * Creates and returns a new client context.
     *
     * @return the newly created context
     */
    public static EJBClientContext create() {
        return create(null, EJBClientContext.class.getClassLoader());
    }

    /**
     * Creates and returns a new client context, using the given class loader to look for initializers.
     *
     * @param classLoader the class loader. Cannot be null
     * @return the newly created context
     */
    public static EJBClientContext create(ClassLoader classLoader) {
        return create(null, classLoader);
    }

    /**
     * Creates and returns a new client context. The passed <code>ejbClientConfiguration</code> will
     * be used by this client context during any of the context management activities (like auto-creation
     * of remoting EJB receivers)
     *
     * @param ejbClientConfiguration The EJB client configuration. Can be null.
     * @return
     */
    public static EJBClientContext create(final EJBClientConfiguration ejbClientConfiguration) {
        return create(ejbClientConfiguration, EJBClientContext.class.getClassLoader());
    }

    /**
     * Creates and returns a new client context, using the given class loader to look for initializers.
     * The passed <code>ejbClientConfiguration</code> will be used by this client context during any of
     * the context management activities (like auto-creation of remoting EJB receivers)
     *
     * @param ejbClientConfiguration The EJB client configuration. Can be null.
     * @param classLoader            The class loader. Cannot be null
     * @return
     */
    public static EJBClientContext create(final EJBClientConfiguration ejbClientConfiguration, final ClassLoader classLoader) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_CONTEXT_PERMISSION);
        }
        final EJBClientContext context = new EJBClientContext(ejbClientConfiguration);
        // run it through the initializers
        context.init(classLoader);
        return context;
    }


    /**
     * Sets the EJB client context selector. Replaces the existing selector, which is then returned by this method
     *
     * @param newSelector The selector to set. Cannot be null
     * @return Returns the previously set EJB client context selector.
     * @throws SecurityException if a security manager is installed and you do not have the {@code setEJBClientContextSelector}
     *                           {@link RuntimePermission}
     */
    public static ContextSelector<EJBClientContext> setSelector(final ContextSelector<EJBClientContext> newSelector) {
        if (newSelector == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB client context selector");
        }
        if (SELECTOR_LOCKED) {
            throw Logs.MAIN.ejbClientContextSelectorMayNotBeChanged();
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_SELECTOR_PERMISSION);
        }
        final ContextSelector<EJBClientContext> oldSelector = SELECTOR;
        SELECTOR = newSelector;
        return oldSelector;
    }

    /**
     * Set a constant EJB client context.  Replaces the existing selector, which is then returned by this method
     *
     * @param context the context to set
     * @return Returns the previously set EJB client context selector.
     * @throws SecurityException if a security manager is installed and you do not have the {@code setEJBClientContextSelector} {@link RuntimePermission}
     */
    public static ContextSelector<EJBClientContext> setConstantContext(final EJBClientContext context) {
        return setSelector(new ConstantContextSelector<EJBClientContext>(context));
    }

    /**
     * Returns the current EJB client context selector
     *
     * @return
     */
    public static ContextSelector<EJBClientContext> getSelector() {
        return SELECTOR;
    }

    /**
     * Prevent the selector from being changed again.  Attempts to do so will result in a {@code SecurityException}.
     *
     * @throws SecurityException if a security manager is installed and you do not have the {@code setEJBClientContextSelector}
     *                           {@link RuntimePermission}
     */
    public static void lockSelector() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SET_SELECTOR_PERMISSION);
        }
        SELECTOR_LOCKED = true;
    }

    /**
     * Returns true if the EJB client context cannot be changed because it has been {@link #lockSelector()}.
     * Returns false otherwise.
     *
     * @return
     */
    public static boolean isSelectorLocked() {
        return SELECTOR_LOCKED;
    }

    /**
     * Get the current client context for this thread.
     *
     * @return the current client context
     */
    public static EJBClientContext getCurrent() {
        return SELECTOR.getCurrent();
    }

    /**
     * Get the current client context for this thread, throwing an exception if none is set.
     *
     * @return the current client context
     * @throws IllegalStateException if the current client context is not set
     */
    public static EJBClientContext requireCurrent() throws IllegalStateException {
        final EJBClientContext clientContext = getCurrent();
        if (clientContext == null) {
            throw Logs.MAIN.noEJBClientContextAvailable();
        }
        return clientContext;
    }

    /**
     * Returns a {@link EJBClientContext} which is identified by the passed {@link EJBClientContextIdentifier ejbClientContextIdentifier}.
     * The {@link EJBClientContext} is identified with the help of the {@link #getSelector() current selector}. If the
     * {@link #getSelector() current selector} is not of type {@link IdentityEJBClientContextSelector} or if that selector
     * can't {@link IdentityEJBClientContextSelector#getContext(EJBClientContextIdentifier) return} a {@link EJBClientContext}
     * for the passed {@link EJBClientContextIdentifier ejbClientContextIdentifier} then this method throws an {@link IllegalStateException}
     *
     * @param ejbClientContextIdentifier The EJB client context identifier
     * @return
     * @throws IllegalStateException If this method couldn't find a {@link EJBClientContext} for the passed <code>ejbClientContextIdentifier</code>
     */
    public static EJBClientContext require(final EJBClientContextIdentifier ejbClientContextIdentifier) throws IllegalStateException {
        final ContextSelector<EJBClientContext> currentSelector = SELECTOR;
        // see if the selector is capable of handling requests for identity based client contexts
        if (!(currentSelector instanceof IdentityEJBClientContextSelector)) {
            // the current selector can't handle identity based contexts.
            throw new IllegalStateException("No EJB client context available for context identifier: " + ejbClientContextIdentifier + ",since the " +
                    " current EJB client context selector " + currentSelector + " is not capable of returning identity based EJB client contexts");
        }
        // the selector is capable of handling scoped contexts, so use it to fetch the right one
        final EJBClientContext identityEjbClientContext = ((IdentityEJBClientContextSelector) currentSelector).getContext(ejbClientContextIdentifier);
        if (identityEjbClientContext == null) {
            throw new IllegalStateException("No EJB client context available for context identifier: " + ejbClientContextIdentifier);
        }
        return identityEjbClientContext;
    }

    /**
     * Register an EJB receiver with this client context.
     * <p/>
     * If the same {@link EJBReceiver} has already been associated in this client context or if a {@link EJBReceiver receiver}
     * with the same {@link org.jboss.ejb.client.EJBReceiver#getNodeName() node name} has already been associated in this client
     * context, then this method does <i>not</i> register the passed <code>receiver</code> and returns false.
     *
     * @param receiver the receiver to register
     * @return Returns true if the receiver was registered in this client context. Else returns false.
     * @throws IllegalArgumentException If the passed <code>receiver</code> is null
     */
    public boolean registerEJBReceiver(final EJBReceiver receiver) {
        return this.registerEJBReceiver(receiver, null);
    }

    /**
     * Registers a {@link EJBReceiver} in this context and uses the {@link EJBReceiverContextCloseHandler receiverContextCloseHandler}
     * to notify of a {@link EJBReceiverContext} being closed.
     * <p/>
     * If the same {@link EJBReceiver} has already been associated in this client context or if a {@link EJBReceiver receiver}
     * with the same {@link org.jboss.ejb.client.EJBReceiver#getNodeName() node name} has already been associated in this client
     * context, then this method does <i>not</i> register the passed <code>receiver</code> and returns false.
     *
     * @param receiver                    The EJB receiver to register
     * @param receiverContextCloseHandler The receiver context close handler. Can be null.
     * @return Returns true if the receiver was registered in this client context. Else returns false.
     */
    boolean registerEJBReceiver(final EJBReceiver receiver, final EJBReceiverContextCloseHandler receiverContextCloseHandler) {
        // make sure the EJB client context has not been closed
        this.assertNotClosed();

        if (receiver == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB receiver");
        }
        final EJBReceiverContext ejbReceiverContext;
        final ReceiverAssociation association;
        synchronized (this.ejbReceiverAssociations) {
            if (this.ejbReceiverAssociations.containsKey(receiver)) {
                logger.debug("Skipping registration of receiver " + receiver + " since the same instance already exists in this client context " + this);
                // nothing to do
                return false;
            }
            // see if we already have a receiver for the node name corresponding to the receiver
            // being registered
            final EJBReceiver existingReceiverForNode = this.getNodeEJBReceiver(receiver.getNodeName(), false);
            if (existingReceiverForNode != null) {
                logger.debug("Skipping registration of receiver " + receiver + " since an EJB receiver already exists for " +
                        "node name " + receiver.getNodeName() + " in client context " + this);
                return false;
            }

            ejbReceiverContext = new EJBReceiverContext(receiver, this);
            association = new ReceiverAssociation(ejbReceiverContext);
            this.ejbReceiverAssociations.put(receiver, association);
            // register a close handler, if any, for this receiver context
            if (receiverContextCloseHandler != null) {
                this.receiverContextCloseHandlers.put(ejbReceiverContext, receiverContextCloseHandler);
            }
        }
        // associate it with a context
        receiver.associate(ejbReceiverContext);

        synchronized (this.ejbReceiverAssociations) {

            association.associated = true;
            // Associating a receiver with a context might be either successful or might fail (for example:
            // failure in version handshake between client/server), in which case the receiver context
            // will be closed and ultimately the association removed from this client context.
            // So registration is successful only if the association is still in the associations map of this
            // client context
            final boolean registered = this.ejbReceiverAssociations.get(receiver) != null;
            // let the EJBClientContextListener(s) know that a receiver was registered
            if (registered) {
                // we *don't* want to send these notification to listeners synchronously since the listeners can be any arbitrary
                // application code and can potential block for a long time. So invoke the listeners asynchronously via our ExecutorService
                final Collection<EJBClientContextListener> listeners = new ArrayList<EJBClientContextListener>(this.ejbClientContextListeners);
                for (final EJBClientContextListener listener : listeners) {
                    this.ejbClientContextTasksExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                listener.receiverRegistered(ejbReceiverContext);
                            } catch (Throwable t) {
                                // log and ignore
                                logger.debug("Exception trying to invoke EJBClientContextListener " + listener + " for EJB client context " + EJBClientContext.this + " on registertation of EJBReceiver " + receiver, t);
                            }
                        }
                    });
                }
            }
            return registered;
        }
    }

    /**
     * Unregister (a previously registered) EJB receiver from this client context.
     * <p/>
     * This EJB client context will not use this unregistered receiver for any subsequent
     * invocations
     *
     * @param receiver The EJB receiver to unregister
     * @throws IllegalArgumentException If the passed <code>receiver</code> is null
     */
    public void unregisterEJBReceiver(final EJBReceiver receiver) {
        if (receiver == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB receiver");
        }
        synchronized (this.ejbReceiverAssociations) {
            final ReceiverAssociation association = this.ejbReceiverAssociations.remove(receiver);
            if (association != null) {
                final EJBReceiverContext receiverContext = association.context;
                final EJBReceiverContextCloseHandler receiverContextCloseHandler = this.receiverContextCloseHandlers.remove(receiverContext);
                if (receiverContextCloseHandler != null) {
                    receiverContextCloseHandler.receiverContextClosed(receiverContext);
                }
                // we *don't* want to send these notification to listeners synchronously since the listeners can be any arbitrary
                // application code and can potential block for a long time. So invoke the listeners asynchronously via our ExecutorService
                final Collection<EJBClientContextListener> listeners = new ArrayList<EJBClientContextListener>(this.ejbClientContextListeners);
                for (final EJBClientContextListener listener : listeners) {
                    this.ejbClientContextTasksExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                listener.receiverUnRegistered(receiverContext);
                            } catch (Throwable t) {
                                // log and ignore
                                logger.debug("Exception trying to invoke EJBClientContextListener " + listener + " for EJB client context " + EJBClientContext.this + " on un-registertation of EJBReceiver " + receiver, t);
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * Register a Remoting connection with this client context.
     *
     * @param connection the connection to register
     */
    @Deprecated
    public void registerConnection(final Connection connection) {
        //TODO: the protocol is hard coded to remote here. If this method is used to
        //add a cconnection that is then used to setup a cluster this could cause issues
        registerEJBReceiver(new RemotingConnectionEJBReceiver(connection, RemotingConnectionEJBReceiver.HTTP_REMOTING));
    }
    /**
     * Register a Remoting connection with this client context.
     *
     * @param connection the connection to register
     * @param remotingProtocol The remoting protocol. Can be 'remote', 'http-remoting' or 'https-remoting'
     */
    public void registerConnection(final Connection connection, String remotingProtocol) {
        //TODO: the protocol is hard coded to remote here. If this method is used to
        //add a cconnection that is then used to setup a cluster this could cause issues
        registerEJBReceiver(new RemotingConnectionEJBReceiver(connection, remotingProtocol));
    }

    /**
     * Register a client interceptor with this client context.
     * <p/>
     * If the passed <code>clientInterceptor</code> is already added to this context with the same <code>priority</code>
     * then this method just returns the old {@link org.jboss.ejb.client.EJBClientInterceptor.Registration}. If however,
     * the <code>clientInterceptor</code> is already registered in this context with a different priority then this method
     * throws an {@link IllegalArgumentException}
     *
     * @param priority          the absolute priority of this interceptor (lower runs earlier; higher runs later)
     * @param clientInterceptor the interceptor to register
     * @return a handle which may be used to later remove this registration
     * @throws IllegalArgumentException if the given interceptor is {@code null}, the priority is less than 0, or the
     *                                  given interceptor is already registered with a different priority
     */
    public EJBClientInterceptor.Registration registerInterceptor(final int priority, final EJBClientInterceptor clientInterceptor) throws IllegalArgumentException {
        // make sure the EJB client context has not been closed
        this.assertNotClosed();

        if (clientInterceptor == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB client interceptor");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_INTERCEPTOR_PERMISSION);
        }
        final EJBClientInterceptor.Registration newRegistration = new EJBClientInterceptor.Registration(this, clientInterceptor, priority);
        EJBClientInterceptor.Registration[] oldRegistrations, newRegistrations;
        do {
            oldRegistrations = registrations;
            for (EJBClientInterceptor.Registration oldRegistration : oldRegistrations) {
                if (oldRegistration.getInterceptor() == clientInterceptor) {
                    if (oldRegistration.compareTo(newRegistration) == 0) {
                        // This means that a client interceptor which has already been added to this context,
                        // is being added with the same priority. In such cases, this new registration request
                        // is effectively a no-op and we just return the old registration
                        return oldRegistration;
                    } else {
                        // This means that a client interceptor which has been added to this context, is being added
                        // again with a different priority. We don't allow that to happen
                        throw Logs.MAIN.ejbClientInterceptorAlreadyRegistered(clientInterceptor);
                    }
                }
            }
            final int length = oldRegistrations.length;
            newRegistrations = Arrays.copyOf(oldRegistrations, length + 1);
            newRegistrations[length] = newRegistration;
            Arrays.sort(newRegistrations);
        } while (!registrationsUpdater.compareAndSet(this, oldRegistrations, newRegistrations));
        return newRegistration;
    }

    /**
     * Returns the {@link EJBClientConfiguration} applicable to this EJB client context. Returns null
     * if this EJB client context isn't configured with a {@link EJBClientConfiguration}
     *
     * @return
     */
    public EJBClientConfiguration getEJBClientConfiguration() {
        return this.ejbClientConfiguration;
    }

    /**
     * Registers a {@link ReconnectHandler} in this {@link EJBClientContext}
     *
     * @param reconnectHandler The reconnect handler. Cannot be null
     */
    public void registerReconnectHandler(final ReconnectHandler reconnectHandler) {
        if (this.closed) {
            if (logger.isTraceEnabled()) {
                logger.trace("EJB client context " + this + " has been closed, reconnect handler " + reconnectHandler + " will not be added to the context");
            }
            return;
        }

        if (reconnectHandler == null) {
            throw Logs.MAIN.paramCannotBeNull("Reconnect handler");
        }
        synchronized (this.reconnectHandlers) {
            this.reconnectHandlers.add(reconnectHandler);
        }
    }

    /**
     * Unregisters a {@link ReconnectHandler} from this {@link EJBClientContext}
     *
     * @param reconnectHandler The reconnect handler to unregister
     */
    public void unregisterReconnectHandler(final ReconnectHandler reconnectHandler) {
        synchronized (this.reconnectHandlers) {
            this.reconnectHandlers.remove(reconnectHandler);
        }
    }

    /**
     * Registers a {@link EJBClientContextListener} for this {@link EJBClientContext}. The {@link EJBClientContextListener}
     * will be notified of lifecycle events related to the {@link EJBClientContext}
     *
     * @param listener The EJB client context listener. Cannot be null.
     * @return Returns true if the passed <code>listener</code> was registered with this EJB client context. False otherwise.
     */
    public boolean registerEJBClientContextListener(final EJBClientContextListener listener) {
        if (listener == null) {
            throw Logs.MAIN.paramCannotBeNull("EJB client context listener");
        }
        return this.ejbClientContextListeners.add(listener);
    }

    void removeInterceptor(final EJBClientInterceptor.Registration registration) {
        EJBClientInterceptor.Registration[] oldRegistrations, newRegistrations;
        do {
            oldRegistrations = registrations;
            newRegistrations = null;
            final int length = oldRegistrations.length;
            final int newLength = length - 1;
            if (length == 1) {
                if (oldRegistrations[0] == registration) {
                    newRegistrations = NO_INTERCEPTORS;
                }
            } else {
                for (int i = 0; i < length; i++) {
                    if (oldRegistrations[i] == registration) {
                        if (i == newLength) {
                            newRegistrations = Arrays.copyOf(oldRegistrations, newLength);
                            break;
                        } else {
                            newRegistrations = new EJBClientInterceptor.Registration[newLength];
                            if (i > 0) System.arraycopy(oldRegistrations, 0, newRegistrations, 0, i);
                            System.arraycopy(oldRegistrations, i + 1, newRegistrations, i, newLength - i);
                            break;
                        }
                    }
                }
            }
            if (newRegistrations == null) {
                return;
            }
        } while (!registrationsUpdater.compareAndSet(this, oldRegistrations, newRegistrations));
    }

    Collection<EJBReceiver> getEJBReceivers(final String appName, final String moduleName, final String distinctName) {
        return this.getEJBReceivers(appName, moduleName, distinctName, true);
    }

    private Collection<EJBReceiver> getEJBReceivers(final String appName, final String moduleName, final String distinctName,
                                                    final boolean attemptReconnect) {

        if (this.closed) {
            if (logger.isTraceEnabled()) {
                logger.trace("EJB client context " + this + " has been closed, returning an empty collection of EJB receivers");
            }
            return Collections.emptySet();
        }

        final Collection<EJBReceiver> eligibleEJBReceivers = new HashSet<EJBReceiver>();
        synchronized (this.ejbReceiverAssociations) {
            for (final Map.Entry<EJBReceiver, ReceiverAssociation> entry : this.ejbReceiverAssociations.entrySet()) {
                if (entry.getValue().associated) {
                    final EJBReceiver ejbReceiver = entry.getKey();
                    if (ejbReceiver.acceptsModule(appName, moduleName, distinctName)) {
                        eligibleEJBReceivers.add(ejbReceiver);
                    }
                }
            }
        }
        if (eligibleEJBReceivers.isEmpty() && attemptReconnect) {
            // we found no receivers, so see if we there are re-connect handlers which can create possible
            // receivers
            this.attemptReconnections();
            // now that the re-connect handlers have run, let's fetch the receivers (if any) for this app/module/distinct-name
            // combination. We won't attempt any reconnections now.
            eligibleEJBReceivers.addAll(this.getEJBReceivers(appName, moduleName, distinctName, false));
        }
        return eligibleEJBReceivers;
    }

    /**
     * Get the first EJB receiver which matches the given combination of app, module and distinct name.
     *
     * @param appName      the application name, or {@code null} for a top-level module
     * @param moduleName   the module name
     * @param distinctName the distinct name, or {@code null} for none
     * @return the first EJB receiver to match, or {@code null} if none match
     */
    EJBReceiver getEJBReceiver(final String appName, final String moduleName, final String distinctName) {
        return this.getEJBReceiver((Collection<String>) null, appName, moduleName, distinctName);
    }

    /**
     * Get the first EJB receiver which matches the given combination of app, module and distinct name. If there's
     * no such EJB receiver, then this method throws a {@link IllegalStateException}
     *
     * @param appName      the application name, or {@code null} for a top-level module
     * @param moduleName   the module name
     * @param distinctName the distinct name, or {@code null} for none
     * @return the first EJB receiver to match
     * @throws IllegalStateException If there's no {@link EJBReceiver} which can handle a EJB for the passed combination
     *                               of app, module and distinct name.
     */
    EJBReceiver requireEJBReceiver(final String appName, final String moduleName, final String distinctName)
            throws IllegalStateException {

        // try and find a receiver which can handle this combination
        final EJBReceiver ejbReceiver = this.getEJBReceiver(appName, moduleName, distinctName);
        if (ejbReceiver == null) {
            throw Logs.MAIN.noEJBReceiverAvailableForDeployment(appName, moduleName, distinctName);
        }
        return ejbReceiver;
    }

    /**
     * Get the first EJB receiver which matches the given combination of app, module and distinct name.
     *
     * @param invocationContext
     * @param appName           the application name, or {@code null} for a top-level module
     * @param moduleName        the module name
     * @param distinctName      the distinct name, or {@code null} for none
     * @return the first EJB receiver to match, or {@code null} if none match
     */
    EJBReceiver getEJBReceiver(final EJBClientInvocationContext invocationContext, final String appName, final String moduleName, final String distinctName) {
        final Iterator<EJBReceiver> iterator = getEJBReceivers(appName, moduleName, distinctName).iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        final Set<String> excludedNodes = invocationContext == null ? Collections.EMPTY_SET : invocationContext.getExcludedNodes();
        final Map<String, EJBReceiver> eligibleReceivers = new HashMap<String, EJBReceiver>();
        while (iterator.hasNext()) {
            final EJBReceiver receiver = iterator.next();
            final String nodeName = receiver.getNodeName();
            // The receiver is not eligible if the invocation context has marked that
            // node as excluded
            if (excludedNodes.contains(nodeName)) {
                logger.debug(nodeName + " is excluded from handling appname=" + appName + ",modulename=" + moduleName +
                        ",distinctname=" + distinctName + " in invocation context " + invocationContext);
                continue;
            }
            // make a note that the receiver is eligible
            eligibleReceivers.put(receiver.getNodeName(), receiver);
        }
        if (eligibleReceivers.isEmpty()) {
            return null;
        }
        // let the deployment node selector, select a node
        final String selectedNode = this.deploymentNodeSelector.selectNode(eligibleReceivers.keySet().toArray(new String[eligibleReceivers.size()]), appName, moduleName, distinctName);
        logger.debug(this.deploymentNodeSelector + " deployment node selector selected " + selectedNode + " node for appname=" + appName + ",modulename=" + moduleName + ",distinctname=" + distinctName);
        // if the deployment node selector picked a node which didn't belong to the eligible receivers
        // then let's just return (a random) eligible node from the iterator
        if (selectedNode == null || selectedNode.trim().isEmpty() || !eligibleReceivers.containsKey(selectedNode)) {
            logger.debug("Selected node " + selectedNode + " doesn't belong to eligible receivers. Continuing with a random eligible receiver");
            return iterator.next();
        }
        return eligibleReceivers.get(selectedNode);
    }

    /**
     * Get the first EJB receiver which matches the given combination of app, module and distinct name. If there's
     * no such EJB receiver, then this method throws a {@link IllegalStateException}
     *
     * @param appName      the application name, or {@code null} for a top-level module
     * @param moduleName   the module name
     * @param distinctName the distinct name, or {@code null} for none
     * @return the first EJB receiver to match
     * @throws IllegalStateException If there's no {@link EJBReceiver} which can handle a EJB for the passed combination
     *                               of app, module and distinct name.
     */
    EJBReceiver requireEJBReceiver(final EJBClientInvocationContext clientInvocationContext, final String appName, final String moduleName, final String distinctName)
            throws IllegalStateException {

        // try and find a receiver which can handle this combination
        final EJBReceiver ejbReceiver = this.getEJBReceiver(clientInvocationContext, appName, moduleName, distinctName);
        if (ejbReceiver == null) {
            throw Logs.MAIN.noEJBReceiverAvailableForDeploymentDuringInvocation(appName, moduleName, distinctName, clientInvocationContext);
        }
        return ejbReceiver;
    }

    /**
     * Get the first EJB receiver which matches the given combination of app, module and distinct name.
     *
     * @param excludedNodeNames The node names of the EJBReceiver(s) which should be ignored while selecting the eligible receivers. Can be null or empty collection.
     * @param appName           the application name, or {@code null} for a top-level module
     * @param moduleName        the module name
     * @param distinctName      the distinct name, or {@code null} for none
     * @return the first EJB receiver to match, or {@code null} if none match
     */
    EJBReceiver getEJBReceiver(final Collection<String> excludedNodeNames, final String appName, final String moduleName, final String distinctName) {
        final Iterator<EJBReceiver> iterator = getEJBReceivers(appName, moduleName, distinctName).iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        final Map<String, EJBReceiver> eligibleReceivers = new HashMap<String, EJBReceiver>();
        while (iterator.hasNext()) {
            final EJBReceiver receiver = iterator.next();
            final String nodeName = receiver.getNodeName();
            // The receiver is not eligible if this is an excluded node
            if (excludedNodeNames != null && excludedNodeNames.contains(nodeName)) {
                logger.debugf("%s has been asked to be excluded from handling appName=%s, moduleName=%s=, distinctName=%s", nodeName, appName, moduleName, distinctName);
                continue;
            }
            // make a note that the receiver is eligible
            eligibleReceivers.put(receiver.getNodeName(), receiver);
        }
        if (eligibleReceivers.isEmpty()) {
            return null;
        }
        // let the deployment node selector, select a node
        final String selectedNode = this.deploymentNodeSelector.selectNode(eligibleReceivers.keySet().toArray(new String[eligibleReceivers.size()]), appName, moduleName, distinctName);
        logger.debugf("%s deployment node selector selected %s node for appname=%s, modulename=%s, distinctName=%s", this.deploymentNodeSelector, selectedNode, appName, moduleName, distinctName);
        // if the deployment node selector picked a node which didn't belong to the eligible receivers
        // then let's just return (a random) eligible node from the iterator
        if (selectedNode == null || selectedNode.trim().isEmpty() || !eligibleReceivers.containsKey(selectedNode)) {
            logger.debugf("Selected node %s doesn't belong to eligible receivers. Continuing with a random eligible receiver", selectedNode);
            return iterator.next();
        }
        return eligibleReceivers.get(selectedNode);
    }

    /**
     * Get the first EJB receiver which matches the given combination of app, module and distinct name. If there's
     * no such EJB receiver, then this method throws a {@link IllegalStateException}
     *
     * @param excludedNodeNames The node names of the EJBReceiver(s) which should be ignored while selecting the eligible receivers. Can be null or empty collection.
     * @param appName           the application name, or {@code null} for a top-level module
     * @param moduleName        the module name
     * @param distinctName      the distinct name, or {@code null} for none
     * @return the first EJB receiver to match
     * @throws IllegalStateException If there's no {@link EJBReceiver} which can handle a EJB for the passed combination
     *                               of app, module and distinct name.
     */
    EJBReceiver requireEJBReceiver(final Collection<String> excludedNodeNames, final String appName, final String moduleName, final String distinctName)
            throws IllegalStateException {

        // try and find a receiver which can handle this combination
        final EJBReceiver ejbReceiver = this.getEJBReceiver(excludedNodeNames, appName, moduleName, distinctName);
        if (ejbReceiver == null) {
            throw Logs.MAIN.noEJBReceiverAvailableForDeployment(appName, moduleName, distinctName);
        }
        return ejbReceiver;
    }

    /**
     * Returns a {@link EJBReceiverContext} for the passed <code>receiver</code>. If the <code>receiver</code>
     * hasn't been registered with this {@link EJBClientContext}, either through a call to {@link #registerConnection(org.jboss.remoting3.Connection, String)}
     * or to {@link #requireEJBReceiver(String, String, String)}, then this method throws an {@link IllegalStateException}
     * <p/>
     *
     * @param receiver The {@link EJBReceiver} for which the {@link EJBReceiverContext} is being requested
     * @return The {@link EJBReceiverContext}
     * @throws IllegalStateException If the passed <code>receiver</code> hasn't been registered with this {@link EJBClientContext}
     */
    EJBReceiverContext requireEJBReceiverContext(final EJBReceiver receiver) throws IllegalStateException {
        // make sure the EJB client context has not been closed
        this.assertNotClosed();

        synchronized (this.ejbReceiverAssociations) {
            final ReceiverAssociation association = this.ejbReceiverAssociations.get(receiver);
            if (association == null) {
                throw Logs.MAIN.receiverNotAssociatedWithClientContext(receiver, this);
            }
            return association.context;
        }
    }

    EJBReceiver requireNodeEJBReceiver(final String nodeName) {
        final EJBReceiver receiver = getNodeEJBReceiver(nodeName);
        if (receiver != null) return receiver;
        throw Logs.MAIN.noEJBReceiverForNode(nodeName);
    }

    EJBReceiver getNodeEJBReceiver(final String nodeName) {
        return this.getNodeEJBReceiver(nodeName, true);
    }

    private EJBReceiver getNodeEJBReceiver(final String nodeName, final boolean attemptReconnect) {
        if (this.closed) {
            if (logger.isTraceEnabled()) {
                logger.trace("EJB client context " + this + " has been closed, no EJB receiver will be returned for node name " + nodeName);
            }
            return null;
        }
        if (nodeName == null) {
            throw Logs.MAIN.paramCannotBeNull("Node name");
        }

        synchronized (this.ejbReceiverAssociations) {
            for (final Map.Entry<EJBReceiver, ReceiverAssociation> entry : this.ejbReceiverAssociations.entrySet()) {
                if (entry.getValue().associated) {
                    final EJBReceiver ejbReceiver = entry.getKey();
                    if (nodeName.equals(ejbReceiver.getNodeName())) {
                        return ejbReceiver;
                    }
                }
            }
        }
        // no EJB receiver found for the node name, so let's see if there are re-connect handlers which can
        // create the EJB receivers
        if (attemptReconnect) {
            this.attemptReconnections();
            // now that re-connections have been attempted, let's fetch any EJB receiver for this node name.
            // we won't try reconnecting again now
            return this.getNodeEJBReceiver(nodeName, false);
        }

        return null;
    }

    EJBReceiverContext requireNodeEJBReceiverContext(final String nodeName) {
        final EJBReceiver ejbReceiver = requireNodeEJBReceiver(nodeName);
        return requireEJBReceiverContext(ejbReceiver);
    }

    EJBReceiverContext getNodeEJBReceiverContext(final String nodeName) {
        final EJBReceiver ejbReceiver = getNodeEJBReceiver(nodeName);
        return ejbReceiver == null ? null : requireEJBReceiverContext(ejbReceiver);
    }

    /**
     * Returns true if the <code>nodeName</code> belongs to a cluster named <code>clusterName</code>. Else
     * returns false.
     *
     * @param clusterName The name of the cluster
     * @param nodeName    The name of the node within the cluster
     * @return
     */
    boolean clusterContains(final String clusterName, final String nodeName) {
        if (this.closed) {
            if (logger.isTraceEnabled()) {
                logger.trace("EJB client context " + this + " has been closed, node named " + nodeName + " is not considered part of cluster named " + clusterName);
            }
            return false;
        }

        final ClusterContext clusterContext = this.clusterContexts.get(clusterName);
        if (clusterContext == null) {
            return false;
        }
        return clusterContext.isNodeAvailable(nodeName);
    }

    /**
     * Returns a {@link EJBReceiverContext} for the <code>clusterName</code>. If there's no such receiver context
     * for the cluster, then this method returns null
     *
     * @param clusterName The name of the cluster
     * @return
     */
    EJBReceiverContext getClusterEJBReceiverContext(final String clusterName) throws IllegalArgumentException {
        return this.getClusterEJBReceiverContext(null, clusterName);
    }

    /**
     * Returns a {@link EJBReceiverContext} for the <code>clusterName</code>. If there's no such receiver context
     * for the cluster, then this method returns null
     *
     * @param invocationContext
     * @param clusterName       The name of the cluster
     * @return
     */
    EJBReceiverContext getClusterEJBReceiverContext(final EJBClientInvocationContext invocationContext, final String clusterName) throws IllegalArgumentException {
        return this.getClusterEJBReceiverContext(invocationContext, clusterName, true);
    }

    private EJBReceiverContext getClusterEJBReceiverContext(final EJBClientInvocationContext invocationContext, final String clusterName, final boolean attemptReconnect) throws IllegalArgumentException {
        if (this.closed) {
            if (logger.isTraceEnabled()) {
                logger.trace("EJB client context " + this + " has been closed, returning no EJB receiver for cluster named " + clusterName);
            }
            return null;
        }

        final ClusterContext clusterContext = this.clusterContexts.get(clusterName);
        if (clusterContext == null) {
            return null;
        }
        final EJBReceiverContext ejbReceiverContext = clusterContext.getEJBReceiverContext(invocationContext);
        if (ejbReceiverContext == null && attemptReconnect) {
            // no receiver context was found for the cluster. So let's see if there are any re-connect handlers
            // which can generate the EJB receivers
            this.attemptReconnections();
            // now that we have attempted the re-connections, let's fetch any EJB receiver context for the cluster.
            // we won't try re-connecting again now
            return this.getClusterEJBReceiverContext(invocationContext, clusterName, false);
        }
        return ejbReceiverContext;
    }

    /**
     * Returns a {@link EJBReceiverContext} for the <code>clusterName</code>. If there's no such receiver context
     * for the cluster, then this method throws an {@link IllegalArgumentException}
     *
     * @param clusterName The name of the cluster
     * @return
     * @throws IllegalArgumentException If there's no EJB receiver context available for the cluster
     */
    EJBReceiverContext requireClusterEJBReceiverContext(final String clusterName) throws IllegalArgumentException {
        return this.requireClusterEJBReceiverContext(null, clusterName);
    }

    /**
     * Returns a {@link EJBReceiverContext} for the <code>clusterName</code>. If there's no such receiver context
     * for the cluster, then this method throws an {@link IllegalArgumentException}
     *
     * @param invocationContext
     * @param clusterName       The name of the cluster
     * @return
     * @throws IllegalArgumentException If there's no EJB receiver context available for the cluster
     */
    EJBReceiverContext requireClusterEJBReceiverContext(final EJBClientInvocationContext invocationContext, final String clusterName) throws IllegalArgumentException {
        // make sure the EJB client context has not been closed
        this.assertNotClosed();

        ClusterContext clusterContext = this.clusterContexts.get(clusterName);
        if (clusterContext == null) {
            // let's wait for some time to see if the asynchronous cluster topology becomes available.
            // Note that this isn't a great thing to do for clusters which might have been removed or for clusters
            // which will never be formed, since this wait results in a 5 second delay in the invocation. But ideally
            // such cases should be pretty low.
            logger.debug("Waiting for cluster topology information to be available for cluster named " + clusterName);
            this.waitForClusterTopology(clusterName);
            // see if the cluster context was created during this wait time
            clusterContext = this.clusterContexts.get(clusterName);
            if (clusterContext == null) {
                throw Logs.MAIN.noClusterContextAvailable(clusterName);
            }
        }
        final EJBReceiverContext ejbReceiverContext = this.getClusterEJBReceiverContext(invocationContext, clusterName);
        if (ejbReceiverContext == null) {
            throw Logs.MAIN.noReceiverContextsInCluster(clusterName);
        }
        return ejbReceiverContext;
    }

    /**
     * Returns the interceptor chain consisting of {@link EJBClientInterceptor}s which have been either explicitly {@link #registerInterceptor(int, EJBClientInterceptor) registered}
     * or have been found in the classpath of the application via the {@link ServiceLoader} for {@link EJBClientInterceptor}s. The {@ink EJBClientInterceptor}s
     * found in the classpath will be added to the end of the chain, in the order they were retrieved by the {@link ServiceLoader}
     *
     * @return
     */
    EJBClientInterceptor[] getInterceptorChain() {
        // todo optimize to eliminate copy
        final EJBClientInterceptor.Registration[] registeredInterceptors = this.registrations;
        final int totalInterceptorLength = this.clientInterceptorsInClasspath != null ? registeredInterceptors.length + this.clientInterceptorsInClasspath.size() : registeredInterceptors.length;
        final EJBClientInterceptor[] interceptors = new EJBClientInterceptor[totalInterceptorLength];
        // The interceptors which have been added in a specific priority/order go first.
        for (int i = 0; i < registeredInterceptors.length; i++) {
            interceptors[i] = registeredInterceptors[i].getInterceptor();
        }
        // lastly all the interceptors (if any) which were on the classpath and loaded via the ServiceLoader are added to the end of the chain.
        if (this.clientInterceptorsInClasspath != null && !this.clientInterceptorsInClasspath.isEmpty()) {
            int i = registeredInterceptors.length;
            for (final EJBClientInterceptor interceptor : this.clientInterceptorsInClasspath) {
                interceptors[i++] = interceptor;
            }
        }
        return interceptors;
    }

    /**
     * Returns a {@link ClusterContext} corresponding to the passed <code>clusterName</code>. If no
     * such cluster context exists, a new one is created and returned. Subsequent invocations on this
     * {@link EJBClientContext} for the same cluster name will return this same {@link ClusterContext}, unless
     * the cluster has been removed from this client context.
     *
     * @param clusterName The name of the cluster
     * @return
     */
    public synchronized ClusterContext getOrCreateClusterContext(final String clusterName) {
        // make sure the EJB client context has not been closed
        this.assertNotClosed();

        ClusterContext clusterContext = this.clusterContexts.get(clusterName);
        if (clusterContext == null) {
            clusterContext = new ClusterContext(clusterName, this, this.ejbClientConfiguration);
            // register a listener which will trigger a notification when nodes are added to the cluster
            clusterContext.registerListener(this.clusterFormationNotifier);
            this.clusterContexts.put(clusterName, clusterContext);
        }
        return clusterContext;
    }

    /**
     * Returns a {@link ClusterContext} corresponding to the passed <code>clusterName</code>. If no
     * such cluster context exists, then this method returns null.
     *
     * @param clusterName The name of the cluster
     * @return
     */
    public synchronized ClusterContext getClusterContext(final String clusterName) {
        if (this.closed) {
            if (logger.isTraceEnabled()) {
                logger.trace("EJB client context " + this + " has been closed, returning no cluster context for cluster named " + clusterName);
            }
            return null;
        }

        return this.clusterContexts.get(clusterName);
    }

    /**
     * Removes the cluster identified by the <code>clusterName</code> from this client context
     *
     * @param clusterName The name of the cluster
     */
    public synchronized void removeCluster(final String clusterName) {
        final ClusterContext clusterContext = this.clusterContexts.remove(clusterName);
        if (clusterContext == null) {
            return;
        }
        try {
            // close the cluster context to allow it to cleanup any resources
            clusterContext.close();
        } catch (Throwable t) {
            // ignore
            logger.debug("Ignoring an error that occured while closing a cluster context for cluster named " + clusterName, t);
        }
    }

    /**
     * Waits for (a maximum of 5 seconds for) a cluster topology to be available for <code>clusterName</code>
     *
     * @param clusterName The name of the cluster
     */
    private void waitForClusterTopology(final String clusterName) {
        final CountDownLatch clusterFormationLatch = new CountDownLatch(1);
        // register for the notification
        this.clusterFormationNotifier.registerForClusterFormation(clusterName, clusterFormationLatch);
        // now wait (max 5 seconds)
        try {
            final boolean receivedClusterTopology = clusterFormationLatch.await(5, TimeUnit.SECONDS);
            if (receivedClusterTopology) {
                logger.debug("Received the cluster topology for cluster named " + clusterName + " during the wait time");
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            // unregister from the cluster formation notification
            this.clusterFormationNotifier.unregisterFromClusterNotification(clusterName, clusterFormationLatch);
        }
    }

    private synchronized void attemptReconnections() {
        if (this.closed) {
            if (logger.isTraceEnabled()) {
                logger.trace("EJB client context " + this + " has been closed, no reconnections, to register EJB receivers, will be attempted");
            }
            return;
        }

        final CountDownLatch reconnectTasksCompletionNotifierLatch;
        final List<ReconnectHandler> reconnectHandlersToAttempt = new ArrayList<ReconnectHandler>(this.reconnectHandlers);
        if (reconnectHandlersToAttempt.isEmpty()) {
            // no re-connections to attempt, just return
            return;
        }
        reconnectTasksCompletionNotifierLatch = new CountDownLatch(reconnectHandlersToAttempt.size());
        for (final ReconnectHandler reconnectHandler : reconnectHandlersToAttempt) {
            // submit each of the re-connection tasks
            this.ejbClientContextTasksExecutorService.submit(new ReconnectAttempt(reconnectHandler, reconnectTasksCompletionNotifierLatch));
        }
        // wait for all tasks to complete (with a upper bound on time limit)
        try {
            long reconnectWaitTimeout = 10000; // default 10 seconds
            if (this.ejbClientConfiguration != null && this.ejbClientConfiguration.getReconnectTasksTimeout() > 0) {
                reconnectWaitTimeout = this.ejbClientConfiguration.getReconnectTasksTimeout();
            }
            reconnectTasksCompletionNotifierLatch.await(reconnectWaitTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * Closes the EJB client context and notifies any registered {@link EJBClientContextListener}s about
     * the context being closed.
     *
     * @throws IOException
     */
    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        // mark this context as closed. The real cleanup like closing of EJB receivers
        // *isn't* the responsibility of the EJB client context. We'll just let our EJBClientContextListeners
        // (if any) know about the context being closed and let them handle closing the receivers if they want to
        this.closed = true;

        for (final EJBClientContextListener listener : this.ejbClientContextListeners) {
            try {
                listener.contextClosed(this);
            } catch (Throwable t) {
                logger.debug("Ignoring the exception thrown by an EJB client context listener while closing the context " + this, t);
            }
        }

        // close the executor we use for reconnect handlers
        this.ejbClientContextTasksExecutorService.shutdownNow();

    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                this.close();
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * Throws a {@link IllegalStateException} if this EJB client context is closed. Else just returns.
     */
    private void assertNotClosed() {
        if (this.closed) {
            throw Logs.MAIN.ejbClientContextIsClosed(this);
        }
    }

    /**
     * A {@link EJBReceiverContextCloseHandler} will be notified through a call to
     * {@link #receiverContextClosed(EJBReceiverContext)} whenever a {@link EJBReceiverContext}, to which
     * the {@link EJBReceiverContextCloseHandler}, has been {@link EJBClientContext#registerEJBReceiver(EJBReceiver, org.jboss.ejb.client.EJBClientContext.EJBReceiverContextCloseHandler) associated}
     * is closed by this {@link EJBClientContext}
     */
    interface EJBReceiverContextCloseHandler {
        /**
         * A callback method which will be invoked when the {@link EJBReceiverContext receiverContext}
         * is closed. This method can do the necessary cleanup (if any) of resources associated with the
         * receiver context
         *
         * @param receiverContext The receiver context which was closed
         */
        void receiverContextClosed(final EJBReceiverContext receiverContext);
    }

    private static final class ReceiverAssociation {
        final EJBReceiverContext context;
        boolean associated = false;

        private ReceiverAssociation(final EJBReceiverContext context) {
            this.context = context;
        }
    }

    /**
     * A notifier which can be used for waiting for cluster formation events
     */
    private final class ClusterFormationNotifier implements ClusterContext.ClusterContextListener {

        private final Map<String, List<CountDownLatch>> clusterFormationListeners = new HashMap<String, List<CountDownLatch>>();

        /**
         * Register for cluster formation event notification.
         *
         * @param clusterName The name of the cluster
         * @param latch       The {@link CountDownLatch} which the caller can use to wait for the cluster formation
         *                    to take place. The {@link ClusterFormationNotifier} will invoke the {@link java.util.concurrent.CountDownLatch#countDown()}
         *                    when the cluster is formed
         */
        void registerForClusterFormation(final String clusterName, final CountDownLatch latch) {
            synchronized (this.clusterFormationListeners) {
                List<CountDownLatch> listeners = this.clusterFormationListeners.get(clusterName);
                if (listeners == null) {
                    listeners = new ArrayList<CountDownLatch>();
                    this.clusterFormationListeners.put(clusterName, listeners);
                }
                listeners.add(latch);
            }
        }

        /**
         * Callback invocation for the cluster formation event. This method will invoke {@link java.util.concurrent.CountDownLatch#countDown()}
         * on each of the waiting {@link CountDownLatch} for the cluster.
         *
         * @param clusterName The name of the cluster
         */
        void notifyClusterFormation(final String clusterName) {
            final List<CountDownLatch> listeners;
            synchronized (this.clusterFormationListeners) {
                // remove the waiting listeners
                listeners = this.clusterFormationListeners.remove(clusterName);
            }
            if (listeners == null) {
                return;
            }
            // notify any waiting listeners
            for (final CountDownLatch latch : listeners) {
                latch.countDown();
            }
        }

        /**
         * Unregisters from cluster formation notifications for the cluster
         *
         * @param clusterName The name of the cluster
         * @param latch       The {@link CountDownLatch} which will be unregistered from the waiting {@link CountDownLatch}es
         */
        void unregisterFromClusterNotification(final String clusterName, final CountDownLatch latch) {
            synchronized (this.clusterFormationListeners) {
                final List<CountDownLatch> listeners = this.clusterFormationListeners.get(clusterName);
                if (listeners == null) {
                    return;
                }
                listeners.remove(latch);
            }
        }

        @Override
        public void clusterNodesAdded(String clusterName, ClusterNodeManager... nodes) {
            this.notifyClusterFormation(clusterName);
        }
    }

    private class ReconnectAttempt implements Runnable {

        private final ReconnectHandler reconnectHandler;
        private final CountDownLatch taskCompletionNotifierLatch;

        ReconnectAttempt(final ReconnectHandler reconnectHandler, final CountDownLatch taskCompletionNotifierLatch) {
            this.reconnectHandler = reconnectHandler;
            this.taskCompletionNotifierLatch = taskCompletionNotifierLatch;
        }

        @Override
        public void run() {
            try {
                this.reconnectHandler.reconnect();
            } catch (Exception e) {
                logger.debug("Exception trying to re-establish a connection from EJB client context " + EJBClientContext.this, e);
            } finally {
                this.taskCompletionNotifierLatch.countDown();
            }
        }
    }
}
