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

import io.servicetalk.concurrent.api.BiIntPredicate;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Before;
import org.junit.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RetryTest {

    private TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private TestPublisher<Integer> source;
    private BiIntPredicate<Throwable> shouldRetry;
    private boolean shouldRetryValue;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        source = new TestPublisher<>();
        shouldRetry = (BiIntPredicate<Throwable>) mock(BiIntPredicate.class);
        when(shouldRetry.test(anyInt(), any())).thenAnswer(invocation -> shouldRetryValue);
        toSource(source.retry(shouldRetry)).subscribe(subscriber);
    }

    @Test
    public void testComplete() {
        subscriber.request(2);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(1, 2));
        assertThat(subscriber.takeTerminal(), is(complete()));
        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void testRetryCount() {
        subscriber.request(2);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains(1, 2));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(shouldRetry);
    }

    @Test
    public void testRequestAcrossRetry() {
        shouldRetryValue = true;
        subscriber.request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains(1, 2));
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        assertTrue(source.isSubscribed());
        source.onNext(3);
        assertThat(subscriber.takeItems(), contains(3));
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testTwoFailures() {
        shouldRetryValue = true;
        subscriber.request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains(1, 2));
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeTerminal(), nullValue());
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        assertTrue(source.isSubscribed());
        source.onNext(3);
        source.onError(DELIBERATE_EXCEPTION);
        verify(shouldRetry).test(2, DELIBERATE_EXCEPTION);
        source.onComplete();
        assertThat(subscriber.takeItems(), contains(3));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testMaxRetries() {
        shouldRetryValue = true;
        subscriber.request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeItems(), contains(1, 2));
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeTerminal(), nullValue());
        verify(shouldRetry).test(1, DELIBERATE_EXCEPTION);
        shouldRetryValue = false;
        DeliberateException fatal = new DeliberateException();
        source.onError(fatal);
        assertThat(subscriber.takeError(), sameInstance(fatal));
    }

    @Test
    public void testCancel() {
        final TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        subscriber.request(2);
        source.onNext(1, 2);
        assertThat(subscriber.takeItems(), contains(1, 2));
        subscriber.cancel();
        source.onError(DELIBERATE_EXCEPTION);
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();
        subscriber = new TestPublisherSubscriber<>();
        source = new TestPublisher<>();
        toSource(source.retry((times, cause) -> {
            throw ex;
        })).subscribe(subscriber);
        subscriber.request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeError(), sameInstance(ex));
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }
}
