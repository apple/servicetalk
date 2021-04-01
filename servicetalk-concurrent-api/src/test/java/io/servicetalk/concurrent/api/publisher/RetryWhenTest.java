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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RetryWhenTest {

    private TestPublisher<Integer> source = new TestPublisher<>();
    private TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private BiIntFunction<Throwable, Completable> shouldRetry;
    private LegacyTestCompletable retrySignal;
    private Executor executor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        shouldRetry = (BiIntFunction<Throwable, Completable>) mock(BiIntFunction.class);
        retrySignal = new LegacyTestCompletable();
        when(shouldRetry.apply(anyInt(), any())).thenAnswer(invocation -> {
            retrySignal = new LegacyTestCompletable();
            return retrySignal;
        });
        toSource(source.retryWhen(shouldRetry)).subscribe(subscriber);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void publishOnWithRetry() {
        // This is an indication of whether we are using the same offloader across different subscribes. If this works,
        // then it does not really matter if we reuse offloaders or not. eg: if tomorrow we do not hold up a thread for
        // the lifetime of the Subscriber, we can reuse the offloader.
        executor = newCachedThreadExecutor();
        Publisher<Object> source = Publisher.failed(DELIBERATE_EXCEPTION)
                .publishOn(executor)
                .retryWhen((count, t) ->
                        count == 1 ?
                                // If we complete the returned Completable synchronously, then the offloader will not
                                // terminate before we add another entity in the next subscribe. So, we return an
                                // asynchronously completed Completable.
                                executor.submit(() -> { }) : failed(t));

        Exception e = assertThrows(ExecutionException.class, () -> source.toFuture().get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testComplete() {
        subscriber.awaitSubscription().request(2);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.takeOnNext(2), contains(1, 2));

        verifyZeroInteractions(shouldRetry);
    }

    @Test
    public void testRetryCount() {
        subscriber.awaitSubscription().request(2);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        DeliberateException fatal = new DeliberateException();
        retrySignal.onError(fatal); // stop retry
        assertThat(subscriber.awaitOnError(), sameInstance(fatal));
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(shouldRetry);
    }

    @Test
    public void testRequestAcrossRepeat() {
        subscriber.awaitSubscription().request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        retrySignal.onComplete(); // trigger retry
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        assertTrue(source.isSubscribed());
        source.onNext(3);
        assertThat(subscriber.takeOnNext(), is(3));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    public void testTwoError() {
        subscriber.awaitSubscription().request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        assertTrue(source.isSubscribed());
        source.onNext(3);
        source.onError(DELIBERATE_EXCEPTION);
        verify(shouldRetry).apply(2, DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        source.onComplete();
        assertThat(subscriber.takeOnNext(), is(3));
        subscriber.awaitOnComplete();
    }

    @Test
    public void testMaxRetries() {
        subscriber.awaitSubscription().request(3);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        retrySignal.onComplete(); // trigger retry
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
        assertTrue(source.isSubscribed());
        source.onError(DELIBERATE_EXCEPTION);
        DeliberateException fatal = new DeliberateException();
        retrySignal.verifyListenCalled().onError(fatal); // stop retry
        assertThat(subscriber.awaitOnError(), sameInstance(fatal));
    }

    @Test
    public void testCancelPostErrorButBeforeRetryStart() {
        subscriber.awaitSubscription().request(2);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        retrySignal.verifyListenCalled();
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        subscriber.awaitSubscription().cancel();
        retrySignal.verifyCancelled();
        verify(shouldRetry).apply(1, DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelBeforeRetry() {
        final TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().request(2);
        source.onNext(1, 2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        subscriber.awaitSubscription().cancel();
        source.onComplete();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();
        subscriber = new TestPublisherSubscriber<>();
        source = new TestPublisher<>();
        toSource(source.retryWhen((times, cause) -> {
            throw ex;
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(ex));
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }

    @Test
    public void nullInTerminalCallsOnError() {
        source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
        toSource(source.retryWhen((times1, cause1) -> null)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), instanceOf(NullPointerException.class));
    }
}
