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

import io.servicetalk.concurrent.PublisherSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public final class TestPublisher<T> extends Publisher<T> implements PublisherSource<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPublisher.class);

    private final Function<Subscriber<? super T>, Subscriber<? super T>> subscriberFunction;
    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();

    @Nullable
    private volatile Subscriber<? super T> subscriber;

    public TestPublisher() {
        this(new Builder<T>().buildSubscriberFunction());
    }

    private TestPublisher(final Function<Subscriber<? super T>, Subscriber<? super T>> subscriberFunction) {
        this.subscriberFunction = subscriberFunction;
    }

    public boolean isSubscribed() {
        return subscriber != null;
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        try {
            this.subscriber = requireNonNull(subscriberFunction.apply(subscriber));
        } catch (final Throwable t) {
            record(t);
        }
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        subscribeInternal(subscriber);
    }

    public void onSubscribe(final Subscription subscription) {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions("onSubscribe");
        subscriber.onSubscribe(subscription);
    }

    @SafeVarargs
    public final void onNext(@Nullable final T... items) {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions("onNext");
        if (items == null) {
            subscriber.onNext(null);
        } else {
            for (final T item : items) {
                subscriber.onNext(item);
            }
        }
    }

    public void onComplete() {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions("onComplete");
        subscriber.onComplete();
    }

    public void onError(final Throwable t) {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions("onError");
        subscriber.onError(t);
    }

    private Subscriber<? super T> checkSubscriberAndExceptions(final String signal) {
        if (!exceptions.isEmpty()) {
            final RuntimeException exception = new RuntimeException("Unexpected exception(s) encountered",
                    exceptions.get(0));
            for (int i = 1; i < exceptions.size(); i++) {
                exception.addSuppressed(exceptions.get(i));
            }
            throw exception;
        }
        final Subscriber<? super T> subscriber = this.subscriber;
        if (subscriber == null) {
            throw new IllegalStateException(signal + " without subscriber");
        }
        return subscriber;
    }

    private void record(final Throwable t) {
        requireNonNull(t);
        LOGGER.warn("Unexpected exception", t);
        exceptions.add(t);
    }

    public static class Builder<T> {

        @Nullable
        private Function<Subscriber<? super T>, Subscriber<? super T>> demandCheckingSubscriberFunction;
        @Nullable
        private Function<Subscriber<? super T>, Subscriber<? super T>> autoOnSubscribeSubscriberFunction =
                new AutoOnSubscribeSubscriberFunction<>();

        private Function<Subscriber<? super T>, Subscriber<? super T>> subscriberCardinalityFunction =
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

        public TestPublisher<T> build(final Function<Subscriber<? super T>, Subscriber<? super T>> function) {
            return new TestPublisher<>(requireNonNull(function));
        }

        private Function<Subscriber<? super T>, Subscriber<? super T>> buildSubscriberFunction() {
            Function<Subscriber<? super T>, Subscriber<? super T>> subscriberFunction =
                    demandCheckingSubscriberFunction;
            subscriberFunction = andThen(subscriberFunction, autoOnSubscribeSubscriberFunction);
            subscriberFunction = andThen(subscriberFunction, subscriberCardinalityFunction);
            assert subscriberFunction != null;
            return subscriberFunction;
        }

        public TestPublisher<T> build() {
            return new TestPublisher<>(buildSubscriberFunction());
        }

        @Nullable
        private static <T> Function<Subscriber<? super T>, Subscriber<? super T>>
        andThen(@Nullable final Function<Subscriber<? super T>, Subscriber<? super T>> first,
                @Nullable final Function<Subscriber<? super T>, Subscriber<? super T>> second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }
            return first.andThen(second);
        }
    }
}
