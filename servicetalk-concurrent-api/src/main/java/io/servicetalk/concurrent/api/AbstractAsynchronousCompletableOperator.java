/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.SignalOffloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeCancel;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Base class for operators on a {@link Completable} that process signals asynchronously hence in order to guarantee
 * safe downstream invocations require to wrap their {@link Subscriber}s with a {@link SignalOffloader}.
 * Operators that process signals synchronously can use {@link AbstractSynchronousCompletableOperator} to avoid wrapping
 * their {@link Subscriber}s and hence reduce object allocation.
 *
 * @see AbstractSynchronousCompletableOperator
 */
abstract class AbstractAsynchronousCompletableOperator extends AbstractNoHandleSubscribeCompletable
        implements CompletableOperator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAsynchronousCompletableOperator.class);

    protected final Completable original;
    protected final Executor executor;

    AbstractAsynchronousCompletableOperator(Completable original, Executor executor) {
        super(immediate());
        this.original = requireNonNull(original);
        this.executor = executor;
    }

    @Override
    final Executor executor() {
        return executor;
    }

    @Override
    public abstract Subscriber apply(Subscriber subscriber);

    @Override
    void handleSubscribe(Subscriber subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
                               AsyncContextProvider contextProvider) {
        // Offload signals to the passed Subscriber making sure they are not invoked in the thread that
        // asynchronously processes signals. This is because the thread that processes the signals may have different
        // thread safety characteristics than the typical thread interacting with the execution chain.
        //
        // The AsyncContext needs to be preserved when ever we interact with the original Subscriber, so we wrap it here
        // with the original contextMap. Otherwise some other context may leak into this subscriber chain from the other
        // side of the asynchronous boundary.
        final Subscriber operatorSubscriber = new OffloadedCompletableSubscriber(
                contextProvider.wrapCompletableSubscriberAndCancellable(subscriber, contextMap), executor());
        // Subscriber to use to subscribe to the original source. Since this is an asynchronous operator, it may call
        // Cancellable method from EventLoop (if the asynchronous source created/obtained inside this operator uses
        // EventLoop) which may execute blocking code on EventLoop, eg: beforeCancel(). So, we should offload
        // Cancellable method here.
        //
        // We are introducing offloading on the Subscription, which means the AsyncContext may leak if we don't save
        // and restore the AsyncContext before/after the asynchronous boundary.
        final Subscriber upstreamSubscriber = new OffloadedCancellableCompletableSubscriber(
                apply(operatorSubscriber), executor());
        original.delegateSubscribe(upstreamSubscriber, signalOffloader, contextMap, contextProvider);
    }

    protected abstract static class AbstractOffloadedSingleValueSubscriber {
        private static final int ON_SUBSCRIBE_RECEIVED_MASK = 8;
        private static final int EXECUTING_MASK = 16;
        private static final int RECEIVED_TERMINAL_MASK = 32;
        private static final int EXECUTING_SUBSCRIBED_RECEIVED_MASK = EXECUTING_MASK | ON_SUBSCRIBE_RECEIVED_MASK;

        private static final int STATE_INIT = 0;
        private static final int STATE_AWAITING_TERMINAL = 1;
        private static final int STATE_TERMINATED = 2;
        private static final AtomicIntegerFieldUpdater<AbstractOffloadedSingleValueSubscriber> stateUpdater =
                newUpdater(AbstractOffloadedSingleValueSubscriber.class, "state");

        final Executor executor;
        @Nullable
        // Visibility: Task submitted to executor happens-before task execution.
        private Cancellable cancellable;
        @Nullable
        private Object terminal;
        private volatile int state = STATE_INIT;

        AbstractOffloadedSingleValueSubscriber(final Executor executor) {
            this.executor = executor;
        }

        public final void onSubscribe(final Cancellable cancellable) {
            this.cancellable = cancellable;
            state = ON_SUBSCRIBE_RECEIVED_MASK;
            try {
                executor.execute(this::deliverSignals);
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
                        executor.execute(this::deliverSignals);
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
     * Offloads "publish" terminal signals
     */
    protected static final class OffloadedCompletableSubscriber extends AbstractOffloadedSingleValueSubscriber
            implements CompletableSource.Subscriber {
        private static final Object COMPLETED = new Object() {
            @Override
            public String toString() {
                return "COMPLETED";
            }
        };
        private final CompletableSource.Subscriber subscriber;

        OffloadedCompletableSubscriber(final Subscriber subscriber, final Executor executor) {
            super(executor);
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
            LOGGER.error("Failed to execute task on the executor {}. " +
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
     * Ensures that Cancellable is offloaded to provided executor
     */
    static final class OffloadedCancellableCompletableSubscriber implements CompletableSource.Subscriber {
        private final CompletableSource.Subscriber subscriber;
        private final Executor executor;

        OffloadedCancellableCompletableSubscriber(final CompletableSource.Subscriber subscriber,
                                                  final Executor executor) {
            this.subscriber = requireNonNull(subscriber);
            this.executor = executor;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(new OffloadedCancellable(cancellable, executor));
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
     * Invokes {@link Cancellable#cancel()} of the provided Cancellable using provided executor
     */
    private static final class OffloadedCancellable implements Cancellable {
        private final Cancellable cancellable;
        private final Executor executor;

        OffloadedCancellable(final Cancellable cancellable, final Executor executor) {
            this.cancellable = requireNonNull(cancellable);
            this.executor = executor;
        }

        @Override
        public void cancel() {
            try {
                executor.execute(() -> safeCancel(cancellable));
            } catch (Throwable t) {
                LOGGER.error("Failed to execute task on the executor {}. " +
                                "Invoking Cancellable (cancel()) in the caller thread. Cancellable {}. ",
                        executor, cancellable, t);
                // As a policy, we call the target in the calling thread when the executor is inadequately
                // provisioned. In the future we could make this configurable.
                safeCancel(cancellable);
                // We swallow the error here as we are forwarding the actual call and throwing from here will
                // interrupt the control flow.
            }
        }
    }
}
