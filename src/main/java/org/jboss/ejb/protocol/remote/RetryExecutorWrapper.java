package org.jboss.ejb.protocol.remote;

import org.jboss.ejb._private.Logs;

import java.util.concurrent.Executor;

/**
 * @author Stuart Douglas
 */
public class RetryExecutorWrapper {

    private final Object lock = new Object();
    private Task last = null;

    public Executor getExecutor(Executor executor) {
        return runnable -> {
            synchronized (lock) {
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
