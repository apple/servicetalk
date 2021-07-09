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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.test.internal.AwaitUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Completable} &amp; {@link CompletableSource} whose outgoing signals to its {@link Subscriber}s can be
 * controlled.
 * <p>
 * Behavior beyond simply delegating signals to the {@link Subscriber} is accomplished by a
 * {@link Function Function&lt;Subscriber&lt;? super T&gt;, Subscriber&lt;? super T&gt;&gt;}. This {@link Function} is
 * invoked for every {@link #subscribe(Subscriber)} invocation, and the result is used as the delegate for subsequent
 * {@link #onSubscribe(Cancellable)}, {@link #onComplete()}, and
 * {@link #onError(Throwable)} calls. See {@link Builder} for more information.
 *
 * <h3>Defaults</h3>
 * <ul>
 * <li>Allows sequential but not concurrent subscribers.</li>
 * <li>Sends {@link #onSubscribe(Cancellable)} automatically when subscribed to.</li>
 * </ul>
 */
public final class TestCompletable extends Completable implements CompletableSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestCompletable.class);
    private static final AtomicReferenceFieldUpdater<TestCompletable, Subscriber> subscriberUpdater =
            AtomicReferenceFieldUpdater.newUpdater(TestCompletable.class, Subscriber.class, "subscriber");
    private final Function<Subscriber, Subscriber> subscriberFunction;
    private final List<Throwable> exceptions = new CopyOnWriteArrayList<>();
    private volatile Subscriber subscriber = new WaitingSubscriber();
    private final CountDownLatch subscriberLatch = new CountDownLatch(1);

    /**
     * Create a {@code TestCompletable} with the defaults. See <b>Defaults</b> section of class javadoc.
     */
    public TestCompletable() {
        this(new Builder().buildSubscriberFunction());
    }

    private TestCompletable(final Function<Subscriber, Subscriber> subscriberFunction) {
        this.subscriberFunction = requireNonNull(subscriberFunction);
    }

    /**
     * Returns {@code true} if this {@link TestCompletable} has been subscribed to, {@code false} otherwise.
     *
     * @return {@code true} if this {@link TestCompletable} has been subscribed to, {@code false} otherwise.
     */
    public boolean isSubscribed() {
        return !(subscriber instanceof WaitingSubscriber);
    }

    /**
     * Awaits until this {@link TestCompletable} is subscribed, even if interrupted. If interrupted the
     * {@link Thread#isInterrupted()} will be set upon return.
     */
    public void awaitSubscribed() {
        AwaitUtils.awaitUninterruptibly(subscriberLatch);
    }

    @Override
    protected void handleSubscribe(final Subscriber subscriber) {
        try {
            Subscriber newSubscriber = requireNonNull(subscriberFunction.apply(subscriber));
            for (;;) {
                Subscriber currSubscriber = this.subscriber;
                if (subscriberUpdater.compareAndSet(this, currSubscriber, newSubscriber)) {
                    if (currSubscriber instanceof WaitingSubscriber) {
                        final WaitingSubscriber waiter = (WaitingSubscriber) currSubscriber;
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
    public void subscribe(final Subscriber subscriber) {
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
        final Subscriber subscriber = checkSubscriberAndExceptions();
        subscriber.onSubscribe(cancellable);
    }

    /**
     * Completes the {@link Subscriber}.
     *
     * @see Subscriber#onComplete()
     */
    public void onComplete() {
        final Subscriber subscriber = checkSubscriberAndExceptions();
        subscriber.onComplete();
    }

    /**
     * Delivers the {@link Throwable} {@code t} to the {@link Subscriber}.
     *
     * @param t the error to deliver.
     * @see Subscriber#onError(Throwable)
     */
    public void onError(final Throwable t) {
        final Subscriber subscriber = checkSubscriberAndExceptions();
        subscriber.onError(t);
    }

    private Subscriber checkSubscriberAndExceptions() {
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
     * Allows creation of {@link TestCompletable}s with non-default settings. For defaults, see <b>Defaults</b> section
     * of class javadoc.
     */
    public static class Builder {

        @Nullable
        private Function<Subscriber, Subscriber> autoOnSubscribeFunction =
                new AutoOnSubscribeCompletableSubscriberFunction();

        private Function<Subscriber, Subscriber> subscriberCardinalityFunction =
                new SequentialCompletableSubscriberFunction();

        /**
         * Allow concurrent subscribers. Default is to allow only sequential subscribers.
         *
         * @return this.
         * @see ConcurrentCompletableSubscriberFunction
         */
        public Builder concurrentSubscribers() {
            subscriberCardinalityFunction = new ConcurrentCompletableSubscriberFunction();
            return this;
        }

        /**
         * Allow concurrent subscribers, with the specified {@link ConcurrentCompletableSubscriberFunction}.
         * Default is to allow only sequential subscribers.
         *
         * @param function the {@link ConcurrentCompletableSubscriberFunction} to use.
         * @return this.
         */
        public Builder concurrentSubscribers(final ConcurrentCompletableSubscriberFunction function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Allow sequential subscribers. This is the default.
         *
         * @return this.
         * @see SequentialCompletableSubscriberFunction
         */
        public Builder sequentialSubscribers() {
            subscriberCardinalityFunction = new SequentialCompletableSubscriberFunction();
            return this;
        }

        /**
         * Allow sequential subscribers, with the specified {@link SequentialCompletableSubscriberFunction}.
         * This is the default.
         *
         * @param function the {@link SequentialCompletableSubscriberFunction} to use.
         * @return this.
         */
        public Builder sequentialSubscribers(final SequentialCompletableSubscriberFunction function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Allow only a single subscriber. Default is to allow sequential subscribers.
         *
         * @return this.
         * @see NonResubscribeableCompletableSubscriberFunction
         */
        public Builder singleSubscriber() {
            subscriberCardinalityFunction = new NonResubscribeableCompletableSubscriberFunction();
            return this;
        }

        /**
         * Allow only a single subscriber, with the specified {@link NonResubscribeableCompletableSubscriberFunction}.
         * Default is to allow sequential subscribers.
         *
         * @param function the {@link NonResubscribeableCompletableSubscriberFunction} to use.
         * @return this.
         */
        public Builder singleSubscriber(final NonResubscribeableCompletableSubscriberFunction function) {
            subscriberCardinalityFunction = requireNonNull(function);
            return this;
        }

        /**
         * Enable calling {@link Subscriber#onSubscribe(Cancellable)} automatically upon subscribe. The default is
         * enabled.
         *
         * @return this.
         * @see AutoOnSubscribeCompletableSubscriberFunction
         */
        public Builder autoOnSubscribe() {
            autoOnSubscribeFunction = new AutoOnSubscribeCompletableSubscriberFunction();
            return this;
        }

        /**
         * Enable calling {@link Subscriber#onSubscribe(Cancellable)} automatically upon subscribe, with the specified
         * {@link AutoOnSubscribeCompletableSubscriberFunction}. The default is enabled.
         *
         * @param function the {@link AutoOnSubscribeCompletableSubscriberFunction} to use.
         * @return this.
         */
        public Builder autoOnSubscribe(final AutoOnSubscribeCompletableSubscriberFunction function) {
            autoOnSubscribeFunction = requireNonNull(function);
            return this;
        }

        /**
         * Disable calling {@link Subscriber#onSubscribe(Cancellable)} automatically upon subscribe. The default is
         * enabled.
         *
         * @return this.
         */
        public Builder disableAutoOnSubscribe() {
            autoOnSubscribeFunction = null;
            return this;
        }

        /**
         * Create a {@link TestCompletable} using the specified subscriber function.
         * <p>
         * All other settings from this {@link Builder} will be ignored.
         *
         * @param function The subscriber function to use.
         * @return a new {@link TestCompletable}.
         */
        public TestCompletable build(final Function<Subscriber, Subscriber> function) {
            return new TestCompletable(function);
        }

        private Function<Subscriber, Subscriber> buildSubscriberFunction() {
            Function<Subscriber, Subscriber> subscriberFunction =
                    autoOnSubscribeFunction;
            subscriberFunction = andThen(subscriberFunction, subscriberCardinalityFunction);
            assert subscriberFunction != null;
            return subscriberFunction;
        }

        /**
         * Create a {@link TestCompletable} as configured by the builder.
         *
         * @return a new {@link TestCompletable}.
         */
        public TestCompletable build() {
            return new TestCompletable(buildSubscriberFunction());
        }

        @Nullable
        private static Function<Subscriber, Subscriber>
        andThen(@Nullable final Function<Subscriber, Subscriber> first,
                @Nullable final Function<Subscriber, Subscriber> second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }
            return first.andThen(second);
        }
    }

    private static final class WaitingSubscriber implements Subscriber {
        private final SingleProcessor<Subscriber> realSubscriberSingle = new SingleProcessor<>();

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            waitForSubscriber().onSubscribe(cancellable);
        }

        @Override
        public void onComplete() {
            waitForSubscriber().onComplete();
        }

        @Override
        public void onError(final Throwable t) {
            waitForSubscriber().onError(t);
        }

        void realSubscriber(Subscriber subscriber) {
            realSubscriberSingle.onSuccess(subscriber);
        }

        private Subscriber waitForSubscriber() {
            try {
                return realSubscriberSingle.toFuture().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
