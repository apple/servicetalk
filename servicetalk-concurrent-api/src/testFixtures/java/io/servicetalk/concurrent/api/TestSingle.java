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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.test.internal.AwaitUtils.awaitUninterruptibly;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Single} &amp; {@link SingleSource} whose outgoing signals to its {@link Subscriber}s can be controlled.
 * <p>
 * Behavior beyond simply delegating signals to the {@link Subscriber} is accomplished by a
 * {@link Function Function&lt;Subscriber&lt;? super T&gt;, Subscriber&lt;? super T&gt;&gt;}. This {@link Function} is
 * invoked for every {@link #subscribe(Subscriber)} invocation, and the result is used as the delegate for subsequent
 * {@link #onSubscribe(Cancellable)}, {@link #onSuccess(Object)}, and
 * {@link #onError(Throwable)} calls. See {@link Builder} for more information.
 *
 * <h3>Defaults</h3>
 * <ul>
 *     <li>Allows sequential but not concurrent subscribers.</li>
 *     <li>Sends {@link #onSubscribe(Cancellable)} automatically when subscribed to.</li>
 * </ul>
 *
 * @param <T> Type of the result of this {@code TestSingle}.
 */
public final class TestSingle<T> extends Single<T> implements SingleSource<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestSingle.class);
    private static final AtomicReferenceFieldUpdater<TestSingle, Subscriber> subscriberUpdater =
            AtomicReferenceFieldUpdater.newUpdater(TestSingle.class, Subscriber.class, "subscriber");
    private final Function<Subscriber<? super T>, Subscriber<? super T>> subscriberFunction;
    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();
    private volatile Subscriber<? super T> subscriber = new WaitingSubscriber<>();
    private final CountDownLatch subscriberLatch = new CountDownLatch(1);

    /**
     * Create a {@code TestSingle} with the defaults. See <b>Defaults</b> section of class javadoc.
     */
    public TestSingle() {
        this(new Builder<T>().buildSubscriberFunction());
    }

    private TestSingle(final Function<Subscriber<? super T>, Subscriber<? super T>> subscriberFunction) {
        this.subscriberFunction = requireNonNull(subscriberFunction);
    }

    /**
     * Returns {@code true} if this {@link TestSingle} has been subscribed to, {@code false} otherwise.
     *
     * @return {@code true} if this {@link TestSingle} has been subscribed to, {@code false} otherwise.
     */
    public boolean isSubscribed() {
        return !(subscriber instanceof WaitingSubscriber);
    }

    /**
     * Awaits until this Single is subscribed, even if interrupted. If interrupted the {@link Thread#isInterrupted()}
     * will be set upon return.
     */
    public void awaitSubscribed() {
        awaitUninterruptibly(subscriberLatch);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber) {
        try {
            Subscriber<? super T> newSubscriber = requireNonNull(subscriberFunction.apply(subscriber));
            for (;;) {
                Subscriber<? super T> currSubscriber = this.subscriber;
                if (subscriberUpdater.compareAndSet(this, currSubscriber, newSubscriber)) {
                    if (currSubscriber instanceof WaitingSubscriber) {
                        @SuppressWarnings("unchecked")
                        final WaitingSubscriber<T> waiter = (WaitingSubscriber<T>) currSubscriber;
                        waiter.realSubscriber(newSubscriber);
                    }
                    subscriberLatch.countDown();
                    break;
                }
            }
        } catch (final Throwable t) {
            record(t);
        }
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        subscribeInternal(subscriber);
    }

    /**
     * Delivers the {@link Cancellable} to the {@link Subscriber}'s {@link Subscriber#onSubscribe(Cancellable)}.
     * <p>
     * In the case of {@link Builder#autoOnSubscribe() auto-on-subscribe}, the delegating {@link Cancellable} sent to
     * the {@link Subscriber} by the auto-on-subscribe will switch to {@code cancellable}.
     *
     * @param cancellable the {@link Cancellable}
     */
    public void onSubscribe(final Cancellable cancellable) {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions();
        subscriber.onSubscribe(cancellable);
    }

    /**
     * Delivers the {@code result} to the {@link Subscriber}.
     *
     * @param result the result to deliver.
     * @see Subscriber#onSuccess(Object)
     */
    public void onSuccess(@Nullable final T result) {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions();
        subscriber.onSuccess(result);
    }

    /**
     * Delivers the {@link Throwable} {@code t} to the {@link Subscriber}.
     *
     * @param t the error to deliver.
     * @see Subscriber#onError(Throwable)
     */
    public void onError(final Throwable t) {
        final Subscriber<? super T> subscriber = checkSubscriberAndExceptions();
        subscriber.onError(t);
    }

    private Subscriber<? super T> checkSubscriberAndExceptions() {
        if (!exceptions.isEmpty()) {
            final RuntimeException exception = new RuntimeException("Unexpected exception(s) encountered",
                    exceptions.get(0));
            for (int i = 1; i < exceptions.size(); i++) {
                exception.addSuppressed(exceptions.get(i));
            }
            throw exception;
        }
        return subscriber;
    }

    private void record(final Throwable t) {
        requireNonNull(t);
        LOGGER.warn("Unexpected exception", t);
        exceptions.add(t);
    }

    /**
     * Allows for creating {@link TestSingle}s with non-default settings. For defaults, see <b>Defaults</b> section
     * of class javadoc.
     *
     * @param <T> Type of the result of the {@code TestSingle}.
     */
    public static class Builder<T> {

        @Nullable
        private Function<Subscriber<? super T>, Subscriber<? super T>> autoOnSubscribeFunction =
                new AutoOnSubscribeSingleSubscriberFunction<>();

        private Function<Subscriber<? super T>, Subscriber<? super T>> subscriberCardinalityFunction =
                new SequentialSingleSubscriberFunction<>();

        /**
         * Allow concurrent subscribers. Default is to allow only sequential subscribers.
         *
         * @return this.
         * @see ConcurrentSingleSubscriberFunction
         */
        public Builder<T> concurrentSubscribers() {
            subscriberCardinalityFunction = new ConcurrentSingleSubscriberFunction<>();
            return this;
        }

        /**
         * Allow concurrent subscribers, with the specified {@link ConcurrentSingleSubscriberFunction}.
         * Default is to allow only sequential subscribers.
         *
         * @param function the {@link ConcurrentSingleSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> concurrentSubscribers(final ConcurrentSingleSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Allow sequential subscribers. This is the default.
         *
         * @return this.
         * @see SequentialSingleSubscriberFunction
         */
        public Builder<T> sequentialSubscribers() {
            subscriberCardinalityFunction = new SequentialSingleSubscriberFunction<>();
            return this;
        }

        /**
         * Allow sequential subscribers, with the specified {@link SequentialSingleSubscriberFunction}.
         * This is the default.
         *
         * @param function the {@link SequentialSingleSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> sequentialSubscribers(final SequentialSingleSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Allow only a single subscriber. Default is to allow sequential subscribers.
         *
         * @return this.
         * @see NonResubscribeableSingleSubscriberFunction
         */
        public Builder<T> singleSubscriber() {
            subscriberCardinalityFunction = new NonResubscribeableSingleSubscriberFunction<>();
            return this;
        }

        /**
         * Allow only a single subscriber, with the specified {@link NonResubscribeableSingleSubscriberFunction}.
         * Default is to allow sequential subscribers.
         *
         * @param function the {@link NonResubscribeableSingleSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> singleSubscriber(final NonResubscribeableSingleSubscriberFunction<T> function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Enable calling {@link Subscriber#onSubscribe(Cancellable)} automatically upon subscribe. The default is
         * enabled.
         *
         * @return this.
         * @see AutoOnSubscribeSingleSubscriberFunction
         */
        public Builder<T> autoOnSubscribe() {
            autoOnSubscribeFunction = new AutoOnSubscribeSingleSubscriberFunction<>();
            return this;
        }

        /**
         * Enable calling {@link Subscriber#onSubscribe(Cancellable)} automatically upon subscribe, with the specified
         * {@link AutoOnSubscribeSingleSubscriberFunction}. The default is enabled.
         *
         * @param function the {@link AutoOnSubscribeSingleSubscriberFunction} to use.
         * @return this.
         */
        public Builder<T> autoOnSubscribe(final AutoOnSubscribeSingleSubscriberFunction<T> function) {
            autoOnSubscribeFunction = requireNonNull(function);
            return this;
        }

        /**
         * Disable calling {@link Subscriber#onSubscribe(Cancellable)} automatically upon subscribe. The default is
         * enabled.
         *
         * @return this.
         */
        public Builder<T> disableAutoOnSubscribe() {
            autoOnSubscribeFunction = null;
            return this;
        }

        /**
         * Create a {@link TestSingle} using the specified subscriber function.
         * <p>
         * All other settings from this {@link Builder} will be ignored.
         *
         * @param function The subscriber function to use.
         * @return a new {@link TestSingle}.
         */
        public TestSingle<T> build(final Function<Subscriber<? super T>, Subscriber<? super T>> function) {
            return new TestSingle<>(function);
        }

        private Function<Subscriber<? super T>, Subscriber<? super T>> buildSubscriberFunction() {
            Function<Subscriber<? super T>, Subscriber<? super T>> subscriberFunction =
                    autoOnSubscribeFunction;
            subscriberFunction = andThen(subscriberFunction, subscriberCardinalityFunction);
            assert subscriberFunction != null;
            return subscriberFunction;
        }

        /**
         * Create a {@link TestSingle} as configured by the builder.
         *
         * @return a new {@link TestSingle}.
         */
        public TestSingle<T> build() {
            return new TestSingle<>(buildSubscriberFunction());
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

    private static final class WaitingSubscriber<T> implements Subscriber<T> {
        private final SingleProcessor<Subscriber<? super T>> realSubscriberSingle = new SingleProcessor<>();

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            waitForSubscriber().onSubscribe(cancellable);
        }

        @Override
        public void onSuccess(@Nullable final T t) {
            waitForSubscriber().onSuccess(t);
        }

        @Override
        public void onError(final Throwable t) {
            waitForSubscriber().onError(t);
        }

        void realSubscriber(Subscriber<? super T> subscriber) {
            realSubscriberSingle.onSuccess(subscriber);
        }

        private Subscriber<? super T> waitForSubscriber() {
            try {
                return realSubscriberSingle.toFuture().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
