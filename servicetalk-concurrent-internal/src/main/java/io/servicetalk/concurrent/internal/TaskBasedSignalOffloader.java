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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedSpscQueue;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * An implementation of {@link SignalOffloader} that does not hold up a thread for the lifetime of the offloader.
 * Instead it enqueues multiple tasks to the provided {@link Consumer executor} and hence is susceptible to not having
 * enough capacity in the {@link Consumer executor} when sending signals as compared to detecting insufficient capacity
 * earlier as with {@link ThreadBasedSignalOffloader}.
 */
final class TaskBasedSignalOffloader implements SignalOffloader {

    private static final Object NULL_WRAPPER = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskBasedSignalOffloader.class);

    private final Executor executor;
    private final int publisherSignalQueueInitialCapacity;

    TaskBasedSignalOffloader(final Executor executor) {
        this(executor, 2);
    }

    /**
     * New instance.
     *
     * @param executor A {@link Executor} to use for offloading signals.
     * @param publisherSignalQueueInitialCapacity Initial capacity for the queue of signals to a {@link Subscriber}.
     */
    TaskBasedSignalOffloader(final Executor executor, final int publisherSignalQueueInitialCapacity) {
        this.executor = requireNonNull(executor);
        this.publisherSignalQueueInitialCapacity = publisherSignalQueueInitialCapacity;
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscriber(final Subscriber<? super T> subscriber) {
        return new OffloadedSubscriber<>(subscriber, executor, publisherSignalQueueInitialCapacity);
    }

    @Override
    public <T> SingleSource.Subscriber<? super T> offloadSubscriber(
            final SingleSource.Subscriber<? super T> subscriber) {
        return new OffloadedSingleSubscriber<>(executor, subscriber);
    }

    @Override
    public CompletableSource.Subscriber offloadSubscriber(final CompletableSource.Subscriber subscriber) {
        return new OffloadedCompletableSubscriber(executor, subscriber);
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscription(final Subscriber<? super T> subscriber) {
        return new OffloadedSubscriptionSubscriber<>(subscriber, executor);
    }

    @Override
    public <T> SingleSource.Subscriber<? super T> offloadCancellable(
            final SingleSource.Subscriber<? super T> subscriber) {
        return new OffloadedCancellableSingleSubscriber<>(subscriber, executor);
    }

    @Override
    public CompletableSource.Subscriber offloadCancellable(final CompletableSource.Subscriber subscriber) {
        return new OffloadedCancellableCompletableSubscriber(subscriber, executor);
    }

    @Override
    public <T> void offloadSubscribe(final Subscriber<? super T> subscriber,
                                     final Consumer<Subscriber<? super T>> handleSubscribe) {
        try {
            executor.execute(() -> handleSubscribe.accept(subscriber));
        } catch (Throwable throwable) {
            // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
            deliverErrorFromSource(subscriber, throwable);
        }
    }

    @Override
    public <T> void offloadSubscribe(final SingleSource.Subscriber<? super T> subscriber,
                                     final Consumer<SingleSource.Subscriber<? super T>> handleSubscribe) {
        try {
            executor.execute(() -> handleSubscribe.accept(subscriber));
        } catch (Throwable throwable) {
            // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
            deliverErrorFromSource(subscriber, throwable);
        }
    }

    @Override
    public void offloadSubscribe(final CompletableSource.Subscriber subscriber,
                                 final Consumer<CompletableSource.Subscriber> handleSubscribe) {
        try {
            executor.execute(() -> handleSubscribe.accept(subscriber));
        } catch (Throwable throwable) {
            // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
            deliverErrorFromSource(subscriber, throwable);
        }
    }

    @Override
    public <T> void offloadSignal(final T signal, final Consumer<T> signalConsumer) {
        executor.execute(() -> signalConsumer.accept(signal));
    }

    private static final class OffloadedSubscription implements Subscription, Runnable {
        private static final int STATE_IDLE = 0;
        private static final int STATE_ENQUEUED = 1;
        private static final int STATE_EXECUTING = 2;

        public static final int CANCELLED = -1;
        public static final int TERMINATED = -2;

        private static final AtomicIntegerFieldUpdater<OffloadedSubscription> stateUpdater =
                newUpdater(OffloadedSubscription.class, "state");
        private static final AtomicLongFieldUpdater<OffloadedSubscription> requestedUpdater =
                AtomicLongFieldUpdater.newUpdater(OffloadedSubscription.class, "requested");

        private final Executor executor;
        private final Subscription target;
        private volatile int state = STATE_IDLE;
        private volatile long requested;

        OffloadedSubscription(final Executor executor, final Subscription target) {
            this.executor = executor;
            this.target = requireNonNull(target);
        }

        @Override
        public void request(final long n) {
            if ((!isRequestNValid(n) &&
                    requestedUpdater.getAndSet(this, n < TERMINATED ? n : Long.MIN_VALUE) >= 0) ||
                    requestedUpdater.accumulateAndGet(this, n,
                            FlowControlUtils::addWithOverflowProtectionIfNotNegative) > 0) {
                enqueueTaskIfRequired(true);
            }
        }

        @Override
        public void cancel() {
            long oldVal = requestedUpdater.getAndSet(this, CANCELLED);
            if (oldVal != CANCELLED) {
                enqueueTaskIfRequired(false);
            }
            // duplicate cancel.
        }

        private void enqueueTaskIfRequired(boolean forRequestN) {
            final int oldState = stateUpdater.getAndSet(this, STATE_ENQUEUED);
            if (oldState == STATE_IDLE) {
                try {
                    executor.execute(this);
                } catch (Throwable t) {
                    // Ideally, we should send an error to the related Subscriber but that would mean we make sure
                    // we do not concurrently invoke the Subscriber with the original source which would mean we
                    // add some "lock" in the data path.
                    // This is an optimistic approach assuming executor rejections are occasional and hence adding
                    // Subscription -> Subscriber dependency for all paths is too costly.
                    // As we do for other cases, we simply invoke the target in the calling thread.
                    if (forRequestN) {
                        LOGGER.error("Failed to execute task on the executor {}. " +
                                        "Invoking Subscription (request()) in the caller thread. Subscription {}.",
                                executor, target, t);
                        target.request(requestedUpdater.getAndSet(this, 0));
                    } else {
                        requested = TERMINATED;
                        LOGGER.error("Failed to execute task on the executor {}. " +
                                        "Invoking Subscription (cancel()) in the caller thread. Subscription {}.",
                                executor, target, t);
                        target.cancel();
                    }
                    // We swallow the error here as we are forwarding the actual call and throwing from here will
                    // interrupt the control flow.
                }
            }
        }

        @Override
        public void run() {
            state = STATE_EXECUTING;
            for (;;) {
                long r = requestedUpdater.getAndSet(this, 0);
                if (r > 0) {
                    try {
                        target.request(r);
                        continue;
                    } catch (Throwable t) {
                        // Cancel since request-n threw.
                        requested = r = CANCELLED;
                        LOGGER.error("Unexpected exception from request(). Subscription {}.", target, t);
                    }
                }

                if (r == CANCELLED) {
                    requested = TERMINATED;
                    try {
                        target.cancel();
                    } catch (Throwable t) {
                        LOGGER.error("Ignoring unexpected exception from cancel(). Subscription {}.", target, t);
                    }
                    return; // No more signals are required to be sent.
                } else if (r == TERMINATED) {
                    return; // we want to hard return to avoid resetting state.
                } else if (r != 0) {
                    // Invalid request-n
                    //
                    // As per spec (Rule 3.9) a request-n with n <= 0 MUST signal an onError hence terminating the
                    // Subscription. Since, we can not store negative values in requested and keep going without
                    // requesting more invalid values, we assume spec compliance (no more data can be requested) and
                    // terminate.
                    requested = TERMINATED;
                    try {
                        target.request(r);
                    } catch (IllegalArgumentException iae) {
                        // Expected
                    } catch (Throwable t) {
                        LOGGER.error("Ignoring unexpected exception from request(). Subscription {}.", target, t);
                    }
                    return;
                }
                // We store a request(0) as Long.MIN_VALUE so if we see r == 0 here, it means we are re-entering
                // the loop because we saw the STATE_ENQUEUED but we have already read from requested.

                for (;;) {
                    final int cState = state;
                    if (cState == STATE_EXECUTING) {
                        if (stateUpdater.compareAndSet(this, STATE_EXECUTING, STATE_IDLE)) {
                            return;
                        }
                    } else if (cState == STATE_ENQUEUED) {
                        if (stateUpdater.compareAndSet(this, STATE_ENQUEUED, STATE_EXECUTING)) {
                            break;
                        }
                    } else {
                        return;
                    }
                }
            }
        }
    }

    private static final class OffloadedSubscriber<T> implements Subscriber<T>, Runnable {
        private static final int STATE_IDLE = 0;
        private static final int STATE_ENQUEUED = 1;
        private static final int STATE_EXECUTING = 2;
        private static final int STATE_TERMINATING = 3;
        private static final int STATE_TERMINATED = 4;
        private static final AtomicIntegerFieldUpdater<OffloadedSubscriber> stateUpdater =
                newUpdater(OffloadedSubscriber.class, "state");

        private volatile int state = STATE_IDLE;

        private final Subscriber<? super T> target;
        private final Executor executor;
        private final Queue<Object> signals;
        // Set in onSubscribe before we enqueue the task which provides memory visibility inside the task.
        // Since any further action happens after onSubscribe, we always guarantee visibility of this field inside
        // run()
        @Nullable
        private Subscription subscription;

        OffloadedSubscriber(final Subscriber<? super T> target, final Executor executor,
                                    final int publisherSignalQueueInitialCapacity) {
            this.target = target;
            this.executor = executor;
            // Queue is bounded by request-n
            signals = newUnboundedSpscQueue(publisherSignalQueueInitialCapacity);
        }

        @Override
        public void onSubscribe(final Subscription s) {
            subscription = s;
            offerSignal(s);
        }

        @Override
        public void onNext(final T t) {
            offerSignal(t == null ? NULL_WRAPPER : t);
        }

        @Override
        public void onError(final Throwable t) {
            offerSignal(TerminalNotification.error(t));
        }

        @Override
        public void onComplete() {
            offerSignal(TerminalNotification.complete());
        }

        @Override
        public void run() {
            state = STATE_EXECUTING;
            for (;;) {
                Object signal;
                while ((signal = signals.poll()) != null) {
                    if (signal instanceof Subscription) {
                        Subscription subscription = (Subscription) signal;
                        try {
                            target.onSubscribe(subscription);
                        } catch (Throwable t) {
                            clearSignalsFromExecutorThread();
                            LOGGER.error("Ignored unexpected exception from onSubscribe. Subscriber: {}, " +
                                    "Subscription: {}.", target, subscription, t);
                            subscription.cancel();
                            return; // We can't interact with the queue any more because we terminated, so bail.
                        }
                    } else if (signal instanceof TerminalNotification) {
                        state = STATE_TERMINATED;
                        try {
                            ((TerminalNotification) signal).terminate(target);
                        } catch (Throwable t) {
                            LOGGER.error("Ignored unexpected exception from {}. Subscriber: {}",
                                    ((TerminalNotification) signal).cause() == null ? "onComplete()" :
                                            "onError()", target, t);
                        }
                        return; // We can't interact with the queue any more because we terminated, so bail.
                    } else {
                        @SuppressWarnings("unchecked")
                        T t = signal == NULL_WRAPPER ? null : (T) signal;
                        try {
                            target.onNext(t);
                        } catch (Throwable th) {
                            clearSignalsFromExecutorThread();
                            try {
                                target.onError(th);
                            } catch (Throwable throwable) {
                                LOGGER.error("Ignored unexpected exception from onError(). Subscriber: {}", target,
                                        throwable);
                            } finally {
                                assert subscription != null;
                                subscription.cancel();
                            }
                            return; // We can't interact with the queue any more because we terminated, so bail.
                        }
                    }
                }
                for (;;) {
                    final int cState = state;
                    if (cState == STATE_EXECUTING) {
                        if (stateUpdater.compareAndSet(this, STATE_EXECUTING, STATE_IDLE)) {
                            return;
                        }
                    } else if (cState == STATE_ENQUEUED) {
                        if (stateUpdater.compareAndSet(this, STATE_ENQUEUED, STATE_EXECUTING)) {
                            break;
                        }
                    } else {
                        return;
                    }
                }
            }
        }

        private void clearSignalsFromExecutorThread() {
            do {
                state = STATE_TERMINATING;
                signals.clear();
                // if we fail to go from draining to terminated, that means the state was set to interrupted by the
                // producer thread, and we need to try to drain from the queue again.
            } while (!stateUpdater.compareAndSet(this, STATE_TERMINATING, STATE_TERMINATED));
        }

        private void offerSignal(Object signal) {
            // We optimistically insert into the queue, and then clear elements from the queue later if there is an
            // error detected in the consumer thread.
            if (!signals.offer(signal)) {
                throw new QueueFullException("signals");
            }

            for (;;) {
                final int cState = state;
                if (cState == STATE_TERMINATED) {
                    // Once we have terminated, we are sure no other thread will be consuming from the queue and
                    // therefore we can consume (aka clear) the queue in this thread without violating the single
                    // consumer constraint.
                    signals.clear();
                    return;
                } else if (cState == STATE_TERMINATING) {
                    if (stateUpdater.getAndSet(this, STATE_TERMINATED) == STATE_TERMINATED) {
                        // If another thread was draining the queue, and is no longer training the queue then the only
                        // state we can be in is STATE_TERMINATED. This means no other thread is consuming from the
                        // queue and we are safe to consume/clear it.
                        signals.clear();
                    }
                    return;
                } else if (stateUpdater.compareAndSet(this, cState, STATE_ENQUEUED)) {
                    if (cState == STATE_IDLE) {
                        break;
                    } else {
                        return;
                    }
                }
            }

            try {
                executor.execute(this);
            } catch (Throwable t) {
                state = STATE_TERMINATED;
                // This is an SPSC queue; at this point we are sure that there is no other consumer of the queue
                // because:
                //  - We were in STATE_IDLE and hence the task isn't running.
                //  - The Executor threw from execute(), so we assume it will not run the task.
                signals.clear();
                assert subscription != null;
                subscription.cancel();
                // As a policy, we call the target in the calling thread when the executor is inadequately
                // provisioned. In the future we could make this configurable.
                if (signal instanceof Subscription) {
                    // Offloading of onSubscribe was rejected.
                    // If target throws here, we do not attempt to do anything else as spec has been violated.
                    target.onSubscribe(EMPTY_SUBSCRIPTION);
                }
                try {
                    target.onError(t);
                } catch (Throwable throwable) {
                    LOGGER.error("Ignored unexpected exception from onError. Subscriber: {}", target, throwable);
                }
            }
        }
    }

    private abstract static class AbstractOffloadedSingleValueSubscriber implements Runnable {
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
                executor.execute(this);
            } catch (Throwable t) {
                // As a policy, we call the target in the calling thread when the executor is inadequately
                // provisioned. In the future we could make this configurable.
                state = STATE_TERMINATED;
                sendOnSubscribe(IGNORE_CANCEL);
                terminateOnEnqueueFailure(t);
            }
        }

        @Override
        public final void run() {
            for (;;) {
                int cState = state;
                if (cState == STATE_TERMINATED) {
                    return;
                }
                if (!casAppend(cState, EXECUTING_MASK)) {
                    continue;
                }
                cState |= EXECUTING_MASK;
                if (has(cState, ON_SUBSCRIBE_RECEIVED_MASK)) {
                    while (!stateUpdater.compareAndSet(this, cState, (cState & ~ON_SUBSCRIBE_RECEIVED_MASK))) {
                        cState = state;
                    }
                    assert cancellable != null;
                    sendOnSubscribe(cancellable);
                    // Re-read state to see if we terminated from onSubscribe
                    cState = state;
                }
                if (has(cState, RECEIVED_TERMINAL_MASK)) {
                    if (stateUpdater.compareAndSet(this, cState, STATE_TERMINATED)) {
                        assert terminal != null;
                        deliverTerminalToSubscriber(terminal);
                        return;
                    }
                } else if (stateUpdater.compareAndSet(this, cState, STATE_AWAITING_TERMINAL)) {
                    return;
                }
            }
        }

        final void terminal(final Object terminal) {
            this.terminal = terminal;
            for (;;) {
                int cState = state;
                if (// Duplicate terminal event
                    has(cState, RECEIVED_TERMINAL_MASK) || cState == STATE_TERMINATED ||
                    // Already executing or enqueued for executing, append the state.
                    hasAny(cState, EXECUTING_SUBSCRIBED_RECEIVED_MASK) && casAppend(cState, RECEIVED_TERMINAL_MASK)) {
                    return;
                } else if ((cState == STATE_AWAITING_TERMINAL || cState == STATE_INIT) &&
                        stateUpdater.compareAndSet(this, cState, RECEIVED_TERMINAL_MASK)) {
                    // Either we have seen onSubscribe and the Runnable is no longer executing, or we have not
                    // seen onSubscribe and there is a sequencing issue on the Subscriber. Either way we avoid looping
                    // and deliver the terminal event.
                    try {
                        executor.execute(this);
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

        private static boolean has(int state, int flag) {
            return (state & flag) == flag;
        }

        private static boolean hasAny(int state, int flag) {
            return (state & flag) != 0;
        }

        private boolean casAppend(int cState, int toAppend) {
            return stateUpdater.compareAndSet(this, cState, (cState | toAppend));
        }
    }

    private static final class OffloadedSingleSubscriber<T> extends AbstractOffloadedSingleValueSubscriber
            implements SingleSource.Subscriber<T> {
        private final SingleSource.Subscriber<T> target;

        OffloadedSingleSubscriber(final Executor executor, final SingleSource.Subscriber<T> target) {
            super(executor);
            this.target = requireNonNull(target);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            terminal(result == null ? NULL_WRAPPER : result);
        }

        @Override
        public void onError(final Throwable t) {
            terminal(TerminalNotification.error(t));
        }

        @Override
        void terminateOnEnqueueFailure(final Throwable cause) {
            LOGGER.error("Failed to execute task on the executor {}. " +
                    "Invoking Subscriber (onError()) in the caller thread. Subscriber {}.", executor, target, cause);
            target.onError(cause);
        }

        @Override
        void deliverTerminalToSubscriber(final Object terminal) {
            if (terminal instanceof TerminalNotification) {
                try {
                    final Throwable error = ((TerminalNotification) terminal).cause();
                    assert error != null : "Cause can't be null from TerminalNotification.error(..)";
                    target.onError(error);
                } catch (Throwable t) {
                    LOGGER.error("Ignored unexpected exception from onError. Subscriber: {}", target, t);
                }
            } else {
                try {
                    target.onSuccess(uncheckCast(terminal));
                } catch (Throwable t) {
                    LOGGER.error("Ignored unexpected exception from onSuccess. Subscriber: {}", target, t);
                }
            }
        }

        @Override
        void sendOnSubscribe(final Cancellable cancellable) {
            try {
                target.onSubscribe(cancellable);
            } catch (Throwable t) {
                onSubscribeFailed();
                LOGGER.error("Ignored unexpected exception from onSubscribe. Subscriber: {}, Cancellable: {}.",
                        target, cancellable, t);
                cancellable.cancel();
            }
        }

        @Nullable
        @SuppressWarnings("unchecked")
        private T uncheckCast(final Object signal) {
            return signal == NULL_WRAPPER ? null : (T) signal;
        }
    }

    private static final class OffloadedCompletableSubscriber extends AbstractOffloadedSingleValueSubscriber
            implements CompletableSource.Subscriber {
        private static final Object COMPLETED = new Object();
        private final CompletableSource.Subscriber target;

        OffloadedCompletableSubscriber(final Executor executor, final CompletableSource.Subscriber target) {
            super(executor);
            this.target = requireNonNull(target);
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
                            "Invoking Subscriber (onError()) in the caller thread. Subscriber {}.", executor, target,
                    cause);
            target.onError(cause);
        }

        @Override
        void deliverTerminalToSubscriber(final Object terminal) {
            try {
                if (terminal instanceof Throwable) {
                    target.onError((Throwable) terminal);
                } else {
                    target.onComplete();
                }
            } catch (Throwable t) {
                LOGGER.error("Ignored unexpected exception from {}. Subscriber: {}",
                        terminal instanceof Throwable ? "onError" : "onComplete", target, t);
            }
        }

        @Override
        void sendOnSubscribe(final Cancellable cancellable) {
            try {
                target.onSubscribe(cancellable);
            } catch (Throwable t) {
                onSubscribeFailed();
                LOGGER.error("Ignored unexpected exception from onSubscribe. Subscriber: {}, Cancellable: {}.",
                        target, cancellable, t);
                cancellable.cancel();
            }
        }
    }

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
                executor.execute(() -> {
                    try {
                        cancellable.cancel();
                    } catch (Throwable t) {
                        LOGGER.error("Ignored unexpected exception from cancel(). Cancellable: {}", cancellable, t);
                    }
                });
            } catch (Throwable t) {
                LOGGER.error("Failed to execute task on the executor {}. " +
                                "Invoking Cancellable (cancel()) in the caller thread. Cancellable {}. ",
                        executor, cancellable, t);
                // As a policy, we call the target in the calling thread when the executor is inadequately
                // provisioned. In the future we could make this configurable.
                cancellable.cancel();
                // We swallow the error here as we are forwarding the actual call and throwing from here will
                // interrupt the control flow.
            }
        }
    }

    private static final class OffloadedCancellableSingleSubscriber<T> implements SingleSource.Subscriber<T> {
        private final SingleSource.Subscriber<? super T> subscriber;
        private final Executor executor;

        OffloadedCancellableSingleSubscriber(final SingleSource.Subscriber<? super T> subscriber,
                                             final Executor executor) {
            this.subscriber = requireNonNull(subscriber);
            this.executor = executor;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            subscriber.onSubscribe(new OffloadedCancellable(cancellable, executor));
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            subscriber.onSuccess(result);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }
    }

    private static final class OffloadedCancellableCompletableSubscriber implements CompletableSource.Subscriber {
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

    private static final class OffloadedSubscriptionSubscriber<T> implements Subscriber<T> {
        private final Subscriber<T> subscriber;
        private final Executor executor;

        OffloadedSubscriptionSubscriber(final Subscriber<T> subscriber, final Executor executor) {
            this.subscriber = requireNonNull(subscriber);
            this.executor = executor;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            subscriber.onSubscribe(new OffloadedSubscription(executor, s));
        }

        @Override
        public void onNext(final T t) {
            subscriber.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }
}
