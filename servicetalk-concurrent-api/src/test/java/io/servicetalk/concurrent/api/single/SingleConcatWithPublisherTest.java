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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCollectingPublisherSubscriber;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.sameInstance;

public class SingleConcatWithPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private TestCollectingPublisherSubscriber<Integer> subscriber;
    private TestSingle<Integer> source;
    private TestPublisher<Integer> next;
    private TestSubscription subscription;
    private TestCancellable cancellable;

    @Before
    public void setUp() throws Exception {
        subscriber = new TestCollectingPublisherSubscriber<>();
        cancellable = new TestCancellable();
        source = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build();
        next = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
        subscription = new TestSubscription();
        toSource(source.concat(next)).subscribe(subscriber);
        source.onSubscribe(cancellable);
        subscriber.awaitSubscription();
    }

    @Test
    public void bothCompletion() throws InterruptedException {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        subscriber.awaitSubscription().request(2);
        assertThat("Unexpected items requested.", subscription.requested(), is(2L));
        next.onNext(2);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.takeOnNext(), is(2));
        next.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void sourceCompletionNextError() throws InterruptedException {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        next.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void invalidRequestBeforeNextSubscribeNegative1() throws InterruptedException {
        invalidRequestBeforeNextSubscribe(-1);
    }

    @Test
    public void invalidRequestBeforeNextSubscribeZero() throws InterruptedException {
        invalidRequestBeforeNextSubscribe(0);
    }

    private void invalidRequestBeforeNextSubscribe(long invalidN) throws InterruptedException {
        subscriber.awaitSubscription().request(invalidN);
        source.onSuccess(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void invalidRequestNWithInlineSourceCompletion() throws InterruptedException {
        TestCollectingPublisherSubscriber<Integer> subscriber = new TestCollectingPublisherSubscriber<>();
        toSource(Single.succeeded(1).concat(empty())).subscribe(subscriber);
        subscriber.awaitSubscription().request(-1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void invalidRequestAfterNextSubscribe() throws InterruptedException {
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(-1);
        assertThat("Invalid request-n not propagated.", subscription.requested(), is(lessThan(0L)));
    }

    @Test
    public void multipleInvalidRequest() throws InterruptedException {
        subscriber.awaitSubscription().request(-1);
        subscriber.awaitSubscription().request(-10);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void invalidThenValidRequestNegative1() throws InterruptedException {
        invalidThenValidRequest(-1);
    }

    @Test
    public void invalidThenValidRequestZero() throws InterruptedException {
        invalidThenValidRequest(0);
    }

    private void invalidThenValidRequest(long invalidN) throws InterruptedException {
        subscriber.awaitSubscription().request(invalidN);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
        assertThat(cancellable.isCancelled(), is(true));
    }

    @Test
    public void request0PropagatedAfterSuccess() throws InterruptedException {
        source.onSuccess(1);
        subscriber.awaitSubscription().request(1); // get the success from the Single
        subscriber.awaitSubscription().request(0);
        next.onSubscribe(subscription);
        assertThat("Invalid request-n propagated " + subscription, subscription.requestedEquals(0),
                is(true));
    }

    @Test
    public void sourceError() throws InterruptedException {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat("Unexpected subscriber termination.", subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
    }

    @Test
    public void cancelSource() throws InterruptedException {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        subscriber.awaitSubscription().cancel();
        assertThat("Original single not cancelled.", cancellable.isCancelled(), is(true));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
    }

    @Test
    public void cancelSourcePostRequest() throws InterruptedException {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
        assertThat("Original single not cancelled.", cancellable.isCancelled(), is(true));
        assertThat("Next source subscribed unexpectedly.", next.isSubscribed(), is(false));
    }

    @Test
    public void cancelNext() throws InterruptedException {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        subscriber.awaitSubscription().cancel();
        assertThat("Original single cancelled unexpectedly.", cancellable.isCancelled(), is(false));
        assertThat("Next source not cancelled.", subscription.isCancelled(), is(true));
    }

    @Test
    public void zeroIsNotRequestedOnTransitionToSubscription() throws InterruptedException {
        subscriber.awaitSubscription().request(1);
        source.onSuccess(1);
        next.onSubscribe(subscription);
        assertThat("Invalid request-n propagated " + subscription, subscription.requestedEquals(0),
                is(false));
    }

    private void triggerNextSubscribe() throws InterruptedException {
        subscriber.awaitSubscription().request(1);
        source.onSuccess(1);
        next.onSubscribe(subscription);
    }
}
