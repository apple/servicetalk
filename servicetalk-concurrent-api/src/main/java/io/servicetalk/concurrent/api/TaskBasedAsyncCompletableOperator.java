/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeCancel;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 *  Asynchronous operator for {@link Completable} that processes signals with task based offloading.
 *
 *  <p>This implementation uses <i>task based</i> offloading. Signals are delivered on a thread owned by the provided
 *  {@link Executor} invoked via the {@link Executor#execute(Runnable)} method independently for each signal.
 *  No assumption should be made by applications that a consistent thread will be used for subsequent signals.
 */
abstract class TaskBasedAsyncCompletableOperator extends AbstractNoHandleSubscribeCompletable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskBasedAsyncCompletableOperator.class);

    private final Completable original;
    private final BooleanSupplier shouldOffload;
    private final Executor executor;

    TaskBasedAsyncCompletableOperator(final Completable original,
                                      final BooleanSupplier shouldOffload,
                                      final Executor executor) {
        this.original = original;
        this.shouldOffload = Objects.requireNonNull(shouldOffload, "shouldOffload");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    final BooleanSupplier shouldOffload() {
        return shouldOffload;
    }

    final Executor executor() {
        return executor;
    }

    @Override
    void handleSubscribe(final Subscriber subscriber,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {

        original.delegateSubscribe(subscriber, contextMap, contextProvider);
    }

    abstract static class AbstractOffloadedSingleValueSubscriber {
        private static final int ON_SUBSCRIBE_RECEIVED_MASK = 8;
        private static final int EXECUTING_MASK = 16;
        private static final int RECEIVED_TERMINAL_MASK = 32;
        private static final int EXECUTING_SUBSCRIBED_RECEIVED_MASK = EXECUTING_MASK | ON_SUBSCRIBE_RECEIVED_MASK;

        private static final int STATE_INIT = 0;
        private static final int STATE_AWAITING_TERMINAL = 1;
        private static final int STATE_TERMINATED = 2;
        private static final AtomicIntegerFieldUpdater<AbstractOffloadedSingleValueSubscriber> stateUpdater =
                newUpdater(AbstractOffloadedSingleValueSubscriber.class, "state");

        private final BooleanSupplier shouldOffload;
        final Executor executor;
        @Nullable
        // Visibility: Task submitted to executor happens-before task execution.
        private Cancellable cancellable;
        @Nullable
        private Object terminal;
        private volatile int state = STATE_INIT;
        private boolean hasOffloaded;

        AbstractOffloadedSingleValueSubscriber(final BooleanSupplier shouldOffload, final Executor executor) {
            this.shouldOffload = shouldOffload;
            this.executor = executor;
        }

        private boolean shouldOffload() {
            if (!hasOffloaded) {
                try {
                    if (!shouldOffload.getAsBoolean()) {
                        return false;
                    }
                    hasOffloaded = true;
                } catch (Throwable throwable) {
                    LOGGER.warn("Offloading hint BooleanSupplier {} threw", shouldOffload, throwable);
                    // propagate the failure so that subscription is cancelled.
                    throw throwable;
                }
            }
            return true;
        }

        public final void onSubscribe(final Cancellable cancellable) {
            this.cancellable = cancellable;
            state = ON_SUBSCRIBE_RECEIVED_MASK;
            try {
                if (shouldOffload()) {
                    executor.execute(this::deliverSignals);
                } else {
                    deliverSignals();
                }
            } catch (Throwable t) {
                // As a policy, we call the target in the calling thread when the executor is inadequately
                // provisioned. In the future we could make this configurable.
                state = STATE_TERMINATED;
                sendOnSubscribe(IGNORE_CANCEL);
                terminateOnEnqueueFailure(t);
            }
        }

        private void deliverSignals() {
            while (true) {
                int cState = state;
                if (cState == STATE_TERMINATED) {
                    return;
                }
                if (!casAppend(cState, EXECUTING_MASK)) {
                    continue;
                }
                cState |= EXECUTING_MASK;
                if (has(cState, ON_SUBSCRIBE_RECEIVED_MASK)) {
                    while (!casRemove(cState, ON_SUBSCRIBE_RECEIVED_MASK)) {
                        cState = state;
                    }
                    assert cancellable != null;
                    sendOnSubscribe(cancellable);
                    // Re-read state to see if we terminated from onSubscribe
                    cState = state;
                }
                if (has(cState, RECEIVED_TERMINAL_MASK)) {
                    if (casSet(cState, STATE_TERMINATED)) {
                        assert terminal != null;
                        deliverTerminalToSubscriber(terminal);
                        return;
                    }
                } else if (casSet(cState, STATE_AWAITING_TERMINAL)) {
                    return;
                }
            }
        }

        final void terminal(final Object terminal) {
            this.terminal = terminal;
            while (true) {
                int cState = state;
                if (// Duplicate terminal event
                        has(cState, RECEIVED_TERMINAL_MASK) || cState == STATE_TERMINATED ||
                                // Already executing or enqueued for executing, append the state.
                                hasAny(cState, EXECUTING_SUBSCRIBED_RECEIVED_MASK) &&
                                        casAppend(cState, RECEIVED_TERMINAL_MASK)) {
                    return;
                } else if ((cState == STATE_AWAITING_TERMINAL || cState == STATE_INIT) &&
                        casSet(cState, RECEIVED_TERMINAL_MASK)) {
                    // Either we have seen onSubscribe and the {@link #deliverSignals} is no longer executing, or we
                    // have not seen onSubscribe and there is a sequencing issue on the Subscriber. Either way we avoid
                    // looping and deliver the terminal event.
                    try {
                        if (shouldOffload()) {
                            executor.execute(this::deliverSignals);
                        } else {
                            deliverSignals();
                        }
                    } catch (Throwable t) {
                        state = STATE_TERMINATED;
                        // As a policy, we call the target in the calling thread when the executor is inadequately
                        // provisioned. In the future we could make this configurable.
                        terminateOnEnqueueFailure(t);
                    }
                    return;
                }
            }
        }

        final void onSubscribeFailed() {
            state = STATE_TERMINATED;
        }

        abstract void terminateOnEnqueueFailure(Throwable cause);

        abstract void deliverTerminalToSubscriber(Object terminal);

        abstract void sendOnSubscribe(Cancellable cancellable);

        private boolean casSet(int cState, int toState) {
            return stateUpdater.compareAndSet(this, cState, toState);
        }

        private boolean casAppend(int cState, int toAppend) {
            return stateUpdater.compareAndSet(this, cState, (cState | toAppend));
        }

        private boolean casRemove(int cState, int toRemove) {
            return stateUpdater.compareAndSet(this, cState, (cState & ~toRemove));
        }

        private static boolean has(int state, int flags) {
            return (state & flags) == flags;
        }

        private static boolean hasAny(int state, int flags) {
            return (state & flags) != 0;
        }
    }

    /**
     * Subscriber wrapper that offloads "publish" terminal signals
     */
    protected static final class CompletableSubscriberOffloadedTerminals extends AbstractOffloadedSingleValueSubscriber
            implements Subscriber {
        private static final Object COMPLETED = new Object() {
            @Override
            public String toString() {
                return "COMPLETED";
            }
        };
        private final Subscriber subscriber;

        CompletableSubscriberOffloadedTerminals(final Subscriber subscriber,
                                                final BooleanSupplier shouldOffload, final Executor executor) {
            super(shouldOffload, executor);
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void onComplete() {
            terminal(COMPLETED);
        }

        @Override
        public void onError(final Throwable t) {
            terminal(t);
        }

        @Override
        void terminateOnEnqueueFailure(final Throwable cause) {
            LOGGER.warn("Failed to execute task on the executor {}. " +
                            "Invoking Subscriber (onError()) in the caller thread. Subscriber {}.",
                    executor, subscriber, cause);
            subscriber.onError(cause);
        }

        @Override
        void deliverTerminalToSubscriber(final Object terminal) {
            if (terminal instanceof Throwable) {
                safeOnError(subscriber, (Throwable) terminal);
            } else {
                assert COMPLETED == terminal : "Unexpected terminal " + terminal;
                safeOnComplete(subscriber);
            }
        }

        @Override
        void sendOnSubscribe(final Cancellable cancellable) {
            try {
                subscriber.onSubscribe(cancellable);
            } catch (Throwable t) {
                onSubscribeFailed();
                safeOnError(subscriber, t);
                safeCancel(cancellable);
            }
        }
    }

    /**
     * Subscriber wrapper that offloads the Cancellable
     */
    static final class CompletableSubscriberOffloadedCancellable implements Subscriber {
        private final Subscriber subscriber;
        private final BooleanSupplier shouldOffload;
        private final Executor executor;

        CompletableSubscriberOffloadedCancellable(final Subscriber subscriber,
                                                  final BooleanSupplier shouldOffload, final Executor executor) {
            this.subscriber = requireNonNull(subscriber);
            this.shouldOffload = shouldOffload;
            this.executor = executor;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(new OffloadedCancellable(cancellable, shouldOffload, executor));
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }
    }

    /**
     * Invokes {@link Cancellable#cancel()} of the provided {@link Cancellable} using provided executor
     */
    static final class OffloadedCancellable implements Cancellable {
        private final Cancellable cancellable;
        private final BooleanSupplier shouldOffload;
        private final Executor executor;

        OffloadedCancellable(final Cancellable cancellable,
                             final BooleanSupplier shouldOffload, final Executor executor) {
            this.cancellable = requireNonNull(cancellable);
            this.shouldOffload = shouldOffload;
            this.executor = executor;
        }

        @Override
        public void cancel() {
            if (safeShouldOffload(shouldOffload)) {
                try {
                    executor.execute(() -> safeCancel(cancellable));
                } catch (Throwable t) {
                    LOGGER.warn("Failed to execute task on the executor {}. " +
                                    "Invoking Cancellable (cancel()) in the caller thread. Cancellable {}. ",
                            executor, cancellable, t);
                    // As a policy, we call the target in the calling thread when the executor is inadequately
                    // provisioned. In the future we could make this configurable.
                    safeCancel(cancellable);
                    // We swallow the error here as we are forwarding the actual call and throwing from here will
                    // interrupt the control flow.
                }
            } else {
                safeCancel(cancellable);
            }
        }
    }

    static boolean safeShouldOffload(BooleanSupplier shouldOffload) {
        try {
            return shouldOffload.getAsBoolean();
        } catch (Throwable t) {
            LOGGER.warn("Offloading hint BooleanSupplier {} threw", shouldOffload, t);
        }
        return true;
    }
}
