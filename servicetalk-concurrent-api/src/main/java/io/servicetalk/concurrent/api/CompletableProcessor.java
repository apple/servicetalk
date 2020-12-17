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
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.QueueFullAndRejectedSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link Completable} which is also a {@link Subscriber}. State of this {@link Completable} can be modified by using
 * the {@link Subscriber} methods which is forwarded to all existing or subsequent {@link Subscriber}s.
 */
final class CompletableProcessor extends Completable implements Processor {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<CompletableProcessor, Queue> queueUpdater =
            newUpdater(CompletableProcessor.class, Queue.class, "queue");
    @Nullable
    private volatile Queue<Subscriber> queue = new ConcurrentLinkedQueue<>();
    @Nullable
    private TerminalNotification terminalSignal;

    @Override
    protected void handleSubscribe(Subscriber subscriber) {
        // We must subscribe before adding subscriber the the queue. Otherwise it is possible that this
        // Completable has been terminated and the subscriber may be notified before onSubscribe is called.
        // We used a DelayedCancellable to avoid the case where the Subscriber will synchronously cancel and then
        // we would add the subscriber to the queue and possibly never (until termination) dereference the subscriber.
        DelayedCancellable delayedCancellable = new DelayedCancellable();
        subscriber.onSubscribe(delayedCancellable);
        final Queue<Subscriber> currentQueue = queue;
        if (currentQueue == null) {
            terminateLateSubscriber(subscriber);
        } else if (currentQueue.offer(subscriber)) {
            if (!queueUpdater.compareAndSet(this, currentQueue, currentQueue)) {
                if (currentQueue.remove(subscriber)) {
                    terminateLateSubscriber(subscriber);
                }
            } else {
                delayedCancellable.delayedCancellable(() -> {
                    // Cancel in this case will just cleanup references from the queue to ensure we don't prevent GC of
                    // these references.
                    currentQueue.remove(subscriber);
                });
            }
        } else {
            subscriber.onError(new QueueFullAndRejectedSubscribeException("queue"));
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

    private void terminateLateSubscriber(Subscriber subscriber) {
        TerminalNotification terminalSignal = this.terminalSignal;
        assert terminalSignal != null;
        terminalSignal.terminate(subscriber);
    }

    private void terminate(TerminalNotification terminalSignal) {
        if (this.terminalSignal == null) {
            this.terminalSignal = terminalSignal;
            @SuppressWarnings("unchecked")
            Queue<Subscriber> currentQueue = (Queue<Subscriber>) queueUpdater.getAndSet(this, null);
            if (currentQueue != null) {
                Throwable delayedCause = null;
                Subscriber subscriber;
                while ((subscriber = currentQueue.poll()) != null) {
                    try {
                        terminalSignal.terminate(subscriber);
                    } catch (Throwable cause) {
                        delayedCause = catchUnexpected(delayedCause, cause);
                    }
                }
                if (delayedCause != null) {
                    throwException(delayedCause);
                }
            }
        }
    }

    @Override
    public void subscribe(final Subscriber subscriber) {
        subscribeInternal(subscriber);
    }
}
