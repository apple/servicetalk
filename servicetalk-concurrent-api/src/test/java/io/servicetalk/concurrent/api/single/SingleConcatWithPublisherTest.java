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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
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

    void setUp(boolean deferSubscribe) {
        toSource(source.concat(next, deferSubscribe)).subscribe(subscriber);
        source.onSubscribe(cancellable);
        subscriber.awaitSubscription();
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> invalidRequestN() {
        return Stream.of(Arguments.of(false, -1),
                Arguments.of(false, 0),
                Arguments.of(true, -1),
                Arguments.of(true, 0));
    }

    @SuppressWarnings("unused")
    private static Collection<Arguments> onNextErrorPropagatedParams() {
        List<Arguments> args = new ArrayList<>();
        for (boolean deferSubscribe : asList(false, true)) {
            for (long requestN : asList(1, 2)) {
                for (boolean singleCompletesFirst : asList(false, true)) {
                    args.add(Arguments.of(deferSubscribe, requestN, singleCompletesFirst));
                }
            }
        }
        return args;
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0} requestN={1} singleCompletesFirst={2}")
    @MethodSource("onNextErrorPropagatedParams")
    void onNextErrorPropagated(boolean deferSubscribe, long n, boolean singleCompletesFirst) {
        toSource(source.concat(next, deferSubscribe).<Integer>map(x -> {
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
        assertThat(next.isSubscribed(), is(false));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void bothCompletion(boolean deferSubscribe) {
        setUp(deferSubscribe);
        long requested = triggerNextSubscribe(deferSubscribe);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(2);
        assertThat("Unexpected items requested.", subscription.requested(), is(requested - 1 + 2));
        next.onNext(2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        next.onComplete();
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void sourceCompletionNextError(boolean deferSubscribe) {
        setUp(deferSubscribe);
        triggerNextSubscribe(deferSubscribe);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        next.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0} invalidRequestN={1}")
    @MethodSource("invalidRequestN")
    void invalidRequestNBeforeNextSubscribe(boolean deferSubscribe, long invalidRequestN) {
        setUp(deferSubscribe);
        subscriber.awaitSubscription().request(invalidRequestN);
        source.onSuccess(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void invalidRequestNWithInlineSourceCompletion(boolean deferSubscribe) {
        toSource(succeeded(1).concat(empty(), deferSubscribe)).subscribe(subscriber);
        subscriber.awaitSubscription().request(-1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void invalidRequestAfterNextSubscribe(boolean deferSubscribe) {
        setUp(deferSubscribe);
        triggerNextSubscribe(deferSubscribe);
        subscriber.awaitSubscription().request(-1);
        assertThat("Invalid request-n not propagated.", subscription.requested(), is(lessThan(0L)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void multipleInvalidRequest(boolean deferSubscribe) {
        setUp(deferSubscribe);
        subscriber.awaitSubscription().request(-1);
        subscriber.awaitSubscription().request(-10);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0} invalidRequestN={1}")
    @MethodSource("invalidRequestN")
    void invalidThenValidRequest(boolean deferSubscribe, long invalidRequestN) {
        setUp(deferSubscribe);
        subscriber.awaitSubscription().request(invalidRequestN);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
        assertThat(cancellable.isCancelled(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void request0PropagatedAfterSuccess(boolean deferSubscribe) {
        setUp(deferSubscribe);
        source.onSuccess(1);
        subscriber.awaitSubscription().request(deferSubscribe ? 2 : 1); // get the success from the Single
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        assertThat(subscription.requested(), is(deferSubscribe ? 1L : 0L));
        subscriber.awaitSubscription().request(0);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(0),
                is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void sourceError(boolean deferSubscribe) {
        setUp(deferSubscribe);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat("Unexpected subscriber termination.", subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void cancelSource(boolean deferSubscribe) {
        setUp(deferSubscribe);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertThat("Original single not cancelled.", cancellable.isCancelled(), is(true));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
        if (!deferSubscribe) {
            triggerNextSubscribe(false);
            assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
            assertThat("Next source not cancelled.", cancellable.isCancelled(), is(true));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void cancelSourcePostRequest(boolean deferSubscribe) {
        setUp(deferSubscribe);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
        assertThat("Original single not cancelled.", cancellable.isCancelled(), is(true));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void cancelNext(boolean deferSubscribe) {
        setUp(deferSubscribe);
        triggerNextSubscribe(deferSubscribe);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertThat("Original single cancelled unexpectedly.", cancellable.isCancelled(), is(false));
        assertThat("Next source not cancelled.", subscription.isCancelled(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void zeroIsNotRequestedOnTransitionToSubscription(boolean deferSubscribe) {
        setUp(deferSubscribe);
        subscriber.awaitSubscription().request(1);
        source.onSuccess(1);
        if (deferSubscribe) {
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

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void publisherSubscribeBlockDemandMakesProgress(boolean deferSubscribe) {
        source = new TestSingle<>();
        next = new TestPublisher.Builder<Integer>().build(sub1 -> {
            sub1.onSubscribe(subscription);
            try {
                // Simulate the a blocking operation on demand, like ConnectablePayloadWriter.
                subscription.awaitRequestN(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throwException(e);
            }
            return sub1;
        });
        setUp(deferSubscribe);

        source.onSuccess(10);
        // Give at least 2 demand so there is enough to unblock the awaitRequestN and deliver data below.
        subscriber.awaitSubscription().request(2);

        next.onNext(11);
        next.onComplete();
        assertThat(subscriber.takeOnNext(2), contains(10, 11));
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void onErrorAfterInvalidRequestN(boolean deferSubscribe) {
        setUp(deferSubscribe);
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

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void singleCompletesWithNull(boolean deferSubscribe) {
        setUp(deferSubscribe);
        source.onSuccess(null);
        subscriber.awaitSubscription().request(2);
        assertThat("Next source not subscribed.", next.isSubscribed(), is(true));
        next.onSubscribe(subscription);
        next.onComplete();
        assertThat("Unexpected next element.", subscriber.takeOnNext(), is(nullValue()));
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void demandAccumulatedBeforeSingleCompletes(boolean deferSubscribe) {
        setUp(deferSubscribe);
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

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void requestOneThenMore(boolean deferSubscribe) {
        setUp(deferSubscribe);
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

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void reentryWithMoreDemand(boolean deferSubscribe) {
        List<Integer> emitted = new ArrayList<>();
        boolean[] completed = {false};
        toSource(succeeded(1).concat(from(2), deferSubscribe)).subscribe(new Subscriber<Integer>() {

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

    @ParameterizedTest(name = "{displayName} [{index}] deferSubscribe={0}")
    @ValueSource(booleans = {false, true})
    void cancelledDuringFirstOnNext(boolean deferSubscribe) {
        List<Integer> emitted = new ArrayList<>();
        boolean[] terminated = {false};
        toSource(succeeded(1).concat(never(), deferSubscribe)).subscribe(new Subscriber<Integer>() {

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

    private long triggerNextSubscribe(boolean deferSubscribe) {
        final long n = deferSubscribe ? 2 : 1;
        subscriber.awaitSubscription().request(n);
        source.onSuccess(1);
        next.onSubscribe(subscription);
        return n;
    }
}
