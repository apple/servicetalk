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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletableConcatWithPublisherTest {
    private TestPublisherSubscriber<Integer> subscriber;
    private TestCompletable source;
    private TestPublisher<Integer> next;
    private TestSubscription subscription;
    private TestCancellable cancellable;

    @BeforeEach
    void setUp() {
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
    void bothCompletion() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        assertThat("Unexpected items requested.", subscription.requested(), is(1L));
        next.onNext(1);
        assertThat(subscriber.takeOnNext(), is(1));
        next.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void sourceCompletionNextError() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        next.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void invalidRequestBeforeNextSubscribe() {
        subscriber.awaitSubscription().request(-1);
        triggerNextSubscribe();
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    void invalidRequestAfterNextSubscribe() {
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(-1);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    void multipleInvalidRequest() {
        subscriber.awaitSubscription().request(-1);
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(-10);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    void invalidThenValidRequest() {
        subscriber.awaitSubscription().request(-1);
        subscriber.awaitSubscription().request(10);
        triggerNextSubscribe();
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    void request0Propagated() {
        subscriber.awaitSubscription().request(0);
        triggerNextSubscribe(); // If subscribe happens after request(0) it will be mapped into -1
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    void request0PropagatedAfterComplete() {
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(0);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(0),
                is(true));
    }

    @Test
    void invalidThenValidRequestAcrossNext() {
        subscriber.awaitSubscription().request(-1);
        triggerNextSubscribe();
        subscriber.awaitSubscription().request(10);
        assertThat("Invalid request-n not propagated " + subscription, subscription.requestedEquals(-1),
                is(true));
    }

    @Test
    void sourceError() {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        assertFalse(next.isSubscribed(), "Next source subscribed unexpectedly.");
    }

    @Test
    void cancelSource() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertTrue(cancellable.isCancelled(), "Original completable not cancelled.");
        assertFalse(next.isSubscribed(), "Next source subscribed unexpectedly.");
        triggerNextSubscribe();
        assertTrue(subscription.isCancelled(), "Next source not cancelled.");
    }

    @Test
    void cancelSourcePostRequest() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
        assertTrue(cancellable.isCancelled(), "Original completable not cancelled.");
        assertFalse(next.isSubscribed(), "Next source subscribed unexpectedly.");
    }

    @Test
    void cancelNext() {
        triggerNextSubscribe();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertFalse(cancellable.isCancelled(), "Original completable cancelled unexpectedly.");
        assertTrue(subscription.isCancelled(), "Next source not cancelled.");
    }

    private void triggerNextSubscribe() {
        source.onComplete();
        next.onSubscribe(subscription);
    }
}
