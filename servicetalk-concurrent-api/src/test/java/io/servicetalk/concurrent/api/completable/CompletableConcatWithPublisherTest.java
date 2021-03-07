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
import io.servicetalk.concurrent.internal.TimeoutTracingInfoExtension;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(TimeoutTracingInfoExtension.class)
public class CompletableConcatWithPublisherTest {
    private TestPublisherSubscriber<Integer> subscriber;
    private TestCompletable source;
    private TestPublisher<Integer> next;
    private TestSubscription subscription;
    private TestCancellable cancellable;

    @BeforeEach
    public void setUp() {
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
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
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
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
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
        assertFalse(next.isSubscribed(), "Next source subscribed unexpectedly.");
    }

    @Test
    public void cancelSource() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertTrue(cancellable.isCancelled(), "Original completable not cancelled.");
        assertFalse(next.isSubscribed(), "Next source subscribed unexpectedly.");
        triggerNextSubscribe();
        assertTrue(subscription.isCancelled(), "Next source not cancelled.");
    }

    @Test
    public void cancelSourcePostRequest() {
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
        assertTrue(cancellable.isCancelled(), "Original completable not cancelled.");
        assertFalse(next.isSubscribed(), "Next source subscribed unexpectedly.");
    }

    @Test
    public void cancelNext() {
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
