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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public final class TestPublisher<T> extends Publisher<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPublisher.class);

    private final Function<Subscriber<? super T>, Subscriber<T>> subscriberFunction;
    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();

    @Nullable
    private volatile Subscriber<T> subscriber;

    private TestPublisher(final Function<Subscriber<? super T>, Subscriber<T>> subscriberFunction) {
        this.subscriberFunction = subscriberFunction;
    }

    public boolean subscribed() {
        return subscriber != null;
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        try {
            this.subscriber = requireNonNull(subscriberFunction.apply(subscriber));
        } catch (final Throwable t) {
            record(t);
            LOGGER.warn("Unexpected exception", t);
        }
    }

    public void onSubscribe(final Subscription subscription) {
        rethrow();
        final Subscriber<T> subscriber = this.subscriber;
        if (subscriber == null) {
            recordAndThrow(new IllegalStateException("onSubscribe without subscriber"));
            return;
        }
        subscriber.onSubscribe(subscription);
    }

    @SafeVarargs
    public final void onNext(@Nullable final T... items) {
        rethrow();
        final Subscriber<T> subscriber = this.subscriber;
        if (subscriber == null) {
            throw new IllegalStateException("onNext without subscriber");
        }
        if (items == null) {
            subscriber.onNext(null);
        } else {
            for (final T item : items) {
                subscriber.onNext(item);
            }
        }
    }

    public void onComplete() {
        rethrow();
        final Subscriber<T> subscriber = this.subscriber;
        if (subscriber == null) {
            throw new IllegalStateException("onComplete without subscriber");
        }
        subscriber.onComplete();
    }

    public void onError(final Throwable t) {
        rethrow();
        final Subscriber<T> subscriber = this.subscriber;
        if (subscriber == null) {
            throw new IllegalStateException("onError without subscriber");
        }
        subscriber.onError(t);
    }

    private <TT extends Throwable> void recordAndThrow(final TT t) throws TT {
        record(t);
        throw t;
    }

    private void record(final Throwable t) {
        requireNonNull(t);
        exceptions.add(t);
    }

    private void rethrow() {
        if (!exceptions.isEmpty()) {
            final RuntimeException exception = new RuntimeException("Unexpected exception(s) encountered",
                    exceptions.get(0));
            for (int i = 1; i < exceptions.size(); i++) {
                exception.addSuppressed(exceptions.get(i));
            }
            throw exception;
        }
    }

    public static class Builder<T> {

        @Nullable
        private Function<Subscriber<? super T>, Subscriber<T>> demandCheckingSubscriberFunction;
        @Nullable
        private Function<Subscriber<? super T>, Subscriber<T>> autoOnSubscribeSubscriberFunction =
                new AutoOnSubscribeSubscriberFunction<>();

        private Function<Subscriber<? super T>, Subscriber<T>> subscriberCardinalityFunction =
                new SequentialPublisherSubscriberFunction<>();

        public Builder<T> concurrentSubscribers() {
            subscriberCardinalityFunction = new ConcurrentPublisherSubscriberFunction<>();
            return this;
        }

        public Builder<T> concurrentSubscribers(final ConcurrentPublisherSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        public Builder<T> sequentialSubscribers() {
            subscriberCardinalityFunction = new SequentialPublisherSubscriberFunction<>();
            return this;
        }

        public Builder<T> sequentialSubscribers(final SequentialPublisherSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        public Builder<T> singleSubscriber() {
            subscriberCardinalityFunction = new NonResubscribeablePublisherSubscriberFunction<>();
            return this;
        }

        public Builder<T> singleSubscriber(final NonResubscribeablePublisherSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        public Builder<T> enableDemandCheck() {
            demandCheckingSubscriberFunction = new DemandCheckingSubscriberFunction<>();
            return this;
        }

        public Builder<T> enableDemandCheck(final DemandCheckingSubscriberFunction<T> function) {
            demandCheckingSubscriberFunction = requireNonNull(function);
            return this;
        }

        public Builder<T> disableDemandCheck() {
            demandCheckingSubscriberFunction = null;
            return this;
        }

        public Builder<T> autoOnSubscribe() {
            autoOnSubscribeSubscriberFunction = new AutoOnSubscribeSubscriberFunction<>();
            return this;
        }

        public Builder<T> autoOnSubscribe(final AutoOnSubscribeSubscriberFunction<T> function) {
            autoOnSubscribeSubscriberFunction = requireNonNull(function);
            return this;
        }

        public Builder<T> disableAutoOnSubscribe() {
            autoOnSubscribeSubscriberFunction = null;
            return this;
        }

        public TestPublisher<T> custom(final Function<Subscriber<? super T>, Subscriber<T>> function) {
            return new TestPublisher<>(requireNonNull(function));
        }

        private Function<Subscriber<? super T>, Subscriber<T>> buildSubscriberFunction() {
            Function<Subscriber<? super T>, Subscriber<T>> subscriberFunction = demandCheckingSubscriberFunction;
            subscriberFunction = andThen(subscriberFunction, autoOnSubscribeSubscriberFunction);
            subscriberFunction = andThen(subscriberFunction, subscriberCardinalityFunction);
            assert subscriberFunction != null;
            return subscriberFunction;
        }

        public TestPublisher<T> build() {
            return new TestPublisher<>(buildSubscriberFunction());
        }

        @Nullable
        private static <T> Function<Subscriber<? super T>,
                Subscriber<T>> andThen(@Nullable final Function<Subscriber<? super T>, Subscriber<T>> first,
                                       @Nullable final Function<Subscriber<? super T>, Subscriber<T>> second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return null;
            }
            return first.andThen(second);
        }
    }
}
