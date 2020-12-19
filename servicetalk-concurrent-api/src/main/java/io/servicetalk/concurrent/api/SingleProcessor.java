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
import io.servicetalk.concurrent.SingleSource.Processor;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.TerminalNotification;

import javax.annotation.Nullable;

/**
 * A {@link Single} which is also a {@link Subscriber}. State of this {@link Single} can be modified by using the
 * {@link Subscriber} methods which is forwarded to all existing or subsequent {@link Subscriber}s.
 * @param <T> The type of result of the {@link Single}.
 */
final class SingleProcessor<T> extends Single<T> implements Processor<T, T> {
    private static final Object TERMINAL_UNSET = new Object();
    private final ConcurrentStack<Subscriber<? super T>> stack = new ConcurrentStack<>();
    @Nullable
    private Object terminalSignal = TERMINAL_UNSET;

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        // We must subscribe before adding subscriber the the queue. Otherwise it is possible that this
        // Single has been terminated and the subscriber may be notified before onSubscribe is called.
        // We used a DelayedCancellable to avoid the case where the Subscriber will synchronously cancel and then
        // we would add the subscriber to the queue and possibly never (until termination) dereference the subscriber.
        DelayedCancellable delayedCancellable = new DelayedCancellable();
        subscriber.onSubscribe(delayedCancellable);
        if (stack.push(subscriber)) {
            delayedCancellable.delayedCancellable(() -> {
                // Cancel in this case will just cleanup references from the queue to ensure we don't prevent GC of
                // these references.
                stack.relaxedRemove(subscriber);
            });
        } else {
            terminateLateSubscriber(subscriber);
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

    private void terminateLateSubscriber(Subscriber<? super T> subscriber) {
        Object terminalSignal = this.terminalSignal;
        assert terminalSignal != TERMINAL_UNSET;
        if (terminalSignal instanceof TerminalNotification) {
            final Throwable error = ((TerminalNotification) terminalSignal).cause();
            assert error != null;
            subscriber.onError(error);
        } else {
            @SuppressWarnings("unchecked")
            final T value = (T) terminalSignal;
            subscriber.onSuccess(value);
        }
    }

    private void terminate(@Nullable Object terminalSignal) {
        if (this.terminalSignal == TERMINAL_UNSET) {
            // We must set terminalSignal before close as we depend upon happens-before relationship for this value
            // to be visible for any future late subscribers.
            this.terminalSignal = terminalSignal;
            if (terminalSignal instanceof TerminalNotification) {
                final Throwable error = ((TerminalNotification) terminalSignal).cause();
                assert error != null;
                stack.close(subscriber -> subscriber.onError(error));
            } else {
                @SuppressWarnings("unchecked")
                final T value = (T) terminalSignal;
                stack.close(subscriber -> subscriber.onSuccess(value));
            }
        }
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        subscribeInternal(subscriber);
    }
}
