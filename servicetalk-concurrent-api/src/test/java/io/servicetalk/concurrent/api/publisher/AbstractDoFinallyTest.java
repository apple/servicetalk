/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSubscription;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.TestPublisherSubscriber.newTestPublisherSubscriber;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoFinallyTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    final TestPublisher<String> publisher = new TestPublisher<>();
    final TestPublisherSubscriber<String> subscriber = newTestPublisherSubscriber();
    private Runnable doFinally;
    final TestSubscription subscription = new TestSubscription();

    @Before
    public void setUp() throws Exception {
        doFinally = mock(Runnable.class);
    }

    @Test
    public void testForCancelPostEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.request(1);
        publisher.onNext("Hello");
        assertThat(subscriber.items(), contains("Hello"));
        subscriber.cancel();
        verify(doFinally).run();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testForCancelNoEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.cancel();
        verify(doFinally).run();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testForCancelPostError() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        publisher.onError(DELIBERATE_EXCEPTION);
        subscriber.cancel();
        verify(doFinally).run();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testForCancelPostComplete() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        assertFalse(subscription.isCancelled());
        publisher.onComplete();
        subscriber.cancel();
        verify(doFinally).run();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void testForCompletePostEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.request(1);
        publisher.onNext("Hello");
        assertFalse(subscription.isCancelled());
        publisher.onComplete();
        assertThat(subscriber.items(), contains("Hello"));
        assertTrue(subscriber.isCompleted());
        verify(doFinally).run();
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void testForCompleteNoEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.request(1);
        assertFalse(subscription.isCancelled());
        publisher.onComplete();
        assertTrue(subscriber.isCompleted());
        verify(doFinally).run();
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void testForErrorPostEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.request(1);
        publisher.onNext("Hello");
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains("Hello"));
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
        verify(doFinally).run();
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void testForErrorNoEmissions() {
        doFinally(publisher, doFinally).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.request(1);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
        verify(doFinally).run();
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void testCallbackThrowsErrorOnCancel() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));
        AtomicInteger invocationCount = new AtomicInteger();
        try {
            doFinally(publisher, () -> {
                invocationCount.incrementAndGet();
                throw DELIBERATE_EXCEPTION;
            }).subscribe(subscriber);
            publisher.onSubscribe(subscription);
            subscriber.cancel();
        } finally {
            assertThat("Unexpected calls to doFinally callback.", invocationCount.get(), is(1));

            assertTrue(subscription.isCancelled());
        }
    }

    @Test
    public abstract void testCallbackThrowsErrorOnComplete();

    @Test
    public abstract void testCallbackThrowsErrorOnError();

    protected abstract <T> PublisherSource<T> doFinally(Publisher<T> publisher, Runnable runnable);
}
