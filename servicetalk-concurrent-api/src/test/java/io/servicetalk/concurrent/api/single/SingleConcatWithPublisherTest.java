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
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class SingleConcatWithPublisherTest {
    private TestPublisherSubscriber<Integer> subscriber;
    private TestSingle<Integer> source;
    private TestPublisher<Integer> next;
    private TestSubscription subscription;
    private TestCancellable cancellable;

    boolean deferSubscribe() {
        return false;
    }

    @BeforeEach
    void setUp() {
        subscriber = new TestPublisherSubscriber<>();
        cancellable = new TestCancellable();
        source = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build();
        next = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
        subscription = new TestSubscription();
        toSource(source.concat(next, deferSubscribe())).subscribe(subscriber);
        source.onSubscribe(cancellable);
        subscriber.awaitSubscription();
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> onNextErrorPropagatedParams() {
        return Stream.of(Arguments.of(1, false),
                Arguments.of(1, true),
                Arguments.of(2, false),
                Arguments.of(2, true));
    }

    @ParameterizedTest(name = "requestN={0} singleCompletesFirst={1}")
    @MethodSource("onNextErrorPropagatedParams")
    void onNextErrorPropagated(long n, boolean singleCompletesFirst) {
        subscriber = new TestPublisherSubscriber<>();
        source = new TestSingle<>();
        toSource(source.concat(next, deferSubscribe()).<Integer>map(x -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        if (singleCompletesFirst) {
            source.onSuccess(1);
        }
        subscriber.awaitSubscription().request(n);
        if (!singleCompletesFirst) {
            source.onSuccess(1);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertThat(next.isSubscribed(), is(false));
    }

    @Test
    void bothCompletion() {
        long requested = triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(2);
        assertThat("Unexpected items requested.", subscription.requested(), is(requested - 1 + 2));
        next.onNext(2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        next.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void sourceCompletionNextError() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        next.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void invalidRequestBeforeNextSubscribeNegative1() {
        invalidRequestBeforeNextSubscribe(-1);
    }

    @Test
    void invalidRequestBeforeNextSubscribeZero() {
        invalidRequestBeforeNextSubscribe(0);
    }

    private void invalidRequestBeforeNextSubscribe(long invalidN) {
        subscriber.awaitSubscription().request(invalidN);
        source.onSuccess(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void invalidRequestNWithInlineSourceCompletion() {
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(succeeded(1).concat(empty(), deferSubscribe())).subscribe(subscriber);
        subscriber.awaitSubscription().request(-1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void invalidRequestAfterNextSubscribe() {
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(-1);
        assertThat("Invalid request-n not propagated.", subscription.requested(), is(lessThan(0L)));
    }

    @Test
    void multipleInvalidRequest() {
        subscriber.awaitSubscription().request(-1);
        subscriber.awaitSubscription().request(-10);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void invalidThenValidRequestNegative1() {
        invalidThenValidRequest(-1);
    }

    @Test
    void invalidThenValidRequestZero() {
        invalidThenValidRequest(0);
    }

    private void invalidThenValidRequest(long invalidN) {
        subscriber.awaitSubscription().request(invalidN);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
        assertThat(cancellable.isCancelled(), is(true));
    }

    @Test
    void request0PropagatedAfterSuccess() {
        source.onSuccess(1);
        subscriber.awaitSubscription().request(deferSubscribe() ? 2 : 1); // get the success from the Single
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        assertThat(subscription.requested(), is(deferSubscribe() ? 1L : 0L));
        subscriber.awaitSubscription().request(0);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(0),
                is(true));
    }

    @Test
    void sourceError() {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat("Unexpected subscriber termination.", subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
    }

    @Test
    void cancelSource() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertThat("Original single not cancelled.", cancellable.isCancelled(), is(true));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
    }

    @Test
    void cancelSourcePostRequest() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
        assertThat("Original single not cancelled.", cancellable.isCancelled(), is(true));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
    }

    @Test
    void cancelNext() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertThat("Original single cancelled unexpectedly.", cancellable.isCancelled(), is(false));
        assertThat("Next source not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    void zeroIsNotRequestedOnTransitionToSubscription() {
        subscriber.awaitSubscription().request(1);
        source.onSuccess(1);
        if (deferSubscribe()) {
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

    @Test
    void onErrorAfterInvalidRequestN() {
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

    @Test
    void singleCompletesWithNull() {
        source.onSuccess(null);
        subscriber.awaitSubscription().request(2);
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        next.onComplete();
        assertThat("Unexpected next element.", subscriber.takeOnNext(), is(nullValue()));
        subscriber.awaitOnComplete();
    }

    @Test
    void demandAccumulatedBeforeSingleCompletes() {
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

    @Test
    void requestOneThenMore() {
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

    @Test
    void reentryWithMoreDemand() {
        List<Integer> emitted = new ArrayList<>();
        boolean[] completed = {false};
        toSource(succeeded(1).concat(from(2), deferSubscribe())).subscribe(new Subscriber<Integer>() {

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

    @Test
    void cancelledDuringFirstOnNext() {
        List<Integer> emitted = new ArrayList<>();
        boolean[] terminated = {false};
        toSource(succeeded(1).concat(never(), deferSubscribe())).subscribe(new Subscriber<Integer>() {

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

    private long triggerNextSubscribe() {
        final long n = deferSubscribe() ? 2 : 1;
        subscriber.awaitSubscription().request(n);
        source.onSuccess(1);
        next.onSubscribe(subscription);
        return n;
    }
}
