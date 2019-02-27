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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.SequentialPublisherSubscriberFunction;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSubscription;

import org.junit.After;
import org.junit.Test;

import java.util.Collection;
import java.util.function.IntFunction;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.TestPublisherSubscriber.newTestPublisherSubscriber;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RepeatWhenTest {

    private final TestPublisherSubscriber<Integer> subscriber = newTestPublisherSubscriber();
    private TestPublisher<Integer> source;
    private IntFunction<Completable> shouldRepeat;
    private TestCompletable repeatSignal;
    private Executor executor;

    @After
    public void tearDown() throws Exception {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void publishOnWithRepeat() throws Exception {
        // This is an indication of whether we are using the same offloader across different subscribes. If this works,
        // then it does not really matter if we reuse offloaders or not. eg: if tomorrow we do not hold up a thread for
        // the lifetime of the Subscriber, we can reuse the offloader.
        executor = newCachedThreadExecutor();
        Collection<Integer> result = just(1).publishOn(executor).repeatWhen(count -> count == 1 ?
                // If we complete the returned Completable synchronously, then the offloader will not terminate before
                // we add another entity in the next subscribe. So, we return an asynchronously completed Completable.
                executor.submit(() -> { }) : error(DELIBERATE_EXCEPTION)).toFuture().get();
        assertThat("Unexpected items received.", result, hasSize(2));
    }

    @Test
    public void testError() {
        init();
        subscriber.request(2);
        source.onNext(1, 2);
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains(1, 2));
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
        verifyZeroInteractions(shouldRepeat);
    }

    @Test
    public void testRepeatCount() {
        init();
        subscriber.request(2);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2));
        repeatSignal.onError(DELIBERATE_EXCEPTION); // stop repeat
        assertTrue(subscriber.isCompleted());
        verify(shouldRepeat).apply(1);
    }

    @Test
    public void testRequestAcrossRepeat() {
        init();
        subscriber.request(3);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2));
        repeatSignal.onComplete(); // trigger repeat
        verify(shouldRepeat).apply(1);
        assertTrue(source.isSubscribed());
        source.onNext(3);
        assertThat(subscriber.items(), contains(1, 2, 3));
        assertFalse(subscriber.isTerminated());
    }

    @Test
    public void testTwoCompletes() {
        init();
        subscriber.request(3);
        source.onNext(1, 2);
        source.onComplete();
        assertThat(subscriber.items(), contains(1, 2));
        assertFalse(subscriber.isTerminated());
        verify(shouldRepeat).apply(1);
        repeatSignal.onComplete(); // trigger repeat
        assertTrue(source.isSubscribed());
        source.onNext(3);
        source.onComplete();
        verify(shouldRepeat).apply(2);
        repeatSignal.onComplete(); // trigger repeat
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.items(), contains(1, 2, 3));
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testMaxRepeats() {
        init();
        subscriber.request(3);
        source.onNext(1, 2);
        source.onComplete();
        repeatSignal.onComplete(); // trigger repeat
        assertThat(subscriber.items(), contains(1, 2));
        assertFalse(subscriber.isTerminated());
        verify(shouldRepeat).apply(1);
        assertTrue(source.isSubscribed());
        source.onComplete();
        repeatSignal.verifyListenCalled().onError(DELIBERATE_EXCEPTION); // stop repeat
        assertTrue(subscriber.isCompleted());
    }

    @Test
    public void testCancelPostCompleteButBeforeRetryStart() {
        SequentialPublisherSubscriberFunction<Integer> sequentialPublisherSubscriberFunction =
                new SequentialPublisherSubscriberFunction<>();
        init(new TestPublisher.Builder<Integer>()
                .sequentialSubscribers(sequentialPublisherSubscriberFunction)
                .build());
        subscriber.request(2);
        source.onNext(1, 2);
        source.onComplete();
        repeatSignal.verifyListenCalled();
        assertThat(subscriber.items(), contains(1, 2));
        subscriber.cancel();
        repeatSignal.verifyCancelled();
        assertThat(sequentialPublisherSubscriberFunction.subscriptionCount(), is(1));
        verify(shouldRepeat).apply(1);
    }

    @Test
    public void testCancelBeforeRetry() {
        init();
        final TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        subscriber.request(2);
        source.onNext(1, 2);
        assertThat(subscriber.items(), contains(1, 2));
        subscriber.cancel();
        source.onComplete();
        assertTrue(subscription.isCancelled());
    }

    private void init() {
        init(new TestPublisher<>());
    }

    @SuppressWarnings("unchecked")
    private void init(TestPublisher<Integer> source) {
        this.source = source;
        shouldRepeat = (IntFunction<Completable>) mock(IntFunction.class);
        repeatSignal = new TestCompletable();
        when(shouldRepeat.apply(anyInt())).thenAnswer(invocation -> {
            repeatSignal = new TestCompletable();
            return repeatSignal;
        });
        toSource(source.repeatWhen(shouldRepeat)).subscribe(subscriber);
    }
}
