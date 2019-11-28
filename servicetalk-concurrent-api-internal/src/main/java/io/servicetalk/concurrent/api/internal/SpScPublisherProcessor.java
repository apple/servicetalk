/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.PlatformDependent.newUnboundedSpscQueue;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverTerminalFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;

/**
 * A {@link Publisher} that allows for signals to be directly injected via {@link #sendOnNext(Object)},
 * {@link #sendOnComplete()}, and {@link #sendOnError(Throwable)}. The threading restrictions for this class are:
 * <ul>
 * <li><strong>S</strong>ingle<strong>P</strong>roducer meaning only a single thread is allowed to interact with
 * {@link #sendOnNext(Object)}, {@link #sendOnComplete()}, and {@link #sendOnError(Throwable)} methods.</li>
 * <li><strong>S</strong>ingle<strong>C</strong>onsumer meaning only a single {@link PublisherSource.Subscriber} is
 * supported. Other operators can be used to add support for multiple {@link PublisherSource.Subscriber}s if necessary.
 * </li>
 * </ul>
 *
 * @param <T> The type of {@link Publisher}.
 */
public final class SpScPublisherProcessor<T> extends SubscribablePublisher<T> {
    private static final AtomicLongFieldUpdater<SpScPublisherProcessor> requestedUpdater =
            AtomicLongFieldUpdater.newUpdater(SpScPublisherProcessor.class, "requested");
    private static final AtomicIntegerFieldUpdater<SpScPublisherProcessor> onNextQueueSizeUpdater =
            AtomicIntegerFieldUpdater.newUpdater(SpScPublisherProcessor.class, "onNextQueueSize");
    private static final AtomicReferenceFieldUpdater<SpScPublisherProcessor, Subscriber> subscriberUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SpScPublisherProcessor.class, Subscriber.class,
                    "subscriber");
    private static final long CANCELLED = Long.MIN_VALUE;
    private static final Object NULL_TOKEN = new Object();
    private static final Subscriber<?> CALLING_ON_SUBSCRIBE = newErrorSubscriber();
    private static final Subscriber<?> DRAINING_SUBSCRIBER = newErrorSubscriber();
    private static final Subscriber<?> TERMINATING_SUBSCRIBER = newErrorSubscriber();
    private static final Subscriber<?> TERMINATED_SUBSCRIBER = newErrorSubscriber();

    @Nullable
    private volatile Subscriber<? super T> subscriber;
    private volatile int onNextQueueSize;
    private volatile long requested;
    private final Queue<Object> signalQueue;
    private final int maxOnNextQueueSize;

    /**
     * Create a new instance.
     *
     * @param maxOnNextQueueSize The maximum amount of {@link PublisherSource.Subscriber#onNext(Object)} signals that
     * can be queued from {@link #sendOnNext(Object)}.
     */
    public SpScPublisherProcessor(int maxOnNextQueueSize) {
        this(maxOnNextQueueSize, 2);
    }

    /**
     * Create a new instance.
     *
     * @param maxOnNextQueueSize The maximum amount of {@link PublisherSource.Subscriber#onNext(Object)} signals that
     * can be queued from {@link #sendOnNext(Object)}.
     * @param initialQueueSize The initial size of the {@link Queue} to hold signals for the
     * {@link #sendOnNext(Object)}, {@link #sendOnComplete()}, and {@link #sendOnError(Throwable)} methods.
     */
    private SpScPublisherProcessor(int maxOnNextQueueSize, int initialQueueSize) {
        if (maxOnNextQueueSize <= 0) {
            throw new IllegalArgumentException("maxOnNextQueueSize: " + maxOnNextQueueSize + " (expected >0)");
        }
        this.maxOnNextQueueSize = maxOnNextQueueSize;
        signalQueue = newUnboundedSpscQueue(initialQueueSize);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> s) {
        for (;;) {
            final Subscriber<? super T> subscriber = this.subscriber;
            if (subscriber != null) {
                deliverTerminalFromSource(s, new DuplicateSubscribeException(subscriber, s));
                return;
            } else if (subscriberUpdater.compareAndSet(this, null, CALLING_ON_SUBSCRIBE)) {
                s.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        if (isRequestNValid(n)) {
                            if (requestedUpdater.getAndAccumulate(SpScPublisherProcessor.this, n,
                                    FlowControlUtils::addWithOverflowProtectionIfNotNegative) == 0) {
                                drainQueue();
                            }
                        } else {
                            for (;;) {
                                final long requested = SpScPublisherProcessor.this.requested;
                                if (requested < 0) {
                                    break;
                                } else if (requestedUpdater.compareAndSet(SpScPublisherProcessor.this,
                                        requested, n == CANCELLED ? CANCELLED + 1 : n)) {
                                    drainQueue();
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void cancel() {
                        if (requestedUpdater.getAndSet(SpScPublisherProcessor.this, CANCELLED) !=
                                CANCELLED) {
                            drainQueue(); // just to clear the queue and make objects eligible for GC.
                        }
                    }
                });

                // We hold off all interactions with the Subscriber until control flow returns from onSubscribe to
                // avoid concurrently invoking the Subscriber.
                if (subscriberUpdater.compareAndSet(this, CALLING_ON_SUBSCRIBE, s)) {
                    drainQueue();
                }
                break;
            }
        }
    }

    /**
     * Send an {@link PublisherSource.Subscriber#onNext(Object)} signal to the subscriber of this {@link Publisher}.
     *
     * @param t The signals for {@link PublisherSource.Subscriber#onNext(Object)}.
     * @throws QueueFullException if the queue of signals would exceed the maximum size.
     */
    public void sendOnNext(@Nullable final T t) {
        // This is a single producer process, so we optimistically increment and may maxOnNextQueueSize, but we can
        // decrement after the fact if this is the case to prevent overflow on subsequent calls.
        if (onNextQueueSizeUpdater.getAndIncrement(this) == maxOnNextQueueSize) {
            onNextQueueSizeUpdater.decrementAndGet(this);
            final QueueFullException e = new QueueFullException("signalQueue", maxOnNextQueueSize);
            signalQueue.add(TerminalNotification.error(e));
            drainQueue();
            // The calling thread should be notified that the queue is full, and if this is in the context of another
            // asynchronous source it will serve as a best effort to terminate that source.
            throw e;
        } else {
            signalQueue.add(t == null ? NULL_TOKEN : t);
            drainQueue();
        }
    }

    /**
     * Send an {@link PublisherSource.Subscriber#onError(Throwable)} signal to the subscriber of this {@link Publisher}.
     *
     * @param t The signals for {@link PublisherSource.Subscriber#onError(Throwable)}.
     */
    public void sendOnError(final Throwable t) {
        signalQueue.add(TerminalNotification.error(t));
        drainQueue();
    }

    /**
     * Send an {@link PublisherSource.Subscriber#onComplete()} signal to the subscriber of this {@link Publisher}.
     */
    public void sendOnComplete() {
        signalQueue.add(TerminalNotification.complete());
        drainQueue();
    }

    private void drainQueue() {
        for (;;) {
            final Subscriber<? super T> subscriber = this.subscriber;
            if (subscriber == null || subscriber == CALLING_ON_SUBSCRIBE || subscriber == DRAINING_SUBSCRIBER) {
                if (subscriberUpdater.compareAndSet(this, subscriber, subscriber)) {
                    break;
                }
            } else if (subscriber == TERMINATING_SUBSCRIBER) {
                // forcing the state to TERMINATED_SUBSCRIBER either signals to the consumer thread that it needs to
                // drain again, or we will take ownership of the consumer and drain if already terminated.
                if (subscriberUpdater.getAndSet(this, TERMINATED_SUBSCRIBER) == TERMINATED_SUBSCRIBER) {
                    signalQueue.clear();
                }
                break;
            } else if (subscriber == TERMINATED_SUBSCRIBER) {
                signalQueue.clear();
                break;
            } else {
                for (;;) {
                    if (!subscriberUpdater.compareAndSet(this, subscriber, DRAINING_SUBSCRIBER)) {
                        return;
                    }
                    Object signal;
                    long previousRequested;
                    // getAndAccumulate because we want to know if before the decrement, the value was positive.
                    while ((previousRequested = requestedUpdater.getAndAccumulate(this, 1,
                            FlowControlUtils::subtractIfPositive)) > 0) {
                        signal = signalQueue.poll();
                        if (signal == null) {
                            previousRequested = requestedUpdater.accumulateAndGet(this, 1,
                                    FlowControlUtils::addWithOverflowProtectionIfNotNegative);
                            break;
                        } else if (signal instanceof TerminalNotification) {
                            clearQueueAndTerminate();
                            ((TerminalNotification) signal).terminate(subscriber);
                            return;
                        } else {
                            try {
                                onNextQueueSizeUpdater.decrementAndGet(this);
                                @SuppressWarnings("unchecked")
                                final T tSignal = signal == NULL_TOKEN ? null : (T) signal;
                                subscriber.onNext(tSignal);
                            } catch (Throwable cause) {
                                clearQueueAndTerminate();
                                subscriber.onError(cause);
                                return;
                            }
                        }
                    }
                    if (previousRequested < 0) {
                        clearQueueAndTerminate();
                        if (previousRequested != CANCELLED) {
                            subscriber.onError(newExceptionForInvalidRequestN(previousRequested));
                        }
                        return;
                    } else if ((signal = signalQueue.peek()) instanceof TerminalNotification) {
                        clearQueueAndTerminate();
                        ((TerminalNotification) signal).terminate(subscriber);
                        return;
                    }

                    // "unlock" the subscriber.
                    this.subscriber = subscriber;

                    final boolean empty = signalQueue.isEmpty();
                    previousRequested = requested;
                    if (!empty && previousRequested == 0 || empty && previousRequested >= 0) {
                        break;
                    }
                }
                break;
            }
        }
    }

    private void clearQueueAndTerminate() {
        do {
            subscriber = terminatingSubscriber();
            signalQueue.clear();
        } while (!subscriberUpdater.compareAndSet(this, TERMINATING_SUBSCRIBER, TERMINATED_SUBSCRIBER));
    }

    private static <T> Subscriber<T> newErrorSubscriber() {
        return new Subscriber<T>() {
            @Override
            public void onSubscribe(final Subscription subscription) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void onNext(@Nullable final T o) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void onError(final Throwable t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void onComplete() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Subscriber<T> terminatingSubscriber() {
        return (Subscriber<T>) TERMINATING_SUBSCRIBER;
    }
}
