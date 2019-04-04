package org.jboss.ejb.protocol.remote;

import org.jboss.ejb._private.Logs;
import org.jboss.ejb.client.EJBClientContext;
import org.wildfly.common.context.ContextManager;
import org.wildfly.discovery.Discovery;
import org.wildfly.security.auth.client.AuthenticationContext;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A class for queuing retry operations as well as transferring various necessary contexts from calling thread to executor thread.
 *
 * @author Stuart Douglas
 * @author Richard Achmatowicz
 */
class RetryExecutorWrapper {

    private final Object lock = new Object();
    private Task last = null;

    /**
     * A version of getExecutor() which does not modify the thread contexts of the executor thread
     *
     * @param executor the executor used to execute the runnable
     * @return the modified executor
     */
    Executor getExecutor(Executor executor) {
        if (Logs.INVOCATION.isTraceEnabled()) {
            Logs.INVOCATION.tracef("RetryExecutorWrapper: calling getExecutor(executor= %s)", executor.getClass().getName());
        }
        return runnable -> {
            synchronized (lock) {
                // create a new task
                Task task = new Task(runnable, executor);
                if (last != null) {
                    last.next = task;
                    last = task;
                } else {
                    last = task;
                    executor.execute(task);
                }
            }
        };
    }

    /**
     * A version of getExecutor which allows transferring thread contexts from the calling tread to the executor thread
     *
     * @param executor  the executor used to execute the runnable
     * @param ejbClientContext the EJBClientContext to attach to the executor thread
     * @param discovery the Discovery context to attach to the executor thread
     * @param authenticationContext the AuthenticationContext to attach to the executor thread
     * @return the modified executor
     */
    Executor getExecutor(Executor executor, EJBClientContext ejbClientContext, Discovery discovery, AuthenticationContext authenticationContext) {
        if (Logs.INVOCATION.isTraceEnabled()) {
            Logs.INVOCATION.tracef("RetryExecutorWrapper: calling getExecutor(executor= %s, ejbClientContext = %s, discovery = %s, authenticationContext = %s)",
                    executor.getClass().getName(), ejbClientContext, discovery, authenticationContext);
        }
        return runnable -> {
            synchronized (lock) {
                // provide the caller's context to the executor thread
                Runnable runnableWithContext = wrapExecutorThreadWithCallerContext(runnable, ejbClientContext, discovery, authenticationContext);

                // create a new task
                Task task = new Task(runnableWithContext, executor);
                if (last != null) {
                    last.next = task;
                    last = task;
                } else {
                    last = task;
                    executor.execute(task);
                }
            }
        };
    }

    private Runnable wrapExecutorThreadWithCallerContext(Runnable runnable, EJBClientContext callerEJBClientContext, Discovery callerDiscovery, AuthenticationContext callerAuthenticationContext) {

        Runnable callerContextTask = () -> {

            // get a copy of the executor thread context
            EJBClientContext executorEJBClientContext = EJBClientContext.getContextManager().getThreadDefault();
            Discovery executorDiscovery = Discovery.getContextManager().getThreadDefault();
            AuthenticationContext executorAuthenticationContext = AuthenticationContext.getContextManager().getThreadDefault();

            // set the context on the executor thread
            EJBClientContext.getContextManager().setThreadDefault(callerEJBClientContext);
            Discovery.getContextManager().setThreadDefault(callerDiscovery);
            AuthenticationContext.getContextManager().setThreadDefault(callerAuthenticationContext);

            // run the code
            runnable.run();

            // reset the original executor context
            EJBClientContext.getContextManager().setThreadDefault(executorEJBClientContext);
            Discovery.getContextManager().setThreadDefault(executorDiscovery);
            AuthenticationContext.getContextManager().setThreadDefault(executorAuthenticationContext);
        };
        return callerContextTask;
    }


    private class Task implements Runnable {

        private final Runnable runnable;
        private final Executor delegate;
        private Task next;

        private Task(Runnable runnable, Executor delegate) {
            this.runnable = runnable;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                runnable.run();
            } catch (Throwable t) {
                Logs.MAIN.taskFailed(runnable, t);
            } finally {
                synchronized (lock) {
                    if (last == this) {
                        last = null;
                    }
                    if (next != null) {
                        next.delegate.execute(next);
                    }
                }
            }
        }
    }
}
