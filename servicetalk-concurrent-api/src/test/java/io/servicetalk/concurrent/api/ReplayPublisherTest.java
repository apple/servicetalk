/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

final class ReplayPublisherTest extends MulticastPublisherTest {
    private final TestPublisherSubscriber<Integer> subscriber4 = new TestPublisherSubscriber<>();
    private final TestExecutor executor = new TestExecutor();

    @AfterEach
    void tearDown() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @Override
    <T> Publisher<T> applyOperator(Publisher<T> source, int minSubscribers) {
        return source.replay(new ReplayStrategyBuilder<T>(EmptyReplayAccumulator::emptyAccumulator)
                .minSubscribers(minSubscribers).build());
    }

    @Override
    <T> Publisher<T> applyOperator(Publisher<T> source, int minSubscribers, boolean cancelUpstream) {
        return source.replay(new ReplayStrategyBuilder<T>(EmptyReplayAccumulator::emptyAccumulator)
                .cancelUpstream(cancelUpstream)
                .minSubscribers(minSubscribers).build());
    }

    @Override
    <T> Publisher<T> applyOperator(Publisher<T> source, int minSubscribers, int queueLimit,
                                   Function<Throwable, Completable> terminalResubscribe) {
        return source.replay(new ReplayStrategyBuilder<T>(EmptyReplayAccumulator::emptyAccumulator)
                .queueLimitHint(queueLimit)
                .terminalResubscribe(terminalResubscribe)
                .minSubscribers(minSubscribers).build());
    }

    @Override
    <T> Publisher<T> applyOperator(Publisher<T> source, int minSubscribers, int queueLimit) {
        return source.replay(new ReplayStrategyBuilder<T>(EmptyReplayAccumulator::emptyAccumulator)
                .queueLimitHint(queueLimit)
                .minSubscribers(minSubscribers).build());
    }

