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
package io.servicetalk.concurrent.reactivestreams;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Objects.requireNonNull;

/**
 * A set of adapter methods for converting to and from
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm">Reactive Streams</a> APIs and ServiceTalk APIs.
 */
public final class ReactiveStreamsAdapters {

    private ReactiveStreamsAdapters() {
        // No instances.
    }

    /**
     * Converts the passed {@link Publisher} to a
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm">Reactive Streams</a>
     * {@link org.reactivestreams.Publisher}.
     *
     * @param source {@link org.reactivestreams.Publisher} to convert to a {@link Publisher}.
     * @param <T> Type of items emitted from the {@code source} and the returned {@link Publisher}.
     * @return A {@link Publisher} representation of the passed {@link org.reactivestreams.Publisher}.
     */
    public static <T> Publisher<T> fromReactiveStreamsPublisher(org.reactivestreams.Publisher<T> source) {
        return new RSPublisherToPublisher<>(source);
    }

    /**
     * Converts the passed {@link Publisher} to a
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm">Reactive Streams</a>
     * {@link org.reactivestreams.Publisher}.
     *
     * @param publisher {@link Publisher} to convert to a {@link Publisher}.
     * @param <T> Type of items emitted from the {@code source} and the returned {@link Publisher}.
     * @return A {@link Publisher} representation of the passed {@link PublisherSource}.
     */
    public static <T> org.reactivestreams.Publisher<T> toReactiveStreamsPublisher(Publisher<T> publisher) {
        return new PublisherToRSPublisher<>(publisher);
    }

    /**
     * Converts the passed {@link PublisherSource} to a
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm">Reactive Streams</a>
     * {@link org.reactivestreams.Publisher}.
     *
     * @param source {@link PublisherSource} to convert to a {@link org.reactivestreams.Publisher}.
     * @param <T> Type of items emitted from the {@code source} and the returned {@link org.reactivestreams.Publisher}.
     * @return A {@link org.reactivestreams.Publisher} representation of the passed {@link PublisherSource}.
     */
    public static <T> org.reactivestreams.Publisher<T> toReactiveStreamsPublisher(PublisherSource<T> source) {
        return new PublisherSourceToRSPublisher<>(source);
    }

    private static final class PublisherToRSPublisher<T> implements org.reactivestreams.Publisher<T> {
        private final PublisherSource<T> source;

        PublisherToRSPublisher(final Publisher<T> source) {
            this.source = toSource(requireNonNull(source));
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            source.subscribe(new ReactiveStreamsSubscriber<>(subscriber));
        }
    }

    private static final class PublisherSourceToRSPublisher<T> implements org.reactivestreams.Publisher<T> {
        private final PublisherSource<T> source;

        PublisherSourceToRSPublisher(final PublisherSource<T> source) {
            this.source = requireNonNull(source);
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            source.subscribe(new ReactiveStreamsSubscriber<>(subscriber));
        }
    }

    private static final class ReactiveStreamsSubscriber<T> implements PublisherSource.Subscriber<T> {
        private final Subscriber<? super T> subscriber;

        ReactiveStreamsSubscriber(final Subscriber<? super T> subscriber) {
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void onSubscribe(final PublisherSource.Subscription subscription) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                }
            });
        }

        @Override
        public void onNext(final T t) {
            subscriber.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }

    private static final class RSPublisherToPublisher<T> extends Publisher<T> {
        private final org.reactivestreams.Publisher<T> source;

        RSPublisherToPublisher(final org.reactivestreams.Publisher<T> source) {
            this.source = requireNonNull(source);
        }

        @Override
        protected void handleSubscribe(final PublisherSource.Subscriber<? super T> subscriber) {
            source.subscribe(new RsToSTSubscriber<>(subscriber));
        }
    }

    private static final class RSToSTSubscription implements PublisherSource.Subscription {
        private final Subscription s;

        RSToSTSubscription(final Subscription s) {
            this.s = s;
        }

        @Override
        public void request(final long n) {
            s.request(n);
        }

        @Override
        public void cancel() {
            s.cancel();
        }
    }

    private static final class RsToSTSubscriber<T> implements Subscriber<T> {
        private final PublisherSource.Subscriber<? super T> subscriber;

        RsToSTSubscriber(final PublisherSource.Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            subscriber.onSubscribe(new RSToSTSubscription(s));
        }

        @Override
        public void onNext(final T t) {
            subscriber.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }
}
