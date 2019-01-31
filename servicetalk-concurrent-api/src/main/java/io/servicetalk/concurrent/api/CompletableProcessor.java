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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Completable.Processor;
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
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;

/**
 * A {@link Completable} which is also a {@link Subscriber}. State of this {@link Completable} can be modified by using
 * the {@link Subscriber} methods which is forwarded to all existing or subsequent {@link Subscriber}s.
 */
public final class CompletableProcessor extends Completable implements Processor {

    private static final AtomicReferenceFieldUpdater<CompletableProcessor, TerminalNotification> terminalSignalUpdater =
            AtomicReferenceFieldUpdater.newUpdater(CompletableProcessor.class, TerminalNotification.class, "terminalSignal");
    private static final AtomicIntegerFieldUpdater<CompletableProcessor> drainingTheQueueUpdater =
            AtomicIntegerFieldUpdater.newUpdater(CompletableProcessor.class, "drainingTheQueue");

    private final Queue<Subscriber> subscribers = newUnboundedLinkedMpscQueue();
    @SuppressWarnings("unused")
    @Nullable
    private volatile TerminalNotification terminalSignal;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int drainingTheQueue;

    @Override
    protected void handleSubscribe(Subscriber subscriber) {
        // We must subscribe before adding subscriber the the queue. Otherwise it is possible that this
        // Completable has been terminated and the subscriber may be notified before onSubscribe is called.
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
            TerminalNotification terminalSignal = this.terminalSignal;
            if (terminalSignal != null) {
                notifyListeners(terminalSignal);
            }
        });

        if (subscribers.offer(subscriber)) {
            TerminalNotification terminalSignal = this.terminalSignal;
            if (terminalSignal != null) {
                // To ensure subscribers are notified in order we go through the queue to notify subscribers.
                notifyListeners(terminalSignal);
            }
        } else {
            TerminalNotification.error(new QueueFullAndRejectedSubscribeException("subscribers"))
                    .terminate(subscriber);
        }
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        // no op, we never cancel as Subscribers and subscribes are decoupled.
    }

    @Override
    public void onComplete() {
        terminate(complete());
    }

    @Override
    public void onError(Throwable t) {
        terminate(TerminalNotification.error(t));
    }

    private void terminate(TerminalNotification terminalSignal) {
        if (terminalSignalUpdater.compareAndSet(this, null, terminalSignal)) {
            notifyListeners(terminalSignal);
        }
    }

    private void notifyListeners(TerminalNotification terminalSignal) {
        drainSingleConsumerQueueDelayThrow(subscribers, terminalSignal::terminate, drainingTheQueueUpdater, this);
    }
}
