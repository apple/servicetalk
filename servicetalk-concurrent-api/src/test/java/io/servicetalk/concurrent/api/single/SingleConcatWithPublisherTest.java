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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Publisher.empty;
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

    @BeforeEach
    void setUp() {
        subscriber = new TestPublisherSubscriber<>();
        cancellable = new TestCancellable();
        source = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build();
        next = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
        subscription = new TestSubscription();
        toSource(source.concat(next)).subscribe(subscriber);
        source.onSubscribe(cancellable);
        subscriber.awaitSubscription();
    }

    @Test
    void onNextErrorPropagated() {
        subscriber = new TestPublisherSubscriber<>();
        source = new TestSingle<>();
        toSource(source.concat(next).<Integer>map(x -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onSuccess(1);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertThat(next.isSubscribed(), is(false));
    }

    @Test
    void bothCompletion() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(2);
        assertThat("Unexpected items requested.", subscription.requested(), is(2L));
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
        toSource(Single.succeeded(1).concat(empty())).subscribe(subscriber);
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
        subscriber.awaitSubscription().request(1); // get the success from the Single
        next.onSubscribe(subscription);
        subscriber.awaitSubscription().request(0);
        assertThat("Invalid request-n propagated " + subscription, subscription.requestedEquals(0),
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
        next.onSubscribe(subscription);
        assertThat("Invalid request-n propagated " + subscription, subscription.requestedEquals(0),
                is(false));
    }

    private void triggerNextSubscribe() {
        subscriber.awaitSubscription().request(1);
        source.onSuccess(1);
        next.onSubscribe(subscription);
    }
}
