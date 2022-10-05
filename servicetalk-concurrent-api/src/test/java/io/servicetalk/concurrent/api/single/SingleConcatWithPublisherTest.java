/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.hamcrest.Matchers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.single.SingleConcatWithPublisherTest.ConcatMode.CONCAT;
import static io.servicetalk.concurrent.api.single.SingleConcatWithPublisherTest.ConcatMode.DEFER_SUBSCRIBE;
import static io.servicetalk.concurrent.api.single.SingleConcatWithPublisherTest.ConcatMode.PROPAGATE_CANCEL;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class SingleConcatWithPublisherTest {
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();
    private final TestCancellable cancellable = new TestCancellable();
    private TestSingle<Integer> source = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build();
    private TestPublisher<Integer> next = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();

    enum ConcatMode {
        CONCAT, DEFER_SUBSCRIBE, PROPAGATE_CANCEL
    }

    private static Publisher<Integer> doConcat(ConcatMode mode, Single<Integer> source, Publisher<Integer> next) {
        switch (mode) {
            case CONCAT:
                return source.concat(next);
            case DEFER_SUBSCRIBE:
                return source.concatDeferSubscribe(next);
            case PROPAGATE_CANCEL:
                return source.concatPropagateCancel(next);
            default:
                throw new AssertionError("Unexpected mode: " + mode);
        }
    }

    void setUp(ConcatMode mode) {
        toSource(doConcat(mode, source, next)).subscribe(subscriber);
        source.onSubscribe(cancellable);
        subscriber.awaitSubscription();
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> invalidRequestN() {
        return Stream.of(
                Arguments.of(CONCAT, -1),
                Arguments.of(CONCAT, 0),
                Arguments.of(DEFER_SUBSCRIBE, -1),
                Arguments.of(DEFER_SUBSCRIBE, 0),
                Arguments.of(PROPAGATE_CANCEL, -1),
                Arguments.of(PROPAGATE_CANCEL, 0));
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> modeAndError() {
        return Stream.of(
                Arguments.of(CONCAT, true),
                Arguments.of(CONCAT, false),
                Arguments.of(DEFER_SUBSCRIBE, true),
                Arguments.of(DEFER_SUBSCRIBE, false),
                Arguments.of(PROPAGATE_CANCEL, true),
                Arguments.of(PROPAGATE_CANCEL, false));
    }

    @SuppressWarnings("unused")
    private static Collection<Arguments> onNextErrorPropagatedParams() {
        List<Arguments> args = new ArrayList<>();
        for (ConcatMode mode : ConcatMode.values()) {
            for (long requestN : asList(1, 2)) {
                for (boolean singleCompletesFirst : asList(false, true)) {
                    args.add(Arguments.of(mode, requestN, singleCompletesFirst));
                }
            }
        }
        return args;
    }

    @ParameterizedTest(name = "mode={0} requestN={2} singleCompletesFirst={3}")
    @MethodSource("onNextErrorPropagatedParams")
    void onNextErrorPropagated(ConcatMode mode, long n, boolean singleCompletesFirst)
            throws Exception {
        toSource(doConcat(mode, source, next).<Integer>map(x -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        source.onSubscribe(cancellable);
        subscriber.awaitSubscription();
        if (singleCompletesFirst) {
            source.onSuccess(1);
        }
        subscriber.awaitSubscription().request(n);
        if (!singleCompletesFirst) {
            source.onSuccess(1);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        if (mode == PROPAGATE_CANCEL) {
            next.awaitSubscribed();
            next.onSubscribe(subscription);
            subscription.awaitCancelled();
        } else {
            assertThat(next.isSubscribed(), is(false));
        }
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void bothCompletion(ConcatMode mode) {
        setUp(mode);
        long requested = triggerNextSubscribe(mode);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(2);
        assertThat("Unexpected items requested.", subscription.requested(), is(requested - 1 + 2));
        next.onNext(2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        next.onComplete();
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void sourceCompletionNextError(ConcatMode mode) {
        setUp(mode);
        triggerNextSubscribe(mode);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        next.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "mode={0} invalidRequestN={1}")
    @MethodSource("invalidRequestN")
    void invalidRequestNBeforeNextSubscribe(ConcatMode mode, long invalidRequestN) {
        setUp(mode);
        subscriber.awaitSubscription().request(invalidRequestN);
        source.onSuccess(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void invalidRequestNWithInlineSourceCompletion(ConcatMode mode) {
        toSource(doConcat(mode, succeeded(1), empty())).subscribe(subscriber);
        subscriber.awaitSubscription().request(-1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void invalidRequestAfterNextSubscribe(ConcatMode mode) {
        setUp(mode);
        triggerNextSubscribe(mode);
        subscriber.awaitSubscription().request(-1);
        assertThat("Invalid request-n not propagated.", subscription.requested(), is(lessThan(0L)));
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void multipleInvalidRequest(ConcatMode mode) {
        setUp(mode);
        subscriber.awaitSubscription().request(-1);
        subscriber.awaitSubscription().request(-10);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "mode={0} invalidRequestN={1}")
    @MethodSource("invalidRequestN")
    void invalidThenValidRequest(ConcatMode mode, long invalidRequestN) {
        setUp(mode);
        subscriber.awaitSubscription().request(invalidRequestN);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
        assertThat(cancellable.isCancelled(), is(true));
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void request0PropagatedAfterSuccess(ConcatMode mode) {
        setUp(mode);
        source.onSuccess(1);
        subscriber.awaitSubscription().request(mode == DEFER_SUBSCRIBE ? 2 : 1); // get the success from the Single
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        assertThat(subscription.requested(), is(mode == DEFER_SUBSCRIBE ? 1L : 0L));
        subscriber.awaitSubscription().request(0);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(0),
                is(true));
    }

    @ParameterizedTest(name = "mode={0} error={1}")
    @MethodSource("modeAndError")
    void sourceError(ConcatMode mode, boolean error) throws InterruptedException {
        setUp(mode);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        if (mode == PROPAGATE_CANCEL) {
            next.awaitSubscribed();
            next.onSubscribe(subscription);
            subscription.awaitCancelled();

            // Test that no duplicate terminal events are delivered.
            if (error) {
                next.onError(new DeliberateException());
            } else {
                next.onComplete();
            }
        } else {
            assertThat(next.isSubscribed(), is(false));
        }
    }

    @ParameterizedTest(name = "mode={0} error={1}")
    @MethodSource("modeAndError")
    void cancelSource(ConcatMode mode, boolean error) throws InterruptedException {
        setUp(mode);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        Subscription subscription1 = subscriber.awaitSubscription();
        subscription1.request(2);
        subscription1.cancel();
        assertThat("Original single not cancelled.", cancellable.isCancelled(), is(true));

        if (error) {
            source.onError(DELIBERATE_EXCEPTION);
        } else {
            source.onSuccess(1);
        }

        if (mode == PROPAGATE_CANCEL) {
            next.awaitSubscribed();
            next.onSubscribe(subscription);
            subscription.awaitCancelled();

            if (error) {
                next.onError(new DeliberateException());
            } else {
                next.onComplete();
            }

            // It is not required that no terminal is delivered after cancel but verifies the current implementation for
            // thread safety on the subscriber and to avoid duplicate terminals.
            assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        } else {
            assertThat(next.isSubscribed(), is(false));

            if (error) {
                assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
            } else {
                // It is not required that no terminal is delivered after cancel but verifies the current implementation
                // for thread safety on the subscriber and to avoid duplicate terminals.
                assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
            }
        }
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void cancelSourcePostRequest(ConcatMode mode) {
        setUp(mode);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
        assertThat("Original single not cancelled.", cancellable.isCancelled(), is(true));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(mode == PROPAGATE_CANCEL));
    }

    @ParameterizedTest(name = "mode={0} error={1}")
    @MethodSource("modeAndError")
    void cancelNext(ConcatMode mode, boolean error) {
        setUp(mode);
        triggerNextSubscribe(mode);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertThat("Original single cancelled unexpectedly.", cancellable.isCancelled(), is(false));
        assertThat("Next source not cancelled.", subscription.isCancelled(), is(true));

        assertThat(subscriber.takeOnNext(), equalTo(1));
        if (error) {
            next.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            next.onComplete();
            subscriber.awaitOnComplete();
        }
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void zeroIsNotRequestedOnTransitionToSubscription(ConcatMode mode) {
        setUp(mode);
        subscriber.awaitSubscription().request(1);
        source.onSuccess(1);
        if (mode == DEFER_SUBSCRIBE) {
            assertThat(next.isSubscribed(), is(false));
            subscriber.awaitSubscription().request(1);
            assertThat(next.isSubscribed(), is(true));
            next.onSubscribe(subscription);
            assertThat("Invalid request-n propagated " + subscription, subscription.requestedEquals(1), is(true));
        } else {
            assertThat(next.isSubscribed(), is(true));
            next.onSubscribe(subscription);
            assertThat("Invalid request-n propagated " + subscription, subscription.requestedEquals(0), is(false));
        }
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void publisherSubscribeBlockDemandMakesProgress(ConcatMode mode) {
        source = new TestSingle<>();
        next = new TestPublisher.Builder<Integer>().build(sub1 -> {
            sub1.onSubscribe(subscription);
            try {
                // Simulate a blocking operation on demand, like ConnectablePayloadWriter.
                subscription.awaitRequestN(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throwException(e);
            }
            return sub1;
        });
        setUp(mode);

        source.onSuccess(10);
        // Give at least 2 demand so there is enough to unblock the awaitRequestN and deliver data below.
        subscriber.awaitSubscription().request(2);

        next.onNext(11);
        next.onComplete();
        assertThat(subscriber.takeOnNext(2), contains(10, 11));
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void onErrorAfterInvalidRequestN(ConcatMode mode) {
        setUp(mode);
        source.onSuccess(1);
        subscriber.awaitSubscription().request(2L);
        assertThat("Unexpected next element.", subscriber.takeOnNext(), is(1));
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        assertThat(subscription.requested(), is(1L));
        next.onNext(2);
        assertThat("Unexpected next element.", subscriber.takeOnNext(), is(2));
        subscriber.awaitSubscription().request(-1L);
        assertThat("Invalid request-n not propagated.", subscription.requested(), is(-1L));
        // TestSubscriber does not automatically propagate onError when there is invalid demand, we will do it manually
        next.onError(new IllegalArgumentException());
        assertThat("Unexpected next items.", subscriber.pollAllOnNext(), Matchers.empty());
        assertThat("Unexpected terminal error.",
                subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void singleCompletesWithNull(ConcatMode mode) {
        setUp(mode);
        source.onSuccess(null);
        subscriber.awaitSubscription().request(2);
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        next.onComplete();
        assertThat("Unexpected next element.", subscriber.takeOnNext(), is(nullValue()));
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void demandAccumulatedBeforeSingleCompletes(ConcatMode mode) {
        setUp(mode);
        subscriber.awaitSubscription().request(3L);
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
        assertThat(subscription.requested(), is(0L));
        subscriber.awaitSubscription().request(2L);
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
        assertThat(subscription.requested(), is(0L));
        source.onSuccess(1);
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        assertThat(subscription.requested(), is(4L));
        next.onNext(2);
        assertThat(subscriber.pollAllOnNext(), contains(1, 2));
        next.onComplete();
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void requestOneThenMore(ConcatMode mode) {
        setUp(mode);
        subscriber.awaitSubscription().request(1L);
        subscriber.awaitSubscription().request(1L);
        assertThat(subscription.requested(), is(0L));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
        source.onSuccess(1);
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        assertThat(subscription.requested(), is(1L));
        next.onNext(2);
        assertThat(subscriber.pollAllOnNext(), contains(1, 2));
        next.onComplete();
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void reentryWithMoreDemand(ConcatMode mode) {
        List<Integer> emitted = new ArrayList<>();
        boolean[] completed = {false};
        toSource(doConcat(mode, succeeded(1), from(2))).subscribe(new Subscriber<Integer>() {
            @Nullable
            private Subscription subscription;

            @Override
            public void onSubscribe(final Subscription s) {
                subscription = s;
                subscription.request(1);
            }

            @Override
            public void onNext(@Nullable final Integer v) {
                emitted.add(v);
                assert subscription != null;
                subscription.request(1);
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
                completed[0] = true;
            }
        });

        assertThat(emitted, contains(1, 2));
        assertThat(completed[0], is(true));
    }

    @ParameterizedTest(name = "mode={0}")
    @EnumSource(ConcatMode.class)
    void cancelledDuringFirstOnNext(ConcatMode mode) {
        List<Integer> emitted = new ArrayList<>();
        boolean[] terminated = {false};
        toSource(doConcat(mode, succeeded(1), never())).subscribe(new Subscriber<Integer>() {
            @Nullable
            private Subscription subscription;

            @Override
            public void onSubscribe(final Subscription s) {
                subscription = s;
                subscription.request(1);
            }

            @Override
            public void onNext(@Nullable final Integer v) {
                emitted.add(v);
                assert subscription != null;
                subscription.cancel();
            }

            @Override
            public void onError(final Throwable t) {
                terminated[0] = true;
            }

            @Override
            public void onComplete() {
                terminated[0] = true;
            }
        });

        assertThat(emitted, contains(1));
        assertThat(terminated[0], is(false));
    }

    private long triggerNextSubscribe(ConcatMode mode) {
        final long n = mode == DEFER_SUBSCRIBE ? 2 : 1;
        subscriber.awaitSubscription().request(n);
        source.onSuccess(1);
        next.awaitSubscribed();
        next.onSubscribe(subscription);
        return n;
    }
}
