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
import io.servicetalk.concurrent.PublisherSource.Subscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Publisher} &amp; {@link PublisherSource} whose outgoing signals to its {@link Subscriber}s can be
 * controlled.
 * <p>
 * Behavior beyond simply delegating signals to the {@link Subscriber} is accomplished by a
 * {@link Function Function&lt;Subscriber&lt;? super T&gt;, Subscriber&lt;? super T&gt;&gt;}. This {@link Function} is
 * invoked for every {@link #subscribe(Subscriber)} invocation, and the result is used as the delegate for subsequent
 * {@link #onSubscribe(Subscription)}, {@link #onNext(Object[])}, {@link #onComplete()}, and
 * {@link #onError(Throwable)} calls. See {@link Builder} for more information.
 * <h3>Defaults</h3>
 * <ul>
 *     <li>Allows sequential but not concurrent subscribers.</li>
 *     <li>Asserts that {@link #onNext(Object[])} is not called without sufficient demand.</li>
 *     <li>Sends {@link #onSubscribe(Subscription)} automatically when subscribed to.</li>
 * </ul>
 *
 * @param <T> Type of the items emitted by this {@code TestPublisher}.
 */
public class TestPublisher<T> extends Publisher<T> implements PublisherSource<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPublisher.class);

    private final Function<Subscriber<? super T>, Subscriber<? super T>> subscriberFunction;
    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();

    @Nullable
    private volatile Subscriber<? super T> subscriber;

    /**
     * Create a {@code TestPublisher} with the defaults. See <b>Defaults</b> section of class javadoc.
     */
    public TestPublisher() {
        this(new Builder<T>().buildSubscriberFunction());
    }

    private TestPublisher(final Function<Subscriber<? super T>, Subscriber<? super T>> subscriberFunction) {
        this.subscriberFunction = subscriberFunction;
    }

    /**
     * Returns {@code true} if this {@link TestPublisher} has been subscribed to, {@code false} otherwise.
     *
     * @return {@code true} if this {@link TestPublisher} has been subscribed to, {@code false} otherwise.
     */
    public final boolean isSubscribed() {
        return subscriber != null;
    }

    @Override
    protected final void handleSubscribe(final Subscriber<? super T> subscriber) {
        try {
            initializeSubscriber(subscriber);
        } catch (final Throwable t) {
            record(t);
        }
    }

    /**
     * Initialize the {@link Subscriber} reference retained by this {@link TestPublisher}. After this method is invoked
     * the {@link #isSubscribed()} method should return {@code true}.
     *
     * @param subscriber a {@link Subscriber} from a {@link #subscribe(Subscriber)} invocation.
     */
    protected void initializeSubscriber(final Subscriber<? super T> subscriber) {
        this.subscriber = requireNonNull(subscriberFunction.apply(subscriber));
    }

    @Override
    public final void subscribe(final Subscriber<? super T> subscriber) {
        subscribeInternal(subscriber);
    }

    /**
     * Delivers the {@link Subscription} to the {@link Subscriber}'s {@link Subscriber#onSubscribe(Subscription)}.
     * <p>
     * In the case of {@link Builder#autoOnSubscribe() auto-on-subscribe}, the delegating {@link Subscription} sent to
     * the {@link Subscriber} by the auto-on-subscribe will switch to {@code subscription}.
     *
     * @param subscription the {@link Subscription}
     */
    public final void onSubscribe(final Subscription subscription) {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions("onSubscribe");
        subscriber.onSubscribe(subscription);
    }

    /**
     * Delivers the {@code items}, one at a time, to the {@link Subscriber}.
     *
     * @param items the items to deliver.
     * @see Subscriber#onNext(Object)
     */
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

    /**
     * Completes the {@link Subscriber}.
     *
     * @see Subscriber#onComplete()
     */
    public final void onComplete() {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions("onComplete");
        subscriber.onComplete();
    }

    /**
     * Delivers the {@link Throwable} {@code t} to the {@link Subscriber}.
     *
     * @param t the error to deliver.
     * @see Subscriber#onError(Throwable)
     */
    public final void onError(final Throwable t) {
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

    /**
     * Allows for creating {@link TestPublisher}s with non-default settings. For defaults, see <b>Defaults</b> section
     * of class javadoc.
     *
     * @param <T> Type of the items emitted by the {@code TestPublisher}.
     */
    public static class Builder<T> {

        @Nullable
        private Function<Subscriber<? super T>, Subscriber<? super T>> demandCheckingSubscriberFunction;
        @Nullable
        private Function<Subscriber<? super T>, Subscriber<? super T>> autoOnSubscribeSubscriberFunction =
                new AutoOnSubscribeSubscriberFunction<>();

        private Function<Subscriber<? super T>, Subscriber<? super T>> subscriberCardinalityFunction =
                new SequentialPublisherSubscriberFunction<>();

        /**
         * Allow concurrent subscribers. Default is to allow only sequential subscribers.
         *
         * @return this.
         * @see ConcurrentPublisherSubscriberFunction
         */
        public Builder<T> concurrentSubscribers() {
            subscriberCardinalityFunction = new ConcurrentPublisherSubscriberFunction<>();
            return this;
        }

        /**
         * Allow concurrent subscribers, with the specified {@link ConcurrentPublisherSubscriberFunction}.
         * Default is to allow only sequential subscribers.
         *
         * @param function the {@link ConcurrentPublisherSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> concurrentSubscribers(final ConcurrentPublisherSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Allow sequential subscribers. This is the default.
         *
         * @return this.
         * @see SequentialPublisherSubscriberFunction
         */
        public Builder<T> sequentialSubscribers() {
            subscriberCardinalityFunction = new SequentialPublisherSubscriberFunction<>();
            return this;
        }

        /**
         * Allow sequential subscribers, with the specified {@link SequentialPublisherSubscriberFunction}.
         * This is the default.
         *
         * @param function the {@link SequentialPublisherSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> sequentialSubscribers(final SequentialPublisherSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Allow only a single subscriber. Default is to allow sequential subscribers.
         *
         * @return this.
         * @see NonResubscribeablePublisherSubscriberFunction
         */
        public Builder<T> singleSubscriber() {
            subscriberCardinalityFunction = new NonResubscribeablePublisherSubscriberFunction<>();
            return this;
        }

        /**
         * Allow only a single subscriber, with the specified {@link NonResubscribeablePublisherSubscriberFunction}.
         * Default is to allow sequential subscribers.
         *
         * @param function the {@link NonResubscribeablePublisherSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> singleSubscriber(final NonResubscribeablePublisherSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Enables asserting items are not delivered without sufficient demand. The default is enabled.
         *
         * @return this.
         * @see DemandCheckingSubscriber
         */
        public Builder<T> enableDemandCheck() {
            demandCheckingSubscriberFunction = new DemandCheckingSubscriberFunction<>();
            return this;
        }

        /**
         * Enables asserting items are not delivered without sufficient demand, with the specified
         * {@link DemandCheckingSubscriberFunction}. The default is enabled.
         *
         * @param function the {@link DemandCheckingSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> enableDemandCheck(final DemandCheckingSubscriberFunction<T> function) {
            demandCheckingSubscriberFunction = requireNonNull(function);
            return this;
        }

        /**
         * Disables asserting items are not delivered without sufficient demand. The default is enabled.
         *
         * @return this.
         */
        public Builder<T> disableDemandCheck() {
            demandCheckingSubscriberFunction = null;
            return this;
        }

        /**
         * Enable calling {@link Subscriber#onSubscribe(Subscription)} automatically upon subscribe. The default is
         * enabled.
         *
         * @return this.
         * @see AutoOnSubscribeSubscriberFunction
         */
        public Builder<T> autoOnSubscribe() {
            autoOnSubscribeSubscriberFunction = new AutoOnSubscribeSubscriberFunction<>();
            return this;
        }

        /**
         * Enable calling {@link Subscriber#onSubscribe(Subscription)} automatically upon subscribe, with the specified
         * {@link AutoOnSubscribeSubscriberFunction}. The default is enabled.
         *
         * @param function the {@link AutoOnSubscribeSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> autoOnSubscribe(final AutoOnSubscribeSubscriberFunction<T> function) {
            autoOnSubscribeSubscriberFunction = requireNonNull(function);
            return this;
        }

        /**
         * Disable calling {@link Subscriber#onSubscribe(Subscription)} automatically upon subscribe. The default is
         * enabled.
         *
         * @return this.
         */
        public Builder<T> disableAutoOnSubscribe() {
            autoOnSubscribeSubscriberFunction = null;
            return this;
        }

        /**
         * Create a {@link TestPublisher} using the specified subscriber function.
         * <p>
         * All other settings from this {@link Builder} will be ignored.
         *
         * @param function The subscriber function to use.
         * @return a new {@link TestPublisher}.
         */
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

        /**
         * Create a {@link TestPublisher} as configured by the builder.
         *
         * @return a new {@link TestPublisher}.
         */
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
