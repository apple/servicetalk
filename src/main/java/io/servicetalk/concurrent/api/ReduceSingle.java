/**
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
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Single} that reduces a {@link Publisher} into a single item.
 *
 * @param <R> Item emitted from this.
 * @param <T> Items emitted from the source {@link Publisher}.
 */
final class ReduceSingle<R, T> extends Single<R> {
    private final Publisher<T> source;
    private final Supplier<R> resultFactory;
    private final BiFunction<R, ? super T, R> reducer;

    /**
     * New instance.
     *
     * @param source {@link Publisher} to reduce.
     * @param resultFactory Factory for the result which collects all items emitted by {@code source}.
     *                      This will be called every time the returned {@link Single} is subscribed.
     * @param reducer Invoked for every item emitted by the {@code source} and returns the same or altered {@code result} object.
     */
    ReduceSingle(Publisher<T> source, Supplier<R> resultFactory, BiFunction<R, ? super T, R> reducer) {
        this.source = requireNonNull(source);
        this.resultFactory = requireNonNull(resultFactory);
        this.reducer = requireNonNull(reducer);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super R> singleSubscriber) {
        final R r;
        try {
            r = resultFactory.get();
        } catch (Throwable t) {
            singleSubscriber.onSubscribe(IGNORE_CANCEL);
            singleSubscriber.onError(t);
            return;
        }
        source.subscribe(new ReduceSubscriber<>(r, reducer, singleSubscriber));
    }

    private static final class ReduceSubscriber<R, T> implements org.reactivestreams.Subscriber<T> {
        private static final AtomicReferenceFieldUpdater<ReduceSubscriber, Cancellable> cancellableUpdater =
                AtomicReferenceFieldUpdater.newUpdater(ReduceSubscriber.class, Cancellable.class, "cancellable");
        @SuppressWarnings("unused")
        @Nullable
        private volatile Cancellable cancellable;
        @Nullable
        private R result;
        private final BiFunction<R, ? super T, R> reducer;
        private final Subscriber<? super R> subscriber;

        ReduceSubscriber(@Nullable R result, BiFunction<R, ? super T, R> reducer, Subscriber<? super R> subscriber) {
            this.result = result;
            this.reducer = reducer;
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (!cancellableUpdater.compareAndSet(this, null, s::cancel)) {
                s.cancel();
                return;
            }
            s.request(Long.MAX_VALUE);
            subscriber.onSubscribe(this::cancel);
        }

        @Override
        public void onNext(T t) {
            // If Function.apply(...) throws we just propagate it to the caller which is responsible to terminate
            // its subscriber and cancel the subscription.
            result = reducer.apply(result, t);
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onSuccess(result);
        }

        private void cancel() {
            // Protected against concurrency on the cancel operation between the subscriber and this class.
            Cancellable cancellable = cancellableUpdater.getAndSet(this, IGNORE_CANCEL);
            if (cancellable != null) {
                cancellable.cancel();
            }
        }
    }
}
