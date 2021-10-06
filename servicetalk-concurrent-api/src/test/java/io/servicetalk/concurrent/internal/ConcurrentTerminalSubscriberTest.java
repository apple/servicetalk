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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConcurrentTerminalSubscriberTest {
    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor();

    private final TestPublisher<Integer> publisher =
            new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    void concurrentOnSubscribeWithOnNextAndOnComplete() throws Exception {
        concurrentOnSubscribe(true, true);
    }

    @Test
    void concurrentOnSubscribeWithOnComplete() throws Exception {
        concurrentOnSubscribe(true, false);
    }

    @Test
    void concurrentOnSubscribeWithOnNextAndOnError() throws Exception {
        concurrentOnSubscribe(false, true);
    }

    @Test
    void concurrentOnSubscribeWithOnError() throws Exception {
        concurrentOnSubscribe(false, false);
    }

    @Test
    void concurrentOnNextWithOnComplete() throws Exception {
        concurrentOnNext(true);
    }

    @Test
    void concurrentOnNextWithOnError() throws Exception {
        concurrentOnNext(false);
    }

    @Test
    void concurrentOnCompleteWithOnComplete() throws Exception {
        concurrentOnComplete(true, true);
    }

    @Test
    void concurrentOnCompleteWithOnError() throws Exception {
        concurrentOnComplete(true, false);
    }

    @Test
    void concurrentOnErrorWithOnComplete() throws Exception {
        concurrentOnComplete(false, true);
    }

    @Test
    void concurrentOnErrorWithOnError() throws Exception {
        concurrentOnComplete(false, false);
    }

    @Test
    void reentrySynchronousOnNextAllowedOnComplete() {
        reentrySynchronousOnNextAllowed(true);
    }

    @Test
    void reentrySynchronousOnNextAllowedOnError() {
        reentrySynchronousOnNextAllowed(false);
    }

    private void reentrySynchronousOnNextAllowed(boolean onComplete) {
        AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        Subscriber<Integer> mockSubscriber = (Subscriber<Integer>) mock(Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            subscriptionRef.set(s);
            s.request(1);
            return null;
        }).when(mockSubscriber).onSubscribe(any());
        doAnswer((Answer<Void>) invocation -> {
            subscriptionRef.get().request(1);
            return null;
        }).when(mockSubscriber).onNext(any());

        ConcurrentTerminalSubscriber<Integer> subscriber = new ConcurrentTerminalSubscriber<>(mockSubscriber);
        publisher.subscribe(subscriber);
        publisher.onSubscribe(new Subscription() {
            private int i;
            @Override
            public void request(final long n) {
                if (i < 5) {
                    publisher.onNext(++i);
                } else if (i == 5) {
                    ++i;
                    if (onComplete) {
                        publisher.onComplete();
                    } else {
                        publisher.onError(DELIBERATE_EXCEPTION);
                    }
                    // This should be filtered, because we need to protect against concurrent termination this may
                    // happen concurrently.
                    publisher.onNext(i);
                }
            }

            @Override
            public void cancel() {
            }
        });

        ArgumentCaptor<Integer> onNextArgs = ArgumentCaptor.forClass(Integer.class);
        verify(mockSubscriber, times(5)).onNext(onNextArgs.capture());
        assertEquals(asList(1, 2, 3, 4, 5), onNextArgs.getAllValues());
        if (onComplete) {
            verify(mockSubscriber).onComplete();
        } else {
            verify(mockSubscriber).onError(same(DELIBERATE_EXCEPTION));
        }
    }

    private void concurrentOnComplete(boolean firstOnComplete, boolean secondOnComplete) throws Exception {
        CyclicBarrier terminalEnterBarrier = new CyclicBarrier(2);
        CountDownLatch terminatedLatch = new CountDownLatch(1);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> mockSubscriber = (Subscriber<Integer>) mock(Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(1);
            return null;
        }).when(mockSubscriber).onSubscribe(any());
        doAnswer((Answer<Void>) invocation -> {
            terminalEnterBarrier.await();
            terminatedLatch.countDown();
            return null;
        }).when(mockSubscriber).onComplete();
        doAnswer((Answer<Void>) invocation -> {
            terminalEnterBarrier.await();
            terminatedLatch.countDown();
            return null;
        }).when(mockSubscriber).onError(any());

        ConcurrentTerminalSubscriber<Integer> subscriber = new ConcurrentTerminalSubscriber<>(mockSubscriber);
        publisher.subscribe(subscriber);
        publisher.onSubscribe(subscription);
        executorExtension.executor().execute(() -> {
            if (firstOnComplete) {
                publisher.onComplete();
            } else {
                publisher.onError(DELIBERATE_EXCEPTION);
            }
        });
        terminalEnterBarrier.await();
        if (secondOnComplete) {
            publisher.onComplete();
        } else {
            publisher.onError(DELIBERATE_EXCEPTION);
        }

        terminatedLatch.await();

        if (firstOnComplete && secondOnComplete) {
            verify(mockSubscriber).onComplete();
        } else if (!firstOnComplete && !secondOnComplete) {
            verify(mockSubscriber).onError(same(DELIBERATE_EXCEPTION));
        } else {
            try {
                verify(mockSubscriber).onComplete();
            } catch (AssertionError e) {
                verify(mockSubscriber).onError(same(DELIBERATE_EXCEPTION));
            }
        }
    }

    private void concurrentOnNext(boolean onComplete) throws Exception {
        CountDownLatch onNextLatch = new CountDownLatch(1);
        CyclicBarrier onNextEnterBarrier = new CyclicBarrier(2);
        CountDownLatch terminatedLatch = new CountDownLatch(1);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> mockSubscriber = (Subscriber<Integer>) mock(Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(1);
            return null;
        }).when(mockSubscriber).onSubscribe(any());
        doAnswer((Answer<Void>) invocation -> {
            try {
                onNextEnterBarrier.await();
                onNextLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return null;
        }).when(mockSubscriber).onNext(any());
        doAnswer((Answer<Void>) invocation -> {
            terminatedLatch.countDown();
            return null;
        }).when(mockSubscriber).onComplete();
        doAnswer((Answer<Void>) invocation -> {
            terminatedLatch.countDown();
            return null;
        }).when(mockSubscriber).onError(any());

        ConcurrentTerminalSubscriber<Integer> subscriber = new ConcurrentTerminalSubscriber<>(mockSubscriber);
        publisher.subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscription.awaitRequestN(1);
        executorExtension.executor().execute(() -> publisher.onNext(1));
        onNextEnterBarrier.await();
        if (onComplete) {
            publisher.onComplete();
        } else {
            publisher.onError(DELIBERATE_EXCEPTION);
        }
        onNextLatch.countDown();

        terminatedLatch.await();
        verify(mockSubscriber).onNext(eq(1));

        if (onComplete) {
            verify(mockSubscriber).onComplete();
        } else {
            verify(mockSubscriber).onError(same(DELIBERATE_EXCEPTION));
        }
    }

    private void concurrentOnSubscribe(boolean onComplete, boolean onNext) throws Exception {
        CountDownLatch onSubscribeLatch = new CountDownLatch(1);
        CountDownLatch terminatedLatch = new CountDownLatch(1);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> mockSubscriber = (Subscriber<Integer>) mock(Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            Subscription s = invocation.getArgument(0);
            s.request(1);
            try {
                onSubscribeLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return null;
        }).when(mockSubscriber).onSubscribe(any());
        doAnswer((Answer<Void>) invocation -> {
            terminatedLatch.countDown();
            return null;
        }).when(mockSubscriber).onComplete();
        doAnswer((Answer<Void>) invocation -> {
            terminatedLatch.countDown();
            return null;
        }).when(mockSubscriber).onError(any());

        ConcurrentTerminalSubscriber<Integer> subscriber = new ConcurrentTerminalSubscriber<>(mockSubscriber);
        publisher.subscribe(subscriber);
        executorExtension.executor().execute(() -> publisher.onSubscribe(subscription));
        subscription.awaitRequestN(1);
        if (onNext) {
            publisher.onNext(1);
        }
        if (onComplete) {
            publisher.onComplete();
        } else {
            publisher.onError(DELIBERATE_EXCEPTION);
        }
        onSubscribeLatch.countDown();

        terminatedLatch.await();
        if (onNext) {
            verify(mockSubscriber).onNext(eq(1));
        } else {
            verify(mockSubscriber, never()).onNext(any());
        }

        if (onComplete) {
            verify(mockSubscriber).onComplete();
        } else {
            verify(mockSubscriber).onError(same(DELIBERATE_EXCEPTION));
        }
    }
}
