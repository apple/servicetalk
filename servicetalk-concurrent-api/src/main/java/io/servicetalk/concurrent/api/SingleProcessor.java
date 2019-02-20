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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.internal.QueueFullAndRejectedSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.CONCURRENT_EMITTING;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.CONCURRENT_IDLE;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.drainSingleConsumerQueueDelayThrow;
import static io.servicetalk.concurrent.internal.PlatformDependent.newUnboundedLinkedMpscQueue;

/**
 * A {@link Single} which is also a {@link Subscriber}. State of this {@link Single} can be modified by using the
 * {@link Subscriber} methods which is forwarded to all existing or subsequent {@link Subscriber}s.
 * @param <T> The type of result of the {@link Single}.
 */
public final class SingleProcessor<T> extends Single<T> implements SingleSource.Subscriber<T> {
    private static final AtomicReferenceFieldUpdater<SingleProcessor, Object> terminalSignalUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SingleProcessor.class, Object.class, "terminalSignal");
    private static final AtomicIntegerFieldUpdater<SingleProcessor> drainingTheQueueUpdater =
            AtomicIntegerFieldUpdater.newUpdater(SingleProcessor.class, "drainingTheQueue");
    private static final Object TERMINAL_NULL = new Object();

    private final Queue<Subscriber<? super T>> subscribers = newUnboundedLinkedMpscQueue();
    @Nullable
    @SuppressWarnings("unused")
    private volatile Object terminalSignal = TERMINAL_NULL;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int drainingTheQueue;

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        // We must subscribe before adding subscriber the the queue. Otherwise it is possible that this
        // Single has been terminated and the subscriber may be notified before onSubscribe is called.
        subscriber.onSubscribe(() -> {
            // Cancel in this case will just cleanup references from the queue to ensure we don't prevent GC of these
            // references.
            if (!drainingTheQueueUpdater.compareAndSet(this, CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
                return;
            }
            try {
                subscribers.remove(subscriber);
            } finally {
                drainingTheQueueUpdater.set(this, CONCURRENT_IDLE);
            }
            // Because we held the lock we need to check if any terminal event has occurred in the mean time,
            // and if so notify subscribers.
            Object terminalSignal = this.terminalSignal;
            if (terminalSignal != TERMINAL_NULL) {
                notifyListeners(terminalSignal);
            }
        });

        if (subscribers.offer(subscriber)) {
            Object terminalSignal = this.terminalSignal;
            if (terminalSignal != TERMINAL_NULL) {
                // To ensure subscribers are notified in order we go through the queue to notify subscribers.
                notifyListeners(terminalSignal);
            }
        } else {
            subscriber.onError(new QueueFullAndRejectedSubscribeException("subscribers"));
        }
    }

    @Override
    public void onSubscribe(final Cancellable cancellable) {
        // no op, we never cancel as Subscribers and subscribes are decoupled.
    }

    @Override
    public void onSuccess(@Nullable final T result) {
        terminate(result);
    }

    @Override
    public void onError(final Throwable t) {
        terminate(TerminalNotification.error(t));
    }

    private void terminate(@Nullable Object terminalSignal) {
        if (terminalSignalUpdater.compareAndSet(this, TERMINAL_NULL, terminalSignal)) {
            notifyListeners(terminalSignal);
        }
    }

    private void notifyListeners(@Nullable Object terminalSignal) {
        if (terminalSignal instanceof TerminalNotification) {
            final Throwable error = ((TerminalNotification) terminalSignal).getCause();
            assert error != null : "Cause can't be null from TerminalNotification.error(..)";
            drainSingleConsumerQueueDelayThrow(subscribers, subscriber -> subscriber.onError(error),
                    drainingTheQueueUpdater, this);
        } else {
            @SuppressWarnings("unchecked")
            final T value = (T) terminalSignal;
            drainSingleConsumerQueueDelayThrow(subscribers, subscriber -> subscriber.onSuccess(value),
                    drainingTheQueueUpdater, this);
        }
    }
}
