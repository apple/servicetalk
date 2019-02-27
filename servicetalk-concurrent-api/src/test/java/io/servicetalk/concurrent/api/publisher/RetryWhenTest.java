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

import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.TestPublisherSubscriber.newTestPublisherSubscriber;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

public class RetryWhenTest {

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    private TestPublisher<Integer> source = new TestPublisher<>();
    private TestPublisherSubscriber<Integer> subscriber = newTestPublisherSubscriber();
    private BiIntFunction<Throwable, Completable> shouldRetry;
    private TestCompletable retrySignal;
    private Executor executor;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        shouldRetry = (BiIntFunction<Throwable, Completable>) mock(BiIntFunction.class);
        retrySignal = new TestCompletable();
        when(shouldRetry.apply(anyInt(), any())).thenAnswer(invocation -> {
            retrySignal = new TestCompletable();
            return retrySignal;
        });
        toSource(source.retryWhen(shouldRetry)).subscribe(subscriber);
    }

    @After
    public void tearDown() throws Exception {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void publishOnWithRetry() throws Exception {
        // This is an indication of whether we are using the same offloader across different subscribes. If this works,
        // then it does not really matter if we reuse offloaders or not. eg: if tomorrow we do not hold up a thread for
        // the lifetime of the Subscriber, we can reuse the offloader.
        executor = newCachedThreadExecutor();
        Publisher<Object> source = Publisher.error(DELIBERATE_EXCEPTION)
                .publishOn(executor)
                .retryWhen((count, t) ->
                        count == 1 ?
                                // If we complete the returned Completable synchronously, then the offloader will not
                                // terminate before we add another entity in the next subscribe. So, we return an
                                // asynchronously completed Completable.
                                executor.submit(() -> { }) : error(t));
        expectedException.expect(instanceOf(ExecutionException.class));
        expectedException.expectCause(is(DELIBERATE_EXCEPTION));
        source.toFuture().get();
    }

    @Test
    public void testComplete() {
        subscriber.request(2);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2));
        assertTrue(subscriber.isCompleted());
        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void testRetryCount() {
        subscriber.request(2);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains(1, 2));
        DeliberateException fatal = new DeliberateException();
        retrySignal.onError(fatal); // stop retry
        assertThat(subscriber.error(), sameInstance(fatal));
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(shouldRetry);
    }

    @Test
    public void testRequestAcrossRepeat() {
        subscriber.request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains(1, 2));
        retrySignal.onComplete(); // trigger retry
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        assertTrue(source.isSubscribed());
        source.onNext(3);
        assertThat(subscriber.items(), contains(1, 2, 3));
        assertTrue(subscriber.subscriptionReceived());
        assertFalse(subscriber.isTerminated());
    }

    @Test
    public void testTwoError() {
        subscriber.request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains(1, 2));
        assertTrue(subscriber.subscriptionReceived());
        assertFalse(subscriber.isTerminated());
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        assertTrue(source.isSubscribed());
        source.onNext(3);
        source.onError(DELIBERATE_EXCEPTION);
        verify(shouldRetry).apply(2, DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2, 3));
        assertTrue(subscriber.isCompleted());
    }

    @Test
    public void testMaxRetries() {
        subscriber.request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        assertThat(subscriber.items(), contains(1, 2));
        assertTrue(subscriber.subscriptionReceived());
        assertFalse(subscriber.isTerminated());
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        assertTrue(source.isSubscribed());
        source.onError(DELIBERATE_EXCEPTION);
        DeliberateException fatal = new DeliberateException();
        retrySignal.verifyListenCalled().onError(fatal); // stop retry
        assertThat(subscriber.error(), sameInstance(fatal));
    }

    @Test
    public void testCancelPostErrorButBeforeRetryStart() {
        subscriber.request(2);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        retrySignal.verifyListenCalled();
        assertThat(subscriber.items(), contains(1, 2));
        subscriber.cancel();
        retrySignal.verifyCancelled();
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelBeforeRetry() {
        final TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        subscriber.request(2);
        source.onNext(1, 2);
        assertThat(subscriber.items(), contains(1, 2));
        subscriber.cancel();
        source.onComplete();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();
        subscriber = newTestPublisherSubscriber();
        source = new TestPublisher<>();
        toSource(source.retryWhen((times, cause) -> {
            throw ex;
        })).subscribe(subscriber);
        subscriber.request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.error(), sameInstance(ex));
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }

    @Test
    public void nullInTerminalCallsOnError() {
        source = new TestPublisher<>();
        toSource(source.retryWhen((times1, cause1) -> null)).subscribe(subscriber);
        subscriber.request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.error(), instanceOf(NullPointerException.class));
    }
}
