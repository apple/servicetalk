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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class RepeatWhenSingleTest {
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private static Executor executor;

    @AfterAll
    static void tearDown() throws Exception {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @BeforeAll
    static void setup() throws Exception {
        executor = Executors.newCachedThreadExecutor();
    }

    @Test
    void repeaterNull() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? completed() : null))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        assertThat(subscriber.awaitOnError(), instanceOf(NullPointerException.class));
    }

    @Test
    void repeaterThrows() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> {
                    throw DELIBERATE_EXCEPTION;
                }))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(2);
        assertThat(subscriber.takeOnNext(), equalTo(1));
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void invalidDemand() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? completed() : failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(-1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void concurrentInvalidDemand() throws BrokenBarrierException, InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? executor.submit(() -> {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                }) : failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        barrier.await();
        s.request(-1);
        s.request(-1); // test multiple invalid request-n should only terminate one time.
        assertThat(subscriber.takeOnNext(), equalTo(1));
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void invalidDemandAfterFirst() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? completed() : failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        assertThat(subscriber.takeOnNext(), equalTo(1));
        s.request(-1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void singleRepeatAllDemandUpfront() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? completed() : failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        subscriber.awaitOnComplete();
    }

    @Test
    void manyRepeatAllDemandUpfront() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount < 5 ? completed() : failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(5);
        assertThat(subscriber.takeOnNext(5), contains(1, 2, 3, 4, 5));
        subscriber.awaitOnComplete();
    }

    @Test
    void singleRepeatDelayedDemand() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? executor.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                }) : failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(2);
        assertThat(subscriber.takeOnNext(), equalTo(1));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), nullValue());
        latch.countDown();
        assertThat(subscriber.takeOnNext(), equalTo(2));
        subscriber.awaitOnComplete();
    }

    @Test
    void singleDelayedRepeat() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ?
                        executor.timer(1, MILLISECONDS) : failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        assertThat(subscriber.takeOnNext(), equalTo(1));
        s.request(1);
        assertThat(subscriber.takeOnNext(), equalTo(2));
        subscriber.awaitOnComplete();

        // interaction with the subscription after termination should be NOOP.
        s.request(1);
        s.request(-1);
        s.cancel();
    }

    @Test
    void reentrantCancellationStopsRepeat() throws InterruptedException {
        AtomicInteger value = new AtomicInteger();
        CountDownLatch cancelled = new CountDownLatch(1);
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .whenCancel(cancelled::countDown)
                .repeatWhen((repeatCount, v) -> completed()))
                .subscribe(new PublisherSource.Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(final Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                        subscriber.awaitSubscription().request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(@Nullable final Integer integer) {
                        subscriber.awaitSubscription().cancel(); // cancel inline, should stop repeating
                        subscriber.onNext(integer);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                });
        assertThat(subscriber.takeOnNext(), equalTo(1));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), nullValue());
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), nullValue());
        cancelled.await();
    }

    @Test
    void reentrantRequest() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? completed() : failed(DELIBERATE_EXCEPTION)))
                .subscribe(new PublisherSource.Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(final Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                        subscriber.awaitSubscription().request(1);
                    }

                    @Override
                    public void onNext(@Nullable final Integer integer) {
                        subscriber.onNext(integer);
                        subscriber.awaitSubscription().request(1);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                });
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        subscriber.awaitOnComplete();
    }

    @Test
    void onNextThrows() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? completed() : failed(new DeliberateException()))
                .<Integer>map(x -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void secondSingleFailed() {
        AtomicInteger value = new AtomicInteger();
        toSource(Single.defer(() -> {
                    final int v = value.incrementAndGet();
                    return v == 1 ? Single.succeeded(v) : Single.failed(DELIBERATE_EXCEPTION);
                })
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? completed() : failed(new DeliberateException())))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        assertThat(subscriber.takeOnNext(), equalTo(1));
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void cancellationBeforeEmission() throws InterruptedException {
        AtomicInteger value = new AtomicInteger();
        CountDownLatch cancelled = new CountDownLatch(1);
        CountDownLatch onSubscribe = new CountDownLatch(1);
        toSource(Single.defer(() -> Single.succeeded(value.incrementAndGet()))
                .whenCancel(cancelled::countDown)
                .beforeOnSubscribe(cancellable -> onSubscribe.countDown())
                .repeatWhen((repeatCount, v) -> repeatCount == 1 ? completed() : failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.cancel();
        s.request(Long.MAX_VALUE);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), nullValue());
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), nullValue());

        // We never subscribe upstream if cancellation happens first.
        assertThat(onSubscribe.await(10, MILLISECONDS), is(false));
        assertThat(cancelled.await(10, MILLISECONDS), is(false));
    }
}
