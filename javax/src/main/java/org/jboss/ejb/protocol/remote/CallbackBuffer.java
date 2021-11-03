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

package org.jboss.ejb.protocol.remote;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jboss.ejb._private.Logs;
import org.wildfly.common.Assert;

/**
 * A callback buffer is a one-time switch that buffers up a list of asynchronous callbacks until the switch is flipped,
 * at which time all of the callbacks will be fired one by one.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class CallbackBuffer {
    private final AtomicReference<State> stateRef = new AtomicReference<>(INITIAL);

    public void activate() {
        stateRef.get().activate(stateRef);
    }

    public <T, U> void addListener(final BiConsumer<T, U> consumer, final T p1, final U p2) {
        Assert.checkNotNullParam("consumer", consumer);
        stateRef.get().addListener(stateRef, consumer, p1, p2);
    }

    public <T> void addListener(final Consumer<T> consumer, final T p1) {
        addListener(Consumer::accept, consumer, p1);
    }

    public void addListener(final Runnable runnable) {
        addListener(Runnable::run, runnable);
    }

    // the actual work

    private static final State COMPLETE = new State();

    private static final UnfinishedState INITIAL = new UnfinishedState();

    static class State {
        State() {
        }

        void activate(final AtomicReference<State> stateRef) {
            // no operation
        }

        <T, U> void addListener(AtomicReference<State> stateRef, BiConsumer<T, U> consumer, T p1, U p2) {
            Logs.REMOTING.tracef("Running callback %s(%s, %s)", consumer, p1, p2);
            consumer.accept(p1, p2);
        }
    }

    static class UnfinishedState extends State {
        UnfinishedState() {
        }

        final void activate(final AtomicReference<State> stateRef) {
            if (stateRef.compareAndSet(this, COMPLETE)) {
                activated();
            } else {
                stateRef.get().activate(stateRef);
            }
        }

        void activated() {}

        <T, U> void addListener(final AtomicReference<State> stateRef, final BiConsumer<T, U> consumer, final T p1, final U p2) {
            if (! stateRef.compareAndSet(this, new UnfinishedWithListenerState<T, U>(this,consumer, p1, p2))) {
                stateRef.get().addListener(stateRef, consumer, p1, p2);
            }
            Logs.REMOTING.tracef("Added callback (delayed) %s(%s, %s)", consumer, p1, p2);
        }
    }

    static class UnfinishedWithListenerState<T, U> extends UnfinishedState {
        private final UnfinishedState next;
        private final BiConsumer<T, U> consumer;
        private final T p1;
        private final U p2;

        UnfinishedWithListenerState(final UnfinishedState next, final BiConsumer<T, U> consumer, final T p1, final U p2) {
            this.next = next;
            this.consumer = consumer;
            this.p1 = p1;
            this.p2 = p2;
        }

        void activated() {
            try {
                Logs.REMOTING.tracef("Running callback (delayed) %s(%s, %s)", consumer, p1, p2);
                consumer.accept(p1, p2);
            } finally {
                next.activated();
            }
        }
    }
}