    @Override
    <T> Publisher<T> applyOperator(Publisher<T> source, int minSubscribers, int queueLimit, boolean cancelUpstream) {
        return source.replay(new ReplayStrategyBuilder<T>(EmptyReplayAccumulator::emptyAccumulator)
                .queueLimitHint(queueLimit)
                .cancelUpstream(cancelUpstream)
                .minSubscribers(minSubscribers).build());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void twoSubscribersHistory(boolean onError) {
        Publisher<Integer> publisher = source.replay(2);
        toSource(publisher).subscribe(subscriber1);
        subscriber1.awaitSubscription().request(4);
        assertThat(subscription.requested(), is(4L));
        source.onNext(1, 2, null);
        assertThat(subscriber1.takeOnNext(3), contains(1, 2, null));

        toSource(publisher).subscribe(subscriber2);
        subscriber2.awaitSubscription().request(4);
        assertThat(subscription.requested(), is(4L));

        assertThat(subscriber2.takeOnNext(2), contains(2, null));

        source.onNext(4);
        assertThat(subscriber1.takeOnNext(), is(4));
        assertThat(subscriber2.takeOnNext(), is(4));

        twoSubscribersTerminate(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void subscribeAfterTerminalDeliversHistory(boolean onError) {
        Publisher<Integer> publisher = source.replay(2);
        toSource(publisher).subscribe(subscriber1);
        subscriber1.awaitSubscription().request(4);
        assertThat(subscription.requested(), is(4L));
        source.onNext(1, 2, 3);
        assertThat(subscriber1.takeOnNext(3), contains(1, 2, 3));
        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber1.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            subscriber1.awaitOnComplete();
        }

        toSource(publisher).subscribe(subscriber2);
        subscriber2.awaitSubscription().request(4);
        assertThat(subscriber2.takeOnNext(2), contains(2, 3));
        if (onError) {
            assertThat(subscriber2.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            subscriber2.awaitOnComplete();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void threeSubscribersSum(boolean onError) {
        Publisher<Integer> publisher = source.replay(SumReplayAccumulator::new);
        toSource(publisher).subscribe(subscriber1);
        subscriber1.awaitSubscription().request(4);
        assertThat(subscription.requested(), is(4L));
        source.onNext(1, 2, 3);
        assertThat(subscriber1.takeOnNext(3), contains(1, 2, 3));

        toSource(publisher).subscribe(subscriber2);
        subscriber2.awaitSubscription().request(4);
        assertThat(subscription.requested(), is(4L));

        assertThat(subscriber2.takeOnNext(), equalTo(6));

        source.onNext(4);
        assertThat(subscriber1.takeOnNext(), is(4));
        assertThat(subscriber2.takeOnNext(), is(4));

        toSource(publisher).subscribe(subscriber3);
        subscriber3.awaitSubscription().request(4);
        assertThat(subscription.requested(), is(4L));
        assertThat(subscriber3.takeOnNext(), equalTo(10));

        subscriber1.awaitSubscription().request(1);
        assertThat(subscription.requested(), is(5L));
        source.onNext(5);

        assertThat(subscriber1.takeOnNext(), is(5));
        assertThat(subscriber2.takeOnNext(), is(5));
        assertThat(subscriber3.takeOnNext(), is(5));

        threeSubscribersTerminate(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void threeSubscribersTTL(boolean onError) {
        final Duration ttl = ofMillis(2);
        Publisher<Integer> publisher = source.replay(2, ttl, executor);
        toSource(publisher).subscribe(subscriber1);
        subscriber1.awaitSubscription().request(4);
        assertThat(subscription.requested(), is(4L));
        source.onNext(1, 2);
        executor.advanceTimeBy(1, MILLISECONDS);
        source.onNext((Integer) null);
        assertThat(subscriber1.takeOnNext(3), contains(1, 2, null));

        toSource(publisher).subscribe(subscriber2);
        subscriber2.awaitSubscription().request(4);
        assertThat(subscriber2.takeOnNext(2), contains(2, null));

        executor.advanceTimeBy(1, MILLISECONDS);
        toSource(publisher).subscribe(subscriber3);
        subscriber3.awaitSubscription().request(4);
        assertThat(subscriber3.takeOnNext(), equalTo(null));

        source.onNext(4);
        assertThat(subscriber1.takeOnNext(), equalTo(4));
        assertThat(subscriber2.takeOnNext(), equalTo(4));
        assertThat(subscriber3.takeOnNext(), equalTo(4));

        subscriber1.awaitSubscription().request(10);
        subscriber2.awaitSubscription().request(10);
        subscriber3.awaitSubscription().request(10);
        executor.advanceTimeBy(ttl.toMillis(), MILLISECONDS);
        toSource(publisher).subscribe(subscriber4);
        subscriber4.awaitSubscription().request(4);
        assertThat(subscriber4.pollOnNext(10, MILLISECONDS), nullValue());

        threeSubscribersTerminate(onError);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentTTL(boolean onError) throws Exception {
        final Duration ttl = ofNanos(1);
        final int queueLimit = Integer.MAX_VALUE;
        Executor executor2 = Executors.newCachedThreadExecutor();
        ScheduleQueueExecutor queueExecutor = new ScheduleQueueExecutor(executor2);
        Publisher<Integer> publisher = source.replay(
                ReplayStrategies.<Integer>historyTtlBuilder(2, ttl, queueExecutor)
                .queueLimitHint(queueLimit).build());
        try {
            toSource(publisher).subscribe(subscriber1);
            toSource(publisher).subscribe(subscriber2);
            subscriber1.awaitSubscription().request(Long.MAX_VALUE);
            subscriber2.awaitSubscription().request(Long.MAX_VALUE);
            subscription.awaitRequestN(queueLimit);
            // The goal is to race onNext (which calls accumulate) with the timer expiration. We don't verify all the
            // signals are delivered but instead verify that the timer and max elements are always enforced even after
            // the concurrent operations.
            for (int i = 0; i < 10000; ++i) {
                source.onNext(1);
                Thread.yield(); // Increase likelihood that timer expires some signals.
            }

            // Wait for the timer to expire all signals.
            waitForReplayQueueToDrain(publisher);

            queueExecutor.enableScheduleQueue();
            source.onNext(2, 3);
            toSource(publisher).subscribe(subscriber3);
            subscriber3.awaitSubscription().request(Long.MAX_VALUE);
            assertThat(subscriber3.takeOnNext(2), contains(2, 3));

            // Test that advancing the timer past expiration still expires events and there were no race conditions
            queueExecutor.drainScheduleQueue();
            waitForReplayQueueToDrain(publisher);

            // We don't consume signals for subscriber1 and subscriber2, so just test termination of subscriber3.
            if (onError) {
                source.onError(DELIBERATE_EXCEPTION);
                assertThat(subscriber3.awaitOnError(), is(DELIBERATE_EXCEPTION));
            } else {
                source.onComplete();
                subscriber3.awaitOnComplete();
            }
        } finally {
            executor2.closeAsync().toFuture().get();
        }
    }

    private void waitForReplayQueueToDrain(Publisher<Integer> publisher) throws InterruptedException {
        boolean waitForAccumulatorToDrain;
        do {
            Thread.sleep(1);
            TestPublisherSubscriber<Integer> subscriber5 = new TestPublisherSubscriber<>();
            toSource(publisher).subscribe(subscriber5);
            PublisherSource.Subscription subscription5 = subscriber5.awaitSubscription();
            subscription5.request(Long.MAX_VALUE);
            waitForAccumulatorToDrain = subscriber5.pollOnNext(10, MILLISECONDS) != null;
            subscription5.cancel();
        } while (waitForAccumulatorToDrain);
    }

    @ParameterizedTest(name = "{displayName} [{index}] expectedSubscribers={0} expectedSum={1}")
    @CsvSource(value = {"500,500", "50,50", "50,500", "500,50"})
    void concurrentSubscribes(final int expectedSubscribers, final long expectedSum) throws Exception {
        Publisher<Integer> replay = source.replay(SumReplayAccumulator::new);
        CyclicBarrier startBarrier = new CyclicBarrier(expectedSubscribers + 1);
        Completable[] completables = new Completable[expectedSubscribers];
        @SuppressWarnings("unchecked")
        TestPublisherSubscriber<Integer>[] subscribers = (TestPublisherSubscriber<Integer>[])
                new TestPublisherSubscriber[expectedSubscribers];
        Executor executor = Executors.newCachedThreadExecutor();
        try {
            for (int i = 0; i < subscribers.length; ++i) {
                final TestPublisherSubscriber<Integer> currSubscriber = new TestPublisherSubscriber<>();
                subscribers[i] = currSubscriber;
                completables[i] = executor.submit(() -> {
                    try {
                        startBarrier.await();
                        toSource(replay).subscribe(currSubscriber);
                        currSubscriber.awaitSubscription().request(expectedSum);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            Future<Void> future = Completable.mergeAll(completables.length, completables).toFuture();
            startBarrier.await();
            for (int i = 0; i < expectedSum; ++i) {
                subscription.awaitRequestN(i + 1);
                source.onNext(1);
            }

            future.get();
            source.onComplete(); // deliver terminal after all requests have been delivered.

            for (final TestPublisherSubscriber<Integer> currSubscriber : subscribers) {
                int numOnNext = 0;
                long currSum = 0;
                while (currSum < expectedSum) {
                    Integer next = currSubscriber.takeOnNext();
                    ++numOnNext;
                    if (next != null) {
                        currSum += next;
                    }
                }
                try {
                    assertThat(currSum, equalTo(expectedSum));
                    currSubscriber.awaitOnComplete();
                } catch (Throwable cause) {
                    throw new AssertionError("failure numOnNext=" + numOnNext, cause);
                }
            }

            subscription.awaitRequestN(expectedSum);
            assertThat(subscription.isCancelled(), is(false));
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    private static final class EmptyReplayAccumulator<T> implements ReplayAccumulator<T> {
        static final ReplayAccumulator<?> INSTANCE = new EmptyReplayAccumulator<>();

        private EmptyReplayAccumulator() {
        }

        @SuppressWarnings("unchecked")
        static <T> ReplayAccumulator<T> emptyAccumulator() {
            return (ReplayAccumulator<T>) INSTANCE;
        }

        @Override
        public void accumulate(@Nullable final T t) {
        }

        @Override
        public void deliverAccumulation(final Consumer<T> consumer) {
        }
    }

    private static final class SumReplayAccumulator implements ReplayAccumulator<Integer> {
        private int sum;

        @Override
        public void accumulate(@Nullable final Integer integer) {
            if (integer != null) {
                sum += integer;
            }
        }

        @Override
        public void deliverAccumulation(final Consumer<Integer> consumer) {
            if (sum != 0) {
                consumer.accept(sum);
            }
        }
    }

    private static final class ScheduleHolder implements Cancellable {
        final Duration duration;
        final Runnable task;
        final AtomicBoolean isCancelled = new AtomicBoolean();

        ScheduleHolder(final long duration, final TimeUnit unit, final Runnable task) {
            this(Duration.ofNanos(unit.toNanos(duration)), task);
        }

        ScheduleHolder(final Duration duration, final Runnable task) {
            this.duration = duration;
            this.task = task;
        }

        @Override
        public void cancel() {
            isCancelled.set(true);
        }
    }

    private static final class ScheduleQueueExecutor implements io.servicetalk.concurrent.Executor {
        private final io.servicetalk.concurrent.Executor executor;
        private final AtomicBoolean enableScheduleQueue = new AtomicBoolean();
        private final Queue<ScheduleHolder> scheduleQueue = new ConcurrentLinkedQueue<>();

        private ScheduleQueueExecutor(final io.servicetalk.concurrent.Executor executor) {
            this.executor = executor;
        }

        void enableScheduleQueue() {
            enableScheduleQueue.set(true);
        }

        void drainScheduleQueue() {
            if (enableScheduleQueue.compareAndSet(true, false)) {
                ScheduleHolder item;
                while ((item = scheduleQueue.poll()) != null) {
                    if (item.isCancelled.compareAndSet(false, true)) {
                        executor.schedule(item.task, item.duration);
                    }
                }
            }
        }

        @Override
        public long currentTime(final TimeUnit unit) {
            return executor.currentTime(unit);
        }

        @Override
        public Cancellable execute(final Runnable task) throws RejectedExecutionException {
            return executor.execute(task);
        }

        @Override
        public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
                throws RejectedExecutionException {
            if (enableScheduleQueue.get()) {
                ScheduleHolder holder = new ScheduleHolder(delay, unit, task);
                scheduleQueue.add(holder);
                return holder;
            } else {
                return executor.schedule(task, delay, unit);
            }
        }

        @Override
        public Cancellable schedule(final Runnable task, final Duration delay) throws RejectedExecutionException {
            if (enableScheduleQueue.get()) {
                ScheduleHolder holder = new ScheduleHolder(delay, task);
                scheduleQueue.add(holder);
                return holder;
            } else {
                return executor.schedule(task, delay);
            }
        }
    }
}
