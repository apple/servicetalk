/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OnErrorResumePublisherTest {

    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private TestPublisher<Integer> first = new TestPublisher<>();
    private TestPublisher<Integer> second = new TestPublisher<>();

    @Test
    void testFirstComplete() {
        toSource(first.onErrorResume(throwable -> second)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        first.onNext(1);
        first.onComplete();
        assertThat(subscriber.takeOnNext(), is(1));
        subscriber.awaitOnComplete();
    }

    @Test
    void testFirstErrorSecondComplete() {
        toSource(first.onErrorResume(throwable -> second)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        second.onNext(1);
        second.onComplete();
        assertThat(subscriber.takeOnNext(), is(1));
        subscriber.awaitOnComplete();
    }

    @Test
    void testFirstErrorSecondError() {
        toSource(first.onErrorResume(throwable -> second)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        first.onError(new DeliberateException());
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        second.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void testCancelFirstActive() {
        toSource(first.onErrorResume(throwable -> second)).subscribe(subscriber);
        final TestSubscription subscription = new TestSubscription();
        first.onSubscribe(subscription);
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void testCancelSecondActive() {
        toSource(first.onErrorResume(throwable -> second)).subscribe(subscriber);
        final TestSubscription subscription = new TestSubscription();
        subscriber.awaitSubscription().request(1);
        first.onError(DELIBERATE_EXCEPTION);
        second.onSubscribe(subscription);
        assertTrue(second.isSubscribed());
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
    }

    @Test
    void testDemandAcrossPublishers() {
        toSource(first.onErrorResume(throwable -> second)).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        first.onNext(1);
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(), is(1));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        second.onNext(2);
        second.onComplete();
        assertThat(subscriber.takeOnNext(), is(2));
        subscriber.awaitOnComplete();
    }

    @Test
    void testDuplicateOnError() {
        toSource(first.onErrorResume(throwable -> second)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        second.onNext(1);
        second.onComplete();
        assertThat(subscriber.takeOnNext(), is(1));
        subscriber.awaitOnComplete();
    }

    @Test
    void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();
        toSource(first.onErrorResume(throwable -> {
            throw ex;
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(ex));
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }

    @Test
    void nullInTerminalCallsOnError() {
        toSource(first.onErrorResume(throwable -> null)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), instanceOf(NullPointerException.class));
    }
}
