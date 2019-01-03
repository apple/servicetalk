/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.Completable;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.Single;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.PlatformDependent.newUnboundedSpscQueue;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * An implementation of {@link SignalOffloader} that does not hold up a thread for the lifetime of the offloader.
 * Instead it enqueues multiple tasks to the provided {@link Consumer executor} and hence is susceptible to not having
 * enough capacity in the {@link Consumer executor} when sending signals as compared to detecting insufficient capacity
 * earlier as with {@link ThreadBasedSignalOffloader}.
 */
final class TaskBasedOffloader implements SignalOffloader {

    private static final Object NULL_WRAPPER = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskBasedOffloader.class);

    private final Executor executor;
    private final int publisherSignalQueueInitialCapacity;

    TaskBasedOffloader(final Executor executor) {
        this(executor, 2);
    }

    /**
     * New instance.
     *
     * @param executor A {@link Executor} to use for offloading signals.
     * @param publisherSignalQueueInitialCapacity Initial capacity for the queue of signals to a {@link Subscriber}.
     */
    TaskBasedOffloader(final Executor executor, final int publisherSignalQueueInitialCapacity) {
        this.executor = requireNonNull(executor);
        this.publisherSignalQueueInitialCapacity = publisherSignalQueueInitialCapacity;
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscriber(final Subscriber<? super T> subscriber) {
        return new OffloadedSubscriber<>(subscriber, executor, publisherSignalQueueInitialCapacity);
    }

    @Override
    public <T> Single.Subscriber<? super T> offloadSubscriber(final Single.Subscriber<? super T> subscriber) {
        return new OffloadedSingleSubscriber<>(executor, subscriber);
    }

    @Override
    public Completable.Subscriber offloadSubscriber(final Completable.Subscriber subscriber) {
        return new OffloadedCompletableSubscriber(executor, subscriber);
    }

    @Override
    public <T> Subscriber<? super T> offloadSubscription(final Subscriber<? super T> subscriber) {
        return new OffloadedSubscriptionSubscriber<>(subscriber, executor);
    }

    @Override
    public <T> Single.Subscriber<? super T> offloadCancellable(final Single.Subscriber<? super T> subscriber) {
        return new OffloadedCancellableSingleSubscriber<>(subscriber, executor);
    }

    @Override
    public Completable.Subscriber offloadCancellable(final Completable.Subscriber subscriber) {
        return new OffloadedCancellableCompletableSubscriber(subscriber, executor);
    }

    @Override
    public <T> void offloadSubscribe(final Subscriber<T> subscriber, final Consumer<Subscriber<T>> handleSubscribe) {
        try {
            executor.execute(() -> handleSubscribe.accept(subscriber));
        } catch (Throwable throwable) {
            // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(throwable);
        }
    }

    @Override
    public <T> void offloadSubscribe(final Single.Subscriber<T> subscriber,
                                     final Consumer<Single.Subscriber<T>> handleSubscribe) {
        try {
            executor.execute(() -> handleSubscribe.accept(subscriber));
        } catch (Throwable throwable) {
            // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onError(throwable);
        }
    }

    @Override
    public void offloadSubscribe(final Completable.Subscriber subscriber,
                                 final Consumer<Completable.Subscriber> handleSubscribe) {
        try {
            executor.execute(() -> handleSubscribe.accept(subscriber));
        } catch (Throwable throwable) {
            // We assume that if executor accepted the task, it was run and no exception will be thrown from accept.
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onError(throwable);
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

        private OffloadedSubscription(final Executor executor, final Subscription target) {
            this.executor = executor;
            this.target = requireNonNull(target);
        }

        @Override
        public void request(final long n) {
            if ((!isRequestNValid(n) &&
                    requestedUpdater.getAndSet(this, n < TERMINATED ? n : Long.MIN_VALUE) >= 0) ||
                    requestedUpdater.accumulateAndGet(this, n,
                            FlowControlUtil::addWithOverflowProtectionIfNotNegative) > 0) {
                int oldState = stateUpdater.getAndSet(this, STATE_ENQUEUED);
                if (oldState == STATE_IDLE) {
                    try {
                        executor.execute(this);
                    } catch (RejectedExecutionException re) {
                        LOGGER.error("Task rejected by the executor. " +
                                "Invoking Subscription (request-n) in the caller thread. Subscription {}. ", re);
                        // Ideally, we should send an error to the related Subscriber but that would mean we make sure
                        // we do not concurrently invoke the Subscriber with the original source which would mean we
                        // add some "lock" in the data path.
                        // This is an optimistic approach assuming executor rejections are occasional and hence adding
                        // Subscription -> Subscriber dependency for all paths is too costly.
                        // As we do for other cases, we simply invoke the target in the calling thread.
                        long toRequest = requestedUpdater.getAndSet(this, 0);
                        target.request(toRequest);
                    }
                }
            }
        }

        @Override
        public void cancel() {
            long oldVal = requestedUpdater.getAndSet(this, CANCELLED);
            if (oldVal >= 0) {
                int oldState = stateUpdater.getAndSet(this, STATE_ENQUEUED);
                if (oldState == STATE_IDLE) {
                    try {
                        executor.execute(this);
                    } catch (RejectedExecutionException re) {
                        LOGGER.error("Task rejected by the executor. " +
                                "Invoking Subscription (cancel) in the caller thread. Subscription {}. ", re);
                        requested = TERMINATED;
                        // As a policy, we call the target in the calling thread when the executor is inadequately
                        // provisioned. In the future we could make this configurable.
                        target.cancel();
                    }
                }
            }
            // If oldVal is negative, it is a duplicate cancel.
        }

        @Override
        public void run() {
            boolean isDone;
            do {
                stateUpdater.getAndSet(this, STATE_EXECUTING);
                long r = requestedUpdater.getAndSet(this, 0);
                try {
                    if (r > 0) {
                        try {
                            target.request(r);
                            continue;
                        } catch (Throwable t) {
                            LOGGER.error("Unexpected exception from request(). Subscription {}.", target, t);
                            // Cancel since request-n threw.
                            requested = r = CANCELLED;
                        }
                    }

                    if (r == TERMINATED || r == 0) {
                        return;
                    }
                    if (r == CANCELLED) {
                        // Cancelled
                        try {
                            target.cancel();
                        } catch (Throwable t) {
                            LOGGER.error("Ignoring unexpected exception from cancel(). Subscription {}.", target, t);
                        } finally {
                            requested = TERMINATED;
                        }
                        return; // No more signals are required to be sent.
                    }
                    // Invalid request-n
                    try {
                        target.request(r);
                    } catch (IllegalArgumentException iae) {
                        // Expected
                    } catch (Throwable t) {
                        LOGGER.error("Ignoring unexpected exception from request(). Subscription {}.", target, t);
                    } finally {
                        requested = TERMINATED;
                    }
                } finally {
                    isDone = stateUpdater.getAndSet(this, STATE_IDLE) != STATE_ENQUEUED;
                }
            } while (!isDone);
        }
    }

    private static final class OffloadedSubscriber<T> implements Subscriber<T>, Runnable {
        private static final int STATE_IDLE = 0;
        private static final int STATE_ENQUEUED = 1;
        private static final int STATE_EXECUTING = 2;
        private static final AtomicIntegerFieldUpdater<OffloadedSubscriber> stateUpdater =
                newUpdater(OffloadedSubscriber.class, "state");

        private volatile int state = STATE_IDLE;

        private final ErrorIgnoringSubscriber<? super T> target;
        private final Executor executor;
        private final Queue<Object> signals;
        // Only accessed from the Subscriber thread and hence no additional thread-safety is required.
        protected boolean executorRejected;

        private OffloadedSubscriber(final Subscriber<? super T> target, final Executor executor,
                                    final int publisherSignalQueueInitialCapacity) {
            this.target = new ErrorIgnoringSubscriber<>(target);
            this.executor = executor;
            // Queue is bounded by request-n
            signals = newUnboundedSpscQueue(publisherSignalQueueInitialCapacity);
        }

        @Override
        public void onSubscribe(final Subscription s) {
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
            boolean isDone;
            do {
                stateUpdater.getAndSet(this, STATE_EXECUTING);
                try {
                    for (;;) {
                        Object signal = signals.poll();
                        if (signal == null) {
                            break;
                        }
                        if (signal instanceof Subscription) {
                            Subscription subscription = (Subscription) signal;
                            target.onSubscribe(subscription);
                        } else if (signal instanceof TerminalNotification) {
                            ((TerminalNotification) signal).terminate(target);
                            // Assume terminal is the last signal
                            return;
                        } else {
                            @SuppressWarnings("unchecked")
                            T t = signal == NULL_WRAPPER ? null : (T) signal;
                            target.onNext(t);
                        }
                    }
                } finally {
                    isDone = stateUpdater.getAndSet(this, STATE_IDLE) != STATE_ENQUEUED;
                }
            } while (!isDone);
        }

        private void offerSignal(Object signal) {
            if (executorRejected) {
                // Ignore signals if executor has rejected once as we will not attempt to deliver any more signals.
                return;
            }
            if (!signals.offer(signal)) {
                throw new QueueFullException("offloader-" + target.getClass().getName(), MAX_VALUE);
            }
            int oldState = stateUpdater.getAndSet(this, STATE_ENQUEUED);
            if (oldState == STATE_IDLE) {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException re) {
                    executorRejected = true;
                    // We are terminating the Subscriber and will not send any more signals.
                    signals.clear();
                    if (signal instanceof Subscription) {
                        ((Subscription) signal).cancel();
                    }
                    // As a policy, we call the target in the calling thread when the executor is inadequately
                    // provisioned. In the future we could make this configurable.
                    target.terminateOnEnqueueFailure(re);
                }
            }
        }
    }

    private abstract static class AbstractOffloadedSingleValueSubscriber implements Runnable {
        private static final int STATE_INIT = 0;
        private static final int STATE_ON_SUBSCRIBE_RECEIVED = 1;
        private static final int STATE_EXECUTING = 2;
        private static final int STATE_AWAITING_TERMINAL = 4;
        private static final int STATE_RECEIVED_TERMINAL = 8;
        private static final int STATE_TERMINATED = 16;
        private static final AtomicIntegerFieldUpdater<AbstractOffloadedSingleValueSubscriber> stateUpdater =
                newUpdater(AbstractOffloadedSingleValueSubscriber.class, "state");

        private final Executor executor;
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
            state = STATE_ON_SUBSCRIBE_RECEIVED;
            try {
                executor.execute(this);
            } catch (RejectedExecutionException re) {
                // As a policy, we call the target in the calling thread when the executor is inadequately
                // provisioned. In the future we could make this configurable.
                state = STATE_TERMINATED;
                sendOnSubscribe(IGNORE_CANCEL);
                terminateOnEnqueueFailure(re);
            }
        }

        @Override
        public final void run() {
            for (;;) {
                int cState = state;
                if (cState == STATE_TERMINATED || cState == STATE_AWAITING_TERMINAL || cState == STATE_INIT) {
                    return;
                }
                if (!append(cState, STATE_EXECUTING)) {
                    continue;
                }
                cState |= STATE_EXECUTING;
                if (has(cState, STATE_ON_SUBSCRIBE_RECEIVED)) {
                    assert cancellable != null;
                    sendOnSubscribe(cancellable);
                    // Re-read state to see if we terminated from onSubscribe
                    cState = state;
                }
                if (has(cState, STATE_RECEIVED_TERMINAL)) {
                    if (stateUpdater.compareAndSet(this, cState, STATE_TERMINATED)) {
                        assert terminal != null;
                        sendTerminal(terminal);
                        return;
                    } else if (stateUpdater.compareAndSet(this, cState, STATE_AWAITING_TERMINAL)) {
                        return;
                    }
                } else if (stateUpdater.compareAndSet(this, cState, STATE_AWAITING_TERMINAL)) {
                    return;
                }
            }
        }

        final void setTerminal(final Object terminal) {
            this.terminal = terminal;
            for (;;) {
                int cState = state;
                if (has(cState, STATE_RECEIVED_TERMINAL) || state == STATE_TERMINATED) {
                    // Duplicate terminal event.
                    return;
                }
                if (has(cState, STATE_EXECUTING) && append(cState, STATE_RECEIVED_TERMINAL)) {
                    // We are in the execution loop, the terminal would be picked up.
                    return;
                } else if (cState == STATE_AWAITING_TERMINAL &&
                        stateUpdater.compareAndSet(this, STATE_AWAITING_TERMINAL, STATE_RECEIVED_TERMINAL)) {
                    // We are not executing hence need to enqueue the task to deliver terminal.
                    try {
                        executor.execute(this);
                    } catch (RejectedExecutionException re) {
                        state = STATE_TERMINATED;
                        // As a policy, we call the target in the calling thread when the executor is inadequately
                        // provisioned. In the future we could make this configurable.
                        terminateOnEnqueueFailure(re);
                    }
                    return;
                } else if (has(cState, STATE_ON_SUBSCRIBE_RECEIVED) && append(cState, STATE_RECEIVED_TERMINAL)) {
                    // We have already scheduled the task from onSubscribe, so both onSubscribe and terminal will be
                    // delivered.
                    return;
                }
            }
        }

        final void onSubscribeFailed() {
            stateUpdater.getAndSet(this, STATE_TERMINATED);
        }

        abstract void terminateOnEnqueueFailure(RejectedExecutionException cause);

        abstract void sendTerminal(Object terminal);

        abstract void sendOnSubscribe(Cancellable cancellable);

        private boolean has(int state, int flag) {
            return (state & flag) == flag;
        }

        private boolean append(int cState, int toAppend) {
            return stateUpdater.compareAndSet(this, cState, (cState | toAppend));
        }
    }

    private static final class OffloadedSingleSubscriber<T> extends AbstractOffloadedSingleValueSubscriber
            implements Single.Subscriber<T> {
        private final Single.Subscriber<T> target;

        OffloadedSingleSubscriber(final Executor executor, final Single.Subscriber<T> target) {
            super(executor);
            this.target = requireNonNull(target);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            setTerminal(result == null ? NULL_WRAPPER : result);
        }

        @Override
        public void onError(final Throwable t) {
            setTerminal(t);
        }

        @Override
        void terminateOnEnqueueFailure(final RejectedExecutionException cause) {
            target.onError(cause);
        }

        @Override
        void sendTerminal(final Object terminal) {
            if (terminal instanceof Throwable) {
                try {
                    target.onError((Throwable) terminal);
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
                LOGGER.error("Ignored unexpected exception from onSubscribe. Subscriber: {}, Cancellable: {}.",
                        target, cancellable, t);
                cancellable.cancel();
                onSubscribeFailed();
            }
        }

        @Nullable
        @SuppressWarnings("unchecked")
        private T uncheckCast(final Object signal) {
            return signal == NULL_WRAPPER ? null : (T) signal;
        }
    }

    private static final class OffloadedCompletableSubscriber extends AbstractOffloadedSingleValueSubscriber
            implements Completable.Subscriber {
        private final Completable.Subscriber target;

        OffloadedCompletableSubscriber(final Executor executor, final Completable.Subscriber target) {
            super(executor);
            this.target = requireNonNull(target);
        }

        @Override
        public void onComplete() {
            setTerminal(complete());
        }

        @Override
        public void onError(final Throwable t) {
            setTerminal(error(t));
        }

        @Override
        void terminateOnEnqueueFailure(final RejectedExecutionException cause) {
            target.onError(cause);
        }

        @Override
        void sendTerminal(final Object terminal) {
            try {
                ((TerminalNotification) terminal).terminate(target);
            } catch (Throwable t) {
                LOGGER.error("Ignored unexpected exception from {}. Subscriber: {}",
                        ((TerminalNotification) terminal).getCause() == null ? "onComplete" : "onError", target, t);
            }
        }

        @Override
        void sendOnSubscribe(final Cancellable cancellable) {
            try {
                target.onSubscribe(cancellable);
            } catch (Throwable t) {
                LOGGER.error("Ignored unexpected exception from onSubscribe. Subscriber: {}, Cancellable: {}.",
                        target, cancellable, t);
                cancellable.cancel();
                onSubscribeFailed();
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
            } catch (RejectedExecutionException re) {
                LOGGER.error("Failed to enqueue for cancel(). Cancellable: {}", cancellable, re);
                // As a policy, we call the target in the calling thread when the executor is inadequately
                // provisioned. In the future we could make this configurable.
                cancellable.cancel();
            }
        }
    }

    private static final class OffloadedCancellableSingleSubscriber<T> implements Single.Subscriber<T> {
        private final Single.Subscriber<? super T> subscriber;
        private final Executor executor;

        OffloadedCancellableSingleSubscriber(final Single.Subscriber<? super T> subscriber,
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

    private static final class OffloadedCancellableCompletableSubscriber implements Completable.Subscriber {
        private final Completable.Subscriber subscriber;
        private final Executor executor;

        OffloadedCancellableCompletableSubscriber(final Completable.Subscriber subscriber,
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

    private static final class ErrorIgnoringSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> original;

        private boolean earlyTermination;
        @Nullable
        private Subscription subscription;

        ErrorIgnoringSubscriber(final Subscriber<? super T> original) {
            this.original = requireNonNull(original);
        }

        @Override
        public void onSubscribe(final Subscription s) {
            if (earlyTermination) {
                return;
            }
            this.subscription = s;
            try {
                original.onSubscribe(s);
            } catch (Throwable t) {
                earlyTermination = true;
                LOGGER.error("Ignored unexpected exception from onSubscribe. Subscriber: {}, Subscription: {}.",
                        original, s, t);
                s.cancel();
            }
        }

        @Override
        public void onNext(final T t) {
            if (earlyTermination) {
                return;
            }
            try {
                original.onNext(t);
            } catch (Throwable throwable) {
                earlyTermination = true;
                LOGGER.error("Unexpected exception from onNext. Subscriber: {}", original, throwable);
                assert subscription != null;
                subscription.cancel();
                original.onError(throwable);
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (earlyTermination) {
                return;
            }
            try {
                original.onError(t);
            } catch (Throwable throwable) {
                earlyTermination = true;
                LOGGER.error("Ignored unexpected exception from onError. Subscriber: {}", original, throwable);
            }
        }

        @Override
        public void onComplete() {
            if (earlyTermination) {
                return;
            }
            try {
                original.onComplete();
            } catch (Throwable throwable) {
                earlyTermination = true;
                LOGGER.error("Ignored unexpected exception from onComplete. Subscriber: {}", original, throwable);
            }
        }

        void terminateOnEnqueueFailure(RejectedExecutionException re) {
            if (earlyTermination) {
                return;
            }
            earlyTermination = true;
            if (subscription == null) {
                original.onSubscribe(EMPTY_SUBSCRIPTION);
            } else {
                subscription.cancel();
            }
            try {
                original.onError(re);
            } catch (Throwable throwable) {
                LOGGER.error("Ignored unexpected exception from onError. Subscriber: {}", original, throwable);
            }
        }
    }
}
