/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.BufferStrategies.forCountOrTime;
import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.concurrent.api.ExecutorExtension.withCachedExecutor;
import static io.servicetalk.concurrent.api.ExecutorExtension.withTestExecutor;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.range;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BufferStrategiesTest {

    @RegisterExtension
    static final ExecutorExtension<Executor> EXECUTOR_EXTENSION = withCachedExecutor();

    @RegisterExtension
    static final ExecutorExtension<TestExecutor> TEST_EXECUTOR_EXTENSION = withTestExecutor();

    @Test
    @Disabled("https://github.com/apple/servicetalk/issues/1259")
    void sizeOrDurationConcurrent() throws Exception {
        final BlockingQueue<TestCompletable> timers = new LinkedBlockingDeque<>();
        final Executor executor = mock(Executor.class);
        when(executor.timer(any(Duration.class))).thenAnswer(invocation -> defer(() -> {
            TestCompletable timer = new TestCompletable();
            timers.add(timer);
            return timer;
        }));

        final int maxBoundaries = 1_000;
        final Collection<Integer> items = unmodifiableCollection(range(1, maxBoundaries).toFuture().get());
        final List<Integer> receivedFromBoundaries = new ArrayList<>(items.size());

        BufferStrategy<Integer, Accumulator<Integer, Iterable<Integer>>, Iterable<Integer>> strategy =
                forCountOrTime(1, ofDays(1), executor);
        CountDownLatch boundariesDone = new CountDownLatch(1);
        BlockingQueue<Accumulator<Integer, Iterable<Integer>>> boundaries = new LinkedBlockingQueue<>();
        BlockingQueue<Accumulator<Integer, Iterable<Integer>>> boundariesToFinish = new LinkedBlockingQueue<>();

        strategy.boundaries()
                .beforeOnNext(boundaries::add)
                .takeAtMost(maxBoundaries)
                .afterFinally(boundariesDone::countDown)
                .ignoreElements().subscribe();
        CyclicBarrier accumulateAndTimerStarted = new CyclicBarrier(2);

        Future<Object> timersFuture = EXECUTOR_EXTENSION.executor().submit(() -> {
            accumulateAndTimerStarted.await();
            while (boundariesDone.getCount() > 0) {
                timers.take().onComplete();
            }
            return null;
        }).toFuture();

        Future<Object> accumulateFuture = EXECUTOR_EXTENSION.executor().submit(() -> {
            accumulateAndTimerStarted.await();
            int boundariesReceived = 0;
            Iterator<Integer> itemsToPopulate = items.iterator();
            while (itemsToPopulate.hasNext()) {
                Accumulator<Integer, Iterable<Integer>> boundary = boundaries.take();
                if (++boundariesReceived < maxBoundaries) {
                    boundary.accumulate(itemsToPopulate.next());
                } else {
                    // add all items to the last boundary
                    do {
                        boundary.accumulate(itemsToPopulate.next());
                    } while (itemsToPopulate.hasNext());
                }
                boundariesToFinish.add(boundary);
            }
            return null;
        }).toFuture();

        boundariesDone.await();
        timersFuture.get();
        accumulateFuture.get();
        Accumulator<Integer, Iterable<Integer>> boundary;
        while ((boundary = boundariesToFinish.poll()) != null) {
            for (Integer item : boundary.finish()) {
                receivedFromBoundaries.add(item);
            }
        }
        assertThat("Unexpected items received.", receivedFromBoundaries, contains(items.toArray()));
    }

    @Test
    void forCountEmptySource() throws Exception {
        assertThat(empty().buffer(forCountOrTime(3, ofDays(1)))
                .toFuture().get(), Matchers.empty());
    }

    @Test
    void forCountFailedSource() {
        ExecutionException e = assertThrows(ExecutionException.class, () -> failed(DELIBERATE_EXCEPTION)
                .buffer(forCountOrTime(3, ofDays(1))).toFuture().get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void forCountOneBoundary() throws Exception {
        assertThat(from(1, 2, 3)
                .buffer(forCountOrTime(3, ofDays(1)))
                .firstOrError()
                .toFuture().get(), contains(1, 2, 3));
    }

    @Test
    void forCountOneBoundaryTerminateBeforeBoundary() throws Exception {
        assertThat(from(1, 2)
                .buffer(forCountOrTime(3, ofDays(1)))
                .firstOrError()
                .toFuture().get(), contains(1, 2));
    }

    @Test
    void forCountMultipleBoundaries() throws Exception {
        assertThat(range(1, 7)
                .buffer(forCountOrTime(2, ofDays(1)))
                .toFuture().get(), contains(asList(1, 2), asList(3, 4), asList(5, 6)));
    }

    @Test
    void forCountMultipleBoundariesTerminateBeforeLastBoundary() throws Exception {
        assertThat(range(1, 6)
                .buffer(forCountOrTime(2, ofDays(1)))
                .toFuture().get(), contains(asList(1, 2), asList(3, 4), singletonList(5)));
    }

    @Test
    void forTimeNoItems() {
        TestExecutor executor = TEST_EXECUTOR_EXTENSION.executor();
        BlockingQueue<Iterable<Integer>> queue = new LinkedBlockingDeque<>();
        Cancellable cancellable = Publisher.<Integer>never()
                .buffer(forCountOrTime(Integer.MAX_VALUE, ofMillis(1), executor))
                .forEach(queue::add);

        executor.advanceTimeBy(1L, MILLISECONDS);
        executor.advanceTimeBy(1L, MILLISECONDS);
        executor.advanceTimeBy(1L, MILLISECONDS);
        cancellable.cancel();

        assertThat(queue, hasSize(3));
        for (Iterable<Integer> it : queue) {
            assertThat(it, emptyIterable());
        }
    }

    @Test
    void forTimeWithItems() {
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestExecutor executor = TEST_EXECUTOR_EXTENSION.executor();
        BlockingQueue<Iterable<Integer>> queue = new LinkedBlockingDeque<>();
        publisher.buffer(forCountOrTime(Integer.MAX_VALUE, ofMillis(1), executor))
                .forEach(queue::add);

        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onNext(1);
        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onNext(2, 3);
        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onComplete();

        assertThat(queue, hasSize(3));
        assertThat(queue.poll(), emptyIterable());
        assertThat(queue.poll(), contains(1));
        assertThat(queue.poll(), contains(2, 3));
    }

    @Test
    void forCount1OrTimeWithItems() {
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestExecutor executor = TEST_EXECUTOR_EXTENSION.executor();
        BlockingQueue<Iterable<Integer>> queue = new LinkedBlockingDeque<>();
        publisher.buffer(forCountOrTime(1, ofMillis(1), executor))
                .forEach(queue::add);

        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onNext(1);
        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onNext(2, 3);
        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onComplete();

        assertThat(queue, hasSize(6));
        assertThat(queue.poll(), emptyIterable());
        assertThat(queue.poll(), contains(1));
        assertThat(queue.poll(), emptyIterable());
        assertThat(queue.poll(), contains(2));
        assertThat(queue.poll(), contains(3));
        assertThat(queue.poll(), emptyIterable());
    }

    @Test
    void forCountOrTimeWithItems() {
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestExecutor executor = TEST_EXECUTOR_EXTENSION.executor();
        BlockingQueue<Iterable<Integer>> queue = new LinkedBlockingDeque<>();
        publisher.buffer(forCountOrTime(3, ofMillis(1), executor))
                .forEach(queue::add);

        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onNext(1);
        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onNext(2, 3);
        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onNext(4, 5, 6);
        executor.advanceTimeBy(1L, MILLISECONDS);
        publisher.onNext(7, 8, 9, 10);
        publisher.onComplete();

        assertThat(queue, hasSize(7));
        assertThat(queue.poll(), emptyIterable());
        assertThat(queue.poll(), contains(1));
        assertThat(queue.poll(), contains(2, 3));
        assertThat(queue.poll(), contains(4, 5, 6));
        assertThat(queue.poll(), emptyIterable());
        assertThat(queue.poll(), contains(7, 8, 9));
        assertThat(queue.poll(), contains(10));
    }

    @Test
    void boundariesEmittedAccordingToDemand() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TerminalNotification> terminated = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        final BlockingQueue<Throwable> asyncErrors = new LinkedBlockingDeque<>();
        toSource(forCountOrTime(2, ofMillis(1)).boundaries())
                .subscribe(new Subscriber<Accumulator<Object, Iterable<Object>>>() {

                    private Subscription s;
                    private int requested;

                    @Override
                    public void onSubscribe(final Subscription subscription) {
                        s = subscription;
                        requested++;
                        s.request(1);
                    }

                    @Override
                    public void onNext(@Nullable final Accumulator<Object, Iterable<Object>> accumulator) {
                        try {
                            assertThat("Unexpected accumulator", accumulator, is(notNullValue()));
                            assertThat("Unexpected accumulation result", accumulator.finish(), emptyIterable());
                        } catch (Throwable t) {
                            asyncErrors.add(t);
                        }
                        count.incrementAndGet();
                        if (requested++ < 5) {
                            s.request(1);
                        } else {
                            s.cancel();
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onError(final Throwable t) {
                        terminated.set(error(t));
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        terminated.set(complete());
                        latch.countDown();
                    }
                });
        latch.await();
        assertThat("Unexpected number of emitted accumulators", count.get(), is(5));
        assertThat("Unexpected termination", terminated.get(), is(nullValue()));
        assertNoAsyncErrors(asyncErrors);
    }

    @Test
    void forCountOneDemandIsRespected() {
        DelayedSubscription subscription = new DelayedSubscription();
        AtomicReference<TerminalNotification> terminated = new AtomicReference<>();
        BlockingQueue<Iterable<Integer>> accumulations = new LinkedBlockingDeque<>();
        toSource(range(1, 6).buffer(forCountOrTime(2, ofDays(1))))
                .subscribe(new Subscriber<Iterable<Integer>>() {
                    @Override
                    public void onSubscribe(final Subscription s) {
                        subscription.delayedSubscription(s);
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(@Nullable final Iterable<Integer> integers) {
                        assert integers != null;
                        accumulations.add(integers);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        terminated.set(error(t));
                    }

                    @Override
                    public void onComplete() {
                        terminated.set(complete());
                    }
                });
        assertThat("Unexpected number of emitted accumulators", accumulations, hasSize(1));
        assertThat("Unexpected accumulators", accumulations, contains(asList(1, 2)));
        assertThat("Unexpected termination", terminated.get(), is(nullValue()));
        subscription.request(1);
        assertThat("Unexpected number of emitted accumulators", accumulations, hasSize(2));
        assertThat("Unexpected accumulators", accumulations, contains(asList(1, 2), asList(3, 4)));
        assertThat("Unexpected termination", terminated.get(), is(nullValue()));
        subscription.request(1);
        assertThat("Unexpected number of emitted accumulators", accumulations, hasSize(3));
        assertThat("Unexpected accumulators", accumulations, contains(asList(1, 2), asList(3, 4), singletonList(5)));
        assertThat("Unexpected termination", terminated.get(), is(complete()));
    }
}
