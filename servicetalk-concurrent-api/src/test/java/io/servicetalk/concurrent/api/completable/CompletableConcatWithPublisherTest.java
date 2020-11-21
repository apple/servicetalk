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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompletableConcatWithPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private TestPublisherSubscriber<Integer> subscriber;
    private TestCompletable source;
    private TestPublisher<Integer> next;
    private TestSubscription subscription;
    private TestCancellable cancellable;

    @Before
    public void setUp() throws Exception {
        subscriber = new TestPublisherSubscriber<>();
        cancellable = new TestCancellable();
        source = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        next = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
        subscription = new TestSubscription();
        toSource(source.concat(next)).subscribe(subscriber);
        source.onSubscribe(cancellable);
        subscriber.awaitSubscription();
    }

    @Test
    public void bothCompletion() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        subscriber.awaitSubscription().request(1);
        assertThat("Unexpected items requested.", subscription.requested(), is(1L));
        next.onNext(1);
        assertThat(subscriber.takeOnNext(), is(1));
        next.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    public void sourceCompletionNextError() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        next.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void invalidRequestBeforeNextSubscribe() {
        subscriber.awaitSubscription().request(-1);
        triggerNextSubscribe();
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    public void invalidRequestAfterNextSubscribe() {
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(-1);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    public void multipleInvalidRequest() {
        subscriber.awaitSubscription().request(-1);
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(-10);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    public void invalidThenValidRequest() {
        subscriber.awaitSubscription().request(-1);
        subscriber.awaitSubscription().request(10);
        triggerNextSubscribe();
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    public void request0Propagated() {
        subscriber.awaitSubscription().request(0);
        triggerNextSubscribe();
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(0),
                is(true));
    }

    @Test
    public void request0PropagatedAfterComplete() {
        source.onComplete();
        subscriber.awaitSubscription().request(0);
        next.onSubscribe(subscription);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(0),
                is(true));
    }

    @Test
    public void invalidThenValidRequestAcrossNext() {
        subscriber.awaitSubscription().request(-1);
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(10);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    public void sourceError() {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertFalse("Next source subscribed unexpectedly.", next.isSubscribed());
    }

    @Test
    public void cancelSource() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        subscriber.awaitSubscription().cancel();
        assertTrue("Original completable not cancelled.", cancellable.isCancelled());
        assertFalse("Next source subscribed unexpectedly.", next.isSubscribed());
        triggerNextSubscribe();
        assertTrue("Next source not cancelled.", subscription.isCancelled());
    }

    @Test
    public void cancelSourcePostRequest() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
        assertTrue("Original completable not cancelled.", cancellable.isCancelled());
        assertFalse("Next source subscribed unexpectedly.", next.isSubscribed());
    }

    @Test
    public void cancelNext() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(false));
        subscriber.awaitSubscription().cancel();
        assertFalse("Original completable cancelled unexpectedly.", cancellable.isCancelled());
        assertTrue("Next source not cancelled.", subscription.isCancelled());
    }

    private void triggerNextSubscribe() {
        source.onComplete();
        next.onSubscribe(subscription);
    }
}
