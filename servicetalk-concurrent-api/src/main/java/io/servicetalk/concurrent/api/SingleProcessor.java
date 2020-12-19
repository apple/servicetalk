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

import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * A {@link Single} which is also a {@link Subscriber}. State of this {@link Single} can be modified by using the
 * {@link Subscriber} methods which is forwarded to all existing or subsequent {@link Subscriber}s.
 * @param <T> The type of result of the {@link Single}.
 */
final class SingleProcessor<T> extends Single<T> implements Processor<T, T> {
    private final RelaxedClosableConcurrentStack<Subscriber<? super T>, Consumer<Subscriber<? super T>>> stack =
            new RelaxedClosableConcurrentStack<>();

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        // We must subscribe before adding subscriber the the queue. Otherwise it is possible that this
        // Single has been terminated and the subscriber may be notified before onSubscribe is called.
        // We used a DelayedCancellable to avoid the case where the Subscriber will synchronously cancel and then
        // we would add the subscriber to the queue and possibly never (until termination) dereference the subscriber.
        DelayedCancellable delayedCancellable = new DelayedCancellable();
        subscriber.onSubscribe(delayedCancellable);
        Consumer<Subscriber<? super T>> terminalSignal = stack.push(subscriber);
        if (terminalSignal == null) {
            delayedCancellable.delayedCancellable(() -> {
                // Cancel in this case will just cleanup references from the queue to ensure we don't prevent GC of
                // these references.
                stack.relaxedRemove(subscriber);
            });
        } else {
            terminalSignal.accept(subscriber);
        }
    }

    @Override
    public void onSubscribe(final Cancellable cancellable) {
        // no op, we never cancel as Subscribers and subscribes are decoupled.
    }

    @Override
    public void onSuccess(@Nullable final T result) {
        terminate(s -> s.onSuccess(result));
    }

    @Override
    public void onError(final Throwable t) {
        terminate(s -> s.onError(t));
    }

    private void terminate(Consumer<Subscriber<? super T>> terminalSignal) {
        stack.close(terminalSignal, terminalSignal);
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        subscribeInternal(subscriber);
    }
}
