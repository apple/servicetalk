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
package io.servicetalk.concurrent.jdkflow;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Objects.requireNonNull;

/**
 * A set of adapter methods for converting to and from
 * <a href="https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.html">JDK Flow</a>
 * APIs and ServiceTalk APIs.
 */
public final class JdkFlowAdapters {

    private JdkFlowAdapters() {
        // No instances.
    }

    /**
     * Converts the passed {@link Publisher} to a
     * <a href="https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.html">JDK Flow</a>
     * {@link java.util.concurrent.Flow.Publisher}.
     *
     * @param source {@link java.util.concurrent.Flow.Publisher} to convert to a {@link Publisher}.
     * @param <T> Type of items emitted from the {@code source} and the returned {@link Publisher}.
     * @return A {@link Publisher} representation of the passed {@link java.util.concurrent.Flow.Publisher}.
     */
    public static <T> Publisher<T> fromFlowPublisher(java.util.concurrent.Flow.Publisher<T> source) {
        return new FlowToStPublisher<>(source);
    }

    /**
     * Converts the passed {@link Publisher} to a
     * <a href="https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.html">JDK Flow</a>
     * {@link java.util.concurrent.Flow.Publisher}.
     *
     * @param publisher {@link Publisher} to convert to a {@link Publisher}.
     * @param <T> Type of items emitted from the {@code source} and the returned {@link Publisher}.
     * @return A {@link java.util.concurrent.Flow.Publisher} representation of the passed {@link Publisher}.
     */
    public static <T> java.util.concurrent.Flow.Publisher<T> toFlowPublisher(Publisher<T> publisher) {
        return new StToFlowPublisher<>(toSource(publisher));
    }

    /**
     * Converts the passed {@link PublisherSource} to a
     * <a href="https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.html">JDK Flow</a>
     * {@link java.util.concurrent.Flow.Publisher}.
     *
     * @param source {@link PublisherSource} to convert to a {@link java.util.concurrent.Flow.Publisher}.
     * @param <T> Type of items emitted from the {@code source} and the returned
     * {@link java.util.concurrent.Flow.Publisher}.
     * @return A {@link java.util.concurrent.Flow.Publisher} representation of the passed {@link PublisherSource}.
     */
    public static <T> java.util.concurrent.Flow.Publisher<T> toFlowPublisher(PublisherSource<T> source) {
        return new StToFlowPublisher<>(source);
    }

    private static final class StToFlowPublisher<T> implements java.util.concurrent.Flow.Publisher<T> {
        private final PublisherSource<T> source;

        StToFlowPublisher(final PublisherSource<T> source) {
            this.source = requireNonNull(source);
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            source.subscribe(new FlowToStSubscriber<>(subscriber));
        }
    }

    private static final class FlowToStSubscriber<T> implements PublisherSource.Subscriber<T> {
        private final Subscriber<? super T> subscriber;

        FlowToStSubscriber(final Subscriber<? super T> subscriber) {
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
        public void onNext(final T next) {
            subscriber.onNext(next);
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

    private static final class FlowToStPublisher<T> extends Publisher<T> {
        private final java.util.concurrent.Flow.Publisher<T> source;

        FlowToStPublisher(final java.util.concurrent.Flow.Publisher<T> source) {
            this.source = requireNonNull(source);
        }

        @Override
        protected void handleSubscribe(final PublisherSource.Subscriber<? super T> subscriber) {
            source.subscribe(new StToFlowSubscriber<>(subscriber));
        }
    }

    private static final class FlowToSTSubscription implements PublisherSource.Subscription {
        private final Subscription s;

        FlowToSTSubscription(final Subscription s) {
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

    private static final class StToFlowSubscriber<T> implements Subscriber<T> {
        private final PublisherSource.Subscriber<? super T> subscriber;

        StToFlowSubscriber(final PublisherSource.Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            subscriber.onSubscribe(new FlowToSTSubscription(s));
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
