/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource.Subscriber;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

abstract class CompletableMergeSubscriber implements Subscriber {
    private static final AtomicReferenceFieldUpdater<CompletableMergeSubscriber, Object> terminalNotificationUpdater =
            newUpdater(CompletableMergeSubscriber.class, Object.class, "terminalNotification");
    private static final Object ON_COMPLETE = new Object();
    private static final Object DELIVERED_DELAYED_ERROR = new Object();
    @SuppressWarnings("unused")
    @Nullable
    private volatile Object terminalNotification;
    private final Subscriber subscriber;
    private final CancellableStack dynamicCancellable;
    private final boolean delayError;

    CompletableMergeSubscriber(Subscriber subscriber, boolean delayError) {
        this.subscriber = subscriber;
        this.delayError = delayError;
        dynamicCancellable = new CancellableStack();
        subscriber.onSubscribe(dynamicCancellable);
    }

    @Override
    public final void onSubscribe(Cancellable cancellable) {
        dynamicCancellable.add(cancellable);
    }

    @Override
    public final void onComplete() {
        if (onTerminate()) {
            tryToCompleteSubscriber();
        }
    }

    @Override
    public final void onError(Throwable t) {
        for (;;) {
            Object terminalNotification = this.terminalNotification;
            if (terminalNotification == null) {
                if (terminalNotificationUpdater.compareAndSet(this, null, t)) {
                    break;
                } else if (!delayError) {
                    // If we are not delaying error notification, and we fail to set the terminal event then that
                    // means we have already notified the subscriber and should return to avoid duplicate terminal
                    // notifications.
                    return;
                }
            } else {
                Throwable tmpT = (Throwable) terminalNotification;
                tmpT.addSuppressed(t);
                t = tmpT;
                break;
            }
        }

        // If we are delaying the error then we need to prevent concurrent termination with the subscribe() thread,
        // and we do this with a two phase atomic set to DELIVERED_DELAYED_ERROR.
        // If we don't delay the error then we never increase the total completion count, so we don't have to worry
        // about concurrent invocation.
        if (!delayError || (onTerminate() &&
                t == terminalNotificationUpdater.getAndSet(this, DELIVERED_DELAYED_ERROR))) {
            onError0(t);
        }
    }

    final void tryToCompleteSubscriber() {
        if (terminalNotificationUpdater.compareAndSet(this, null, ON_COMPLETE)) {
            subscriber.onComplete();
        } else if (delayError) {
            // This maybe called from the subscribe() thread and also the Subscriber thread. It is possible the
            // merge operation already completed successfully in the Subscriber thread, and then the subscribe()
            // thread observed all merged Completables have completed and so also calls this method.
            Object maybeThrowable = terminalNotificationUpdater.getAndSet(this, DELIVERED_DELAYED_ERROR);
            if (maybeThrowable instanceof Throwable) {
                onError0((Throwable) maybeThrowable);
            }
        }
    }

    private void onError0(Throwable t) {
        // If we are delaying error, then everything must have terminated by now, and there is no need to cancel
        // anything. However if we are not delaying error then we should cancel all outstanding work.
        if (!delayError) {
            dynamicCancellable.cancel();
        }
        subscriber.onError(t);
    }

    /**
     * Called when a terminal event is received.
     *
     * @return {@code true} if we are done processing after changing state due to receiving a terminal event.
     */
    abstract boolean onTerminate();
}
