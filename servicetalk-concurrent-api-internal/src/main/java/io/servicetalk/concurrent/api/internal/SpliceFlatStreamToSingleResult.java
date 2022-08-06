/*
 * Copyright © 2018, 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherToSingleOperator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.StacklessCancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.util.Objects.requireNonNull;

/**
 * This operator is responsible for splicing a {@link Publisher}&lt;{@link Object}&gt; into the first emitted element of
 * type {@link First} followed by a {@link Publisher}&lt;{@link Following}&gt; of remaining elements from the original
 * {@link Publisher}&lt;{@link Object}&gt;. The spliced stream will be returned as a
 * {@link Single}&lt;{@link Result}&gt; that should combine {@link First} and {@link Publisher}&lt;{@link Following}&gt;
 * .
 * <p>
 * When {@link Publisher}&lt;{@link Object}&gt; terminates without emitting any item, {@link Single} will complete with
 * {@code null} instead of {@link Result} and the {@code packer} function won't be invoked.
 *
 * @param <First> type of the first element of the {@link Publisher}&lt;{@link Object}&gt;
 * @param <Following> type of the following elements of the {@link Publisher}&lt;{@link Object}&gt;
 * @param <Result> type of the result that combines {@link First} and {@link Publisher}&lt;{@link Following}&gt;
 */
