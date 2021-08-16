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

import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.TaskBasedAsyncCompletableOperator.safeShouldOffload;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeCancel;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedSpscQueue;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Base class for operators on a {@link Publisher} that process signals asynchronously hence in order to guarantee safe
 * downstream invocations require to wrap their {@link Subscriber}s with the correct {@link AsyncContext}.
 * Operators that process signals synchronously can use {@link AbstractSynchronousPublisherOperator} to avoid wrapping
 * their {@link Subscriber}s and hence reduce object allocation.
 *
 * @param <T> Type of original {@link Publisher}.
 *
 * @see AbstractSynchronousPublisherOperator
 */
abstract class TaskBasedAsyncPublisherOperator<T> extends AbstractNoHandleSubscribePublisher<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskBasedAsyncPublisherOperator.class);
    private static final Object NULL_WRAPPER = new Object() {
        @Override
        public String toString() {
            return "NULL_WRAPPER";
        }
    };

    private final Publisher<T> original;
    private final BooleanSupplier shouldOffload;
    private final Executor executor;

    TaskBasedAsyncPublisherOperator(final Publisher<T> original,
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
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(subscriber, contextMap, contextProvider);
    }

    /**
     * Offloads the {@link io.servicetalk.concurrent.PublisherSource.Subscriber} methods.
     *
     * @param <T> type of items
     */
    static final class OffloadedSubscriber<T> implements Subscriber<T> {
        private static final int STATE_IDLE = 0;
        private static final int STATE_ENQUEUED = 1;
        private static final int STATE_EXECUTING = 2;
        private static final int STATE_TERMINATING = 3;
        private static final int STATE_TERMINATED = 4;
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<OffloadedSubscriber> stateUpdater =
                newUpdater(OffloadedSubscriber.class, "state");

        private volatile int state = STATE_IDLE;

        private final Subscriber<? super T> target;
        private final BooleanSupplier shouldOffload;
        private final Executor executor;
        private final Queue<Object> signals;
        // Set in onSubscribe before we enqueue the task which provides memory visibility inside the task.
        // Since any further action happens after onSubscribe, we always guarantee visibility of this field inside
        // run()
        @Nullable
        private Subscription subscription;
        private boolean hasOffloaded;

        OffloadedSubscriber(final Subscriber<? super T> target,
                            final BooleanSupplier shouldOffload, final Executor executor) {
            this(target, shouldOffload, executor, 2);
        }

        OffloadedSubscriber(final Subscriber<? super T> target,
                            final BooleanSupplier shouldOffload, final Executor executor,
                            final int publisherSignalQueueInitialCapacity) {
            this.target = target;
            this.shouldOffload = shouldOffload;
            this.executor = executor;
            // Queue is bounded by request-n
            signals = newUnboundedSpscQueue(publisherSignalQueueInitialCapacity);
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

        @Override
        public void onSubscribe(final Subscription s) {
            subscription = s;
            offerSignal(s);
        }

        @Override
        public void onNext(@Nullable final T t) {
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

        void deliverSignals() {
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
                            safeOnError(target, t);
                            safeCancel(subscription);
                            return; // We can't interact with the queue any more because we terminated, so bail.
                        }
                    } else if (signal instanceof TerminalNotification) {
                        state = STATE_TERMINATED;
                        Throwable cause = ((TerminalNotification) signal).cause();
                        if (cause != null) {
                            safeOnError(target, cause);
                        } else {
                            safeOnComplete(target);
                        }
                        return; // We can't interact with the queue any more because we terminated, so bail.
                    } else {
                        @SuppressWarnings("unchecked")
                        T t = signal == NULL_WRAPPER ? null : (T) signal;
                        try {
                            target.onNext(t);
                        } catch (Throwable th) {
                            clearSignalsFromExecutorThread();
                            safeOnError(target, th);
                            assert subscription != null;
                            safeCancel(subscription);
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
                if (shouldOffload()) {
                    executor.execute(this::deliverSignals);
                } else {
                    deliverSignals();
                }
            } catch (Throwable t) {
                state = STATE_TERMINATED;
                try {
                    // As a policy, we call the target in the calling thread when the executor is inadequately
                    // provisioned. In the future we could make this configurable.
                    if (signal instanceof Subscription) {
                        // Offloading of onSubscribe was rejected.
                        // If target throws here, we do not attempt to do anything else as spec has been violated.
                        target.onSubscribe(EMPTY_SUBSCRIPTION);
                    }
                } finally {
                    safeOnError(target, t);
                }
                // This is an SPSC queue; at this point we are sure that there is no other consumer of the queue
                // because:
                //  - We were in STATE_IDLE and hence the task isn't running.
                //  - The Executor threw from execute(), so we assume it will not run the task.
                signals.clear();
                assert subscription != null;
                safeCancel(subscription);
            }
        }
    }

    /**
     * Wraps the {@link io.servicetalk.concurrent.PublisherSource.Subscription} methods with
     * offloading to the provided executor.
     *
     * @param <T> type of items
     */
    static final class OffloadedSubscriptionSubscriber<T> implements Subscriber<T> {
        private final Subscriber<T> subscriber;
        private final BooleanSupplier shouldOffload;
        private final Executor executor;

        OffloadedSubscriptionSubscriber(Subscriber<T> subscriber, BooleanSupplier shouldOffload, Executor executor) {
            this.subscriber = requireNonNull(subscriber);
            this.shouldOffload = shouldOffload;
            this.executor = executor;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            subscriber.onSubscribe(new OffloadedSubscription(executor, shouldOffload, s));
        }

        @Override
        public void onNext(@Nullable final T t) {
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

    /**
     * Offloads {@link io.servicetalk.concurrent.PublisherSource.Subscription} methods to provided executor
     */
    private static final class OffloadedSubscription implements Subscription {
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
        private final BooleanSupplier shouldOffload;
        private final Subscription target;
        private volatile int state = STATE_IDLE;
        private volatile long requested;
        private boolean hasOffloaded;

        OffloadedSubscription(Executor executor, BooleanSupplier shouldOffload, Subscription target) {
            this.executor = executor;
            this.shouldOffload = shouldOffload;
            this.target = requireNonNull(target);
        }

        private boolean shouldOffload() {
            if (!hasOffloaded) {
                if (!safeShouldOffload(shouldOffload)) {
                    return false;
                }
                hasOffloaded = true;
            }
            return true;
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
                    if (shouldOffload()) {
                        executor.execute(this::executeTask);
                    } else {
                        executeTask();
                    }
                } catch (Throwable t) {
                    // Ideally, we should send an error to the related Subscriber but that would mean we make sure
                    // we do not concurrently invoke the Subscriber with the original source which would mean we
                    // add some "lock" in the data path.
                    // This is an optimistic approach assuming executor rejections are occasional and hence adding
                    // Subscription -> Subscriber dependency for all paths is too costly.
                    // As we do for other cases, we simply invoke the target in the calling thread.
                    if (forRequestN) {
                        LOGGER.warn("Failed to execute task on the executor {}. " +
                                        "Invoking Subscription (request()) in the caller thread. Subscription {}.",
                                executor, target, t);
                        target.request(requestedUpdater.getAndSet(this, 0));
                    } else {
                        requested = TERMINATED;
                        LOGGER.warn("Failed to execute task on the executor {}. " +
                                        "Invoking Subscription (cancel()) in the caller thread. Subscription {}.",
                                executor, target, t);
                        target.cancel();
                    }
                    // We swallow the error here as we are forwarding the actual call and throwing from here will
                    // interrupt the control flow.
                }
            }
        }

        private void executeTask() {
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
                        LOGGER.warn("Unexpected exception from request(). Subscription {}.", target, t);
                    }
                }

                if (r == CANCELLED) {
                    requested = TERMINATED;
                    safeCancel(target);
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
                        LOGGER.warn("Ignoring unexpected exception from request(). Subscription {}.", target, t);
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
}
