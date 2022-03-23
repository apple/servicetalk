/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CacheSingleTest {
    private TestSingle<Integer> source;
    private TestSingleSubscriber<Integer> subscriber1;
    private TestSingleSubscriber<Integer> subscriber2;
    private TestSingleSubscriber<Integer> subscriber3;
    private TestCancellable cancellable;

    @BeforeEach
    void setUp() {
        cancellable = new TestCancellable();
        source = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build(subscriber -> {
            subscriber.onSubscribe(cancellable);
            return subscriber;
        });
        subscriber1 = new TestSingleSubscriber<>();
        subscriber2 = new TestSingleSubscriber<>();
        subscriber3 = new TestSingleSubscriber<>();
    }

    @ParameterizedTest
    @CsvSource(value = {"true,", "false,1", "false,"})
    void singleSubscriber(boolean onError, @Nullable Integer value) {
        toSource(source.cache(1)).subscribe(subscriber1);
        subscriber1.awaitSubscription();
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onSuccess(value);
            assertThat(subscriber1.awaitOnSuccess(), is(value));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void singleSubscriberCancel(boolean cancelUpstream) throws InterruptedException {
        toSource(source.cache(1, cancelUpstream)).subscribe(subscriber1);
        Cancellable subscription1 = subscriber1.awaitSubscription();
        subscription1.cancel();
        subscription1.cancel(); // multiple cancels should be safe.
        if (cancelUpstream) {
            cancellable.awaitCancelled();
        } else {
            assertThat(cancellable.isCancelled(), is(false));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void twoSubscribersOneCancelsMultipleTimes(boolean cancelUpstream) throws InterruptedException {
        Single<Integer> single = source.cache(2, cancelUpstream);
        toSource(single).subscribe(subscriber1);
        toSource(single).subscribe(subscriber2);
        Cancellable subscription1 = subscriber1.awaitSubscription();
        Cancellable subscription2 = subscriber2.awaitSubscription();
        subscription1.cancel();
        subscription1.cancel(); // multiple cancels should be safe.
        subscription2.cancel();
        subscription2.cancel(); // multiple cancels should be safe.
        if (cancelUpstream) {
            cancellable.awaitCancelled();
        } else {
            assertThat(cancellable.isCancelled(), is(false));
        }
    }

    @Test
    void singleSubscriberCancelStillDeliversData() {
        Single<Integer> single = source.cache(1, false);
        toSource(single).subscribe(subscriber1);
        Cancellable subscription1 = subscriber1.awaitSubscription();
        subscription1.cancel();
        assertThat(cancellable.isCancelled(), is(false));

        toSource(single).subscribe(subscriber2);
        Cancellable subscription2 = subscriber2.awaitSubscription();

        source.onSuccess(1); // first subscriber triggered upstream subscribe, we can deliver and signal must be queued.
        assertThat(subscriber2.awaitOnSuccess(), is(1));

        subscription2.cancel();
        assertThat(cancellable.isCancelled(), is(false));
    }

    @ParameterizedTest
    @CsvSource(value = {"true,", "false,1", "false,"})
    void twoSubscribersNoData(boolean onError, @Nullable Integer value) {
        Single<Integer> single = source.cache(2);
        toSource(single).subscribe(subscriber1);
        subscriber1.awaitSubscription();
        toSource(single).subscribe(subscriber2);
        subscriber2.awaitSubscription();

        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onSuccess(value);
            assertThat(subscriber1.awaitOnSuccess(), is(value));
            assertThat(subscriber2.awaitOnSuccess(), is(value));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"true,", "false,1", "false,"})
    void twoSubscribersAfterTerminalData(boolean onError, @Nullable Integer value) {
        Single<Integer> single = source.cache(1, true, (v, t) -> never());
        toSource(single).subscribe(subscriber1);
        subscriber1.awaitSubscription();
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onSuccess(value);
            assertThat(subscriber1.awaitOnSuccess(), is(value));
        }
        toSource(single).subscribe(subscriber2);
        subscriber2.awaitSubscription();
        if (onError) {
            assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            assertThat(subscriber2.awaitOnSuccess(), is(value));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void twoSubscribersCancel(boolean firstSubscription, boolean cancelUpstream) {
        Single<Integer> single = source.cache(2, cancelUpstream);
        toSource(single).subscribe(subscriber1);
        Cancellable subscription1 = subscriber1.awaitSubscription();
        toSource(single).subscribe(subscriber2);
        Cancellable subscription2 = subscriber2.awaitSubscription();
        if (firstSubscription) {
            subscription1.cancel();
        } else {
            subscription2.cancel();
        }

        // Add another subscriber after one of the subscribers has cancelled.
        toSource(single).subscribe(subscriber3);
        Cancellable subscription3 = subscriber3.awaitSubscription();
        source.onSuccess(1);

        // subscriber3 has no demand yet, it shouldn't have any data delivered.
        assertThat(subscriber3.awaitOnSuccess(), is(1));

        if (firstSubscription) {
            assertThat(subscriber1.pollTerminal(10, MILLISECONDS), is(nullValue()));
            assertThat(subscriber2.awaitOnSuccess(), is(1));
        } else {
            assertThat(subscriber1.awaitOnSuccess(), is(1));
            assertThat(subscriber2.pollTerminal(10, MILLISECONDS), is(nullValue()));
        }

        subscription3.cancel(); // cancel after terminal shouldn't impact anything.
    }

    @ParameterizedTest
    @CsvSource(value = {"true,", "false,1", "false,"})
    void threeSubscribersOneLateQueueData(boolean onError, @Nullable Integer value) {
        Single<Integer> single = source.cache(2);
        toSource(single).subscribe(subscriber1);
        toSource(single).subscribe(subscriber2);
        Cancellable localSubscription1 = subscriber1.awaitSubscription();
        Cancellable localSubscription2 = subscriber2.awaitSubscription();

        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onSuccess(value);
            assertThat(subscriber1.awaitOnSuccess(), is(value));
            assertThat(subscriber2.awaitOnSuccess(), is(value));
        }

        toSource(single).subscribe(subscriber3);
        Cancellable localSubscription3 = subscriber3.awaitSubscription();

        if (onError) {
            assertThat(subscriber3.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            assertThat(subscriber3.awaitOnSuccess(), is(value));
        }

        // cancel after terminal shouldn't impact anything.
        localSubscription1.cancel();
        localSubscription2.cancel();
        localSubscription3.cancel();
    }

    @ParameterizedTest
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void terminalResubscribe(boolean onError, boolean cacheData) {
        final AtomicBoolean subscribed = new AtomicBoolean();
        final Integer value = 1;
        final Integer value2 = 2;
        Single<Integer> single = Single.defer(() -> {
            if (subscribed.compareAndSet(false, true)) {
                return onError ? Single.failed(DELIBERATE_EXCEPTION) : Single.succeeded(value);
            } else {
                return Single.succeeded(value2);
            }
        }).cache(1, true, cacheData ? (v, t) -> never() : (v, t) -> completed());
        toSource(single).subscribe(subscriber1);
        subscriber1.awaitSubscription();
        if (onError) {
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            assertThat(subscriber1.awaitOnSuccess(), is(value));
        }

        toSource(single).subscribe(subscriber2);
        subscriber2.awaitSubscription();
        if (cacheData) {
            if (onError) {
                assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
            } else {
                assertThat(subscriber2.awaitOnSuccess(), is(value));
            }
        } else {
            assertThat(subscriber2.awaitOnSuccess(), is(value2));
        }
    }

    @Test
    void inlineCancelFromOnSubscribeToMultipleSubscribers() {
        Single<Integer> single = Single.succeeded(1).cache(2, false);
        @SuppressWarnings("unchecked")
        SingleSource.Subscriber<Integer> sub1 = mock(SingleSource.Subscriber.class);
        @SuppressWarnings("unchecked")
        SingleSource.Subscriber<Integer> sub2 = mock(SingleSource.Subscriber.class);
        doAnswer((Answer<Void>) invocation -> {
            Cancellable c = invocation.getArgument(0);
            c.cancel();
            return null;
        }).when(sub1).onSubscribe(any());
        toSource(single).subscribe(sub1);
        toSource(single).subscribe(sub2);

        verify(sub1, Mockito.never()).onSuccess(any());
        verify(sub2).onSuccess(eq(1));
    }

    @ParameterizedTest
    @CsvSource(value = {"400,401", "400,200", "400,2"})
    void concurrentSubscribers(int expectedSubscribers, int latchCount) throws Exception {
        Single<Integer> multicast = source.cache(expectedSubscribers);
        ExecutorService executorService = new ThreadPoolExecutor(0, expectedSubscribers, 1, SECONDS,
                new SynchronousQueue<>());
        try {
            @SuppressWarnings("unchecked")
            TestSingleSubscriber<Integer>[] subscribers = (TestSingleSubscriber<Integer>[])
                    new TestSingleSubscriber[expectedSubscribers];
            CountDownLatch latch = new CountDownLatch(latchCount);
            List<Future<?>> futures = new ArrayList<>(subscribers.length);
            for (int i = 0; i < subscribers.length; ++i) {
                final TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();
                subscribers[i] = subscriber;
                futures.add(executorService.submit(() -> {
                    latch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                    toSource(multicast).subscribe(subscriber);
                }));
            }

            latch.countDown();
            latch.await();
            source.onSuccess(1);

            for (Future<?> future : futures) {
                future.get();
            }

            for (final TestSingleSubscriber<Integer> subscriber : subscribers) {
                assertThat(subscriber.awaitOnSuccess(), is(1));
            }
        } finally {
            executorService.shutdown();
        }
    }
}