public final class SpliceFlatStreamToSingleResult<First, Following, Result>
        implements PublisherToSingleOperator<Object, Result> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpliceFlatStreamToSingleResult.class);
    private final BiFunction<First, Publisher<Following>, Result> packer;

    /**
     * Creates a new instance.
     *
     * @param packer function to pack the {@link First} and {@link Publisher}&lt;{@link Following}&gt; into a
     * {@link Result}, should never return {@code null}
     */
    public SpliceFlatStreamToSingleResult(BiFunction<First, Publisher<Following>, Result> packer) {
        this.packer = requireNonNull(packer);
    }

    @Override
    public PublisherSource.Subscriber<Object> apply(Subscriber<? super Result> subscriber) {
        return new SplicingSubscriber<>(this, subscriber);
    }

    private static final class SplicingSubscriber<First, Following, Result>
            implements PublisherSource.Subscriber<Object> {
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<SplicingSubscriber, Object>
                maybeFollowingSubUpdater = AtomicReferenceFieldUpdater.newUpdater(SplicingSubscriber.class,
                Object.class, "maybeFollowingSub");

        private static final String CANCELED = "CANCELED";
        private static final String PENDING = "PENDING";
        private static final String EMPTY_COMPLETED = "EMPTY_COMPLETED";
        private static final String EMPTY_COMPLETED_DELIVERED = "EMPTY_COMPLETED_DELIVERED";

        /**
         * A field that assumes various types and states depending on the state of the operator.
         * <p>
         * One of <ul>
         *     <li>{@code null} – initial pending state before the {@link Single} is completed</li>
         *     <li>{@link PublisherSource.Subscriber}&lt;{@link Following}&gt; - when subscribed to the following</li>
         *     <li>{@link #CANCELED} - when the {@link Single} is canceled prematurely</li>
         *     <li>{@link #PENDING} - when the {@link Single} will complete and {@link Following} pending subscribe</li>
         *     <li>{@link #EMPTY_COMPLETED} - when the stream completed prematurely (empty)</li>
         *     <li>{@link #EMPTY_COMPLETED_DELIVERED} - when the premature (empty) completion event was delivered to a
         *     subscriber</li>
         *     <li>{@link Throwable} - the error that occurred in the stream</li>
         * </ul>
         */
        @Nullable
        @SuppressWarnings("unused")
        private volatile Object maybeFollowingSub;

        /**
         * Once a {@link #maybeFollowingSub} is set to a {@link PublisherSource.Subscriber} we cache a copy in a
         * non-volatile field to allow caching in register and avoid instanceof and casting on the hot path.
         */
        @Nullable
        private PublisherSource.Subscriber<Following> followingSubscriber;

        /**
         * Indicates whether the {@link First} has been observed.
         */
        private boolean firstSeenInOnNext;

        /**
         * The {@link Subscription} before wrapping to pass it to the downstream {@link PublisherSource.Subscriber}.
         * Doesn't need to be {@code volatile}, as it should be visible wrt JMM according to
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2.11>RS spec 2.11</a>
         */
        @Nullable
        private Subscription rawSubscription;

        /**
         * We request-1 first and then send {@link Subscriber#onSubscribe(Cancellable)} to {@link #resultSubscriber}.
         * If request-1 synchronously delivers {@link #onNext(Object)}, then we may send
         * {@link Subscriber#onSuccess(Object)} before onSubscribe.
         * This state makes sure we always send onSubscribe first and only once.
         */
        private boolean onSubscribeSent;

        private final SpliceFlatStreamToSingleResult<First, Following, Result> parent;
        private final Subscriber<? super Result> resultSubscriber;

        /**
         * This operator subscribes to one stream and forks into a {@link Single} and {@link Publisher}, so
         * the only risk for concurrently accessing the upstream {@link Subscription} is when the {@link Single}
         * gets canceled after the contained {@link Publisher} is subscribed. To avoid this problem we guard it with
         * a CAS on {@link Single} termination.
         *
         * @param parent reference to the parent class holding immutable state
         * @param resultSubscriber {@link Subscriber} to the {@link Result}
         */
        private SplicingSubscriber(SpliceFlatStreamToSingleResult<First, Following, Result> parent,
                                   Subscriber<? super Result> resultSubscriber) {
            this.parent = parent;
            this.resultSubscriber = resultSubscriber;
        }

        /**
         * This cancels the {@link Subscription} of the upstream {@link Publisher}&lt;{@link Object}&gt; unless this
         * {@link Single} has already terminated.
         * <p>
         * Guarded by the CAS to avoid concurrency with the {@link Subscription} on the contained {@link
         * Publisher}&lt;{@link Following}&gt;
         */
        private void cancelResult(Subscription subscription) {
            if (maybeFollowingSubUpdater.compareAndSet(this, null, CANCELED)) {
                subscription.cancel();
            }
        }

        @Override
        public void onSubscribe(final Subscription inStreamSubscription) {
            if (!checkDuplicateSubscription(rawSubscription, inStreamSubscription)) {
                return;
            }
            rawSubscription = inStreamSubscription;
            // Request the First element that we need to complete the SingleSource<Result>
            rawSubscription.request(1);
            if (!onSubscribeSent) {
                onSubscribeSent = true;
                resultSubscriber.onSubscribe(() -> cancelResult(inStreamSubscription));
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onNext(@Nullable Object obj) {
            if (firstSeenInOnNext) {
                Following following = (Following) obj;
                if (followingSubscriber != null) {
                    followingSubscriber.onNext(following);
                } else {
                    final Object subscriber = maybeFollowingSub;
                    if (subscriber instanceof PublisherSource.Subscriber) {
                        followingSubscriber = (PublisherSource.Subscriber<Following>) subscriber;
                        followingSubscriber.onNext(following);
                    }
                }
            } else {
                ensureResultSubscriberOnSubscribe();
                First first = (First) obj;
                // When the upstream Publisher is canceled we don't give it to any Following Subscribers
                firstSeenInOnNext = true;
                final Result result;
                try {
                    result = parent.packer.apply(first, maybeFollowingSubUpdater.compareAndSet(this, null, PENDING) ?
                            newFollowingPublisher() : Publisher.failed(StacklessCancellationException.newInstance(
                                    "Canceled prematurely from Result", SplicingSubscriber.class, "cancelResult(..)")));
                    requireNonNull(result, "Packer function must return non-null Result");
                } catch (Throwable t) {
                    assert rawSubscription != null;
                    // We know that there is nothing else that can happen on this stream as we are not sending the
                    // Result to the resultSubscriber.
                    rawSubscription.cancel();
                    // Since we update our internal state before calling parent.packer, if parent.packer throws,
                    // it will cause the assumptions to break in onError(). So, we catch and handle the error ourselves
                    // as opposed to let the source call onError.
                    resultSubscriber.onError(t);
                    return;
                }
                resultSubscriber.onSuccess(result);
            }
        }

        @Nonnull
        private Publisher<Following> newFollowingPublisher() {
            return new SubscribablePublisher<Following>() {
                @Override
                protected void handleSubscribe(PublisherSource.Subscriber<? super Following> newSubscriber) {
                    final DelayedSubscription delayedSubscription = new DelayedSubscription();
                    // newSubscriber.onSubscribe MUST be called before making newSubscriber visible below with the CAS
                    // on maybeFollowingSubUpdater. Otherwise there is a potential for concurrent invocation on the
                    // Subscriber which is not allowed by the Reactive Streams specification.
                    newSubscriber.onSubscribe(delayedSubscription);
                    if (maybeFollowingSubUpdater.compareAndSet(SplicingSubscriber.this, PENDING, newSubscriber)) {
                        assert rawSubscription != null;
                        delayedSubscription.delayedSubscription(rawSubscription);
                    } else {
                        // Entering this branch means either a duplicate subscriber or a stream that completed or failed
                        // without a subscriber present. The consequence is that unless we've seen Following data we may
                        // not send onComplete() or onError() to the original subscriber, but that is OK as long as one
                        // subscriber of them gets the correct signal and all others get a duplicate subscriber error.
                        final Object maybeSubscriber = SplicingSubscriber.this.maybeFollowingSub;
                        delayedSubscription.delayedSubscription(EMPTY_SUBSCRIPTION);
                        if (maybeSubscriber == EMPTY_COMPLETED && maybeFollowingSubUpdater
                                .compareAndSet(SplicingSubscriber.this, EMPTY_COMPLETED, EMPTY_COMPLETED_DELIVERED)) {
                            // Prematurely completed (First + empty Publisher<Following>)
                            newSubscriber.onComplete();
                        } else if (maybeSubscriber instanceof Throwable && maybeFollowingSubUpdater
                                .compareAndSet(SplicingSubscriber.this, maybeSubscriber, EMPTY_COMPLETED_DELIVERED)) {
                            // Premature error or cancel
                            newSubscriber.onError((Throwable) maybeSubscriber);
                        } else {
                            // Existing subscriber or terminal event consumed by other subscriber (COMPLETED_DELIVERED)
                            newSubscriber.onError(new DuplicateSubscribeException(maybeSubscriber, newSubscriber));
                        }
                    }
                }
            };
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onError(Throwable t) {
            if (followingSubscriber != null) { // We have a subscriber that has seen onNext()
                followingSubscriber.onError(t);
            } else {
                final Object maybeSubscriber = maybeFollowingSubUpdater.getAndSet(this, t);
                if (maybeSubscriber == CANCELED || !firstSeenInOnNext) {
                    ensureResultSubscriberOnSubscribe();
                    resultSubscriber.onError(t);
                } else if (maybeSubscriber instanceof PublisherSource.Subscriber) {
                    if (maybeFollowingSubUpdater.compareAndSet(this, t, EMPTY_COMPLETED_DELIVERED)) {
                        ((PublisherSource.Subscriber<Following>) maybeSubscriber).onError(t);
                    } else {
                        ((PublisherSource.Subscriber<Following>) maybeSubscriber).onError(new IllegalStateException(
                                "Duplicate Subscribers are not allowed. Existing: " + maybeSubscriber +
                                        ", failed the race with a duplicate, but neither has seen onNext()"));
                    }
                } else {
                    LOGGER.debug("Terminal error queued for delayed delivery to the Publisher<Following>. " +
                            "If it is not subscribed, this event will not be delivered.", t);
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onComplete() {
            if (followingSubscriber != null) { // We have a subscriber that has seen onNext()
                followingSubscriber.onComplete();
            } else {
                final Object maybeSubscriber = maybeFollowingSubUpdater.getAndSet(this, EMPTY_COMPLETED);
                if (maybeSubscriber instanceof PublisherSource.Subscriber) {
                    if (maybeFollowingSubUpdater.compareAndSet(this, EMPTY_COMPLETED, EMPTY_COMPLETED_DELIVERED)) {
                        ((PublisherSource.Subscriber<Following>) maybeSubscriber).onComplete();
                    } else {
                        ((PublisherSource.Subscriber<Following>) maybeSubscriber).onError(new IllegalStateException(
                                "Duplicate Subscribers are not allowed. Existing: " + maybeSubscriber +
                                        ", failed the race with a duplicate, but neither has seen onNext()"));
                    }
                } else if (!firstSeenInOnNext) {
                    ensureResultSubscriberOnSubscribe();
                    resultSubscriber.onSuccess(null);
                }
            }
        }

        private void ensureResultSubscriberOnSubscribe() {
            assert !firstSeenInOnNext;
            if (!onSubscribeSent) {
                onSubscribeSent = true;
                // Since we are going to deliver data or a terminal signal right after this,
                // there is no need for this to be cancellable.
                resultSubscriber.onSubscribe(IGNORE_CANCEL);
            }
        }
    }
}
