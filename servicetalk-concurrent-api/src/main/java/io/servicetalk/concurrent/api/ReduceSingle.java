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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.context.api.ContextMap;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.PublishAndSubscribeOnSingles.deliverOnSubscribeAndOnError;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Single} that reduces a {@link Publisher} into a single item.
 *
 * @param <R> Item emitted from this.
 * @param <T> Items emitted from the source {@link Publisher}.
 */
final class ReduceSingle<R, T> extends AbstractNoHandleSubscribeSingle<R> {
    private final Publisher<T> source;
    private final Supplier<? extends R> resultFactory;
    private final BiFunction<? super R, ? super T, R> reducer;

    /**
     * New instance.
     *
     * @param source {@link Publisher} to reduce.
     * @param resultFactory Factory for the result which collects all items emitted by {@code source}.
     *                      This will be called every time the returned {@link Single} is subscribed.
     * @param reducer Invoked for every item emitted by the {@code source} and returns the same or altered
     * {@code result} object.
     */
    ReduceSingle(Publisher<T> source, Supplier<? extends R> resultFactory,
                 BiFunction<? super R, ? super T, R> reducer) {
        super(source.executor());
        this.source = requireNonNull(source);
        this.resultFactory = requireNonNull(resultFactory);
        this.reducer = requireNonNull(reducer);
    }

    @Override
    void handleSubscribe(final Subscriber<? super R> singleSubscriber, final SignalOffloader signalOffloader,
                         final ContextMap contextMap, final AsyncContextProvider contextProvider) {
        final R r;
        try {
            r = resultFactory.get();
        } catch (Throwable t) {
            deliverOnSubscribeAndOnError(singleSubscriber, signalOffloader, contextMap, contextProvider, t);
            return;
        }
        // We are now subscribing to the original Publisher chain for the first time, re-using the SignalOffloader.
        // Using the special subscribe() method means it will not offload the Subscription (done in the public
        // subscribe() method). So, we use the SignalOffloader to offload subscription if required.
        PublisherSource.Subscriber<? super T> offloadedSubscription = signalOffloader.offloadSubscription(
                contextProvider.wrapSubscription(new ReduceSubscriber<>(r, reducer, singleSubscriber), contextMap));
        // Since we are not creating any new sources by reducing, we should use the same offloader to subscribe to the
        // original Publisher.
        source.delegateSubscribe(offloadedSubscription, signalOffloader, contextMap, contextProvider);
    }

    private static final class ReduceSubscriber<R, T> extends DelayedCancellable
            implements PublisherSource.Subscriber<T> {

        private final BiFunction<? super R, ? super T, R> reducer;
        private final Subscriber<? super R> subscriber;
        @Nullable
        private R result;

        ReduceSubscriber(@Nullable R result, BiFunction<? super R, ? super T, R> reducer,
                         Subscriber<? super R> subscriber) {
            this.result = result;
            this.reducer = reducer;
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            subscriber.onSubscribe(this);
            s.request(Long.MAX_VALUE);
            delayedCancellable(s);
        }

        @Override
        public void onNext(final T t) {
            // If Function.apply(...) throws we just propagate it to the caller which is responsible to terminate
            // its subscriber and cancel the subscription.
            result = reducer.apply(result, t);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onSuccess(result);
        }
    }
}
