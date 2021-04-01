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

import io.servicetalk.concurrent.api.AsyncContextMap.Key;
import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.ExecutorExtension.withCachedExecutor;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.fail;

public class PublisherBufferConcurrencyTest {
    private static final String THREAD_NAME_PREFIX = "buffer-concurrency-test";
    private static final Key<Integer> CTX_KEY = Key.newKey("foo");

    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = withCachedExecutor(THREAD_NAME_PREFIX);

    @Test
    public void largeRun() throws Exception {
        runTest(identity(), identity());
    }

    @Test
    public void executorIsPreserved() throws Exception {
        final Executor executor = executorExtension.executor();
        runTest(beforeBuffer -> beforeBuffer.publishOn(executor),
                afterBuffer -> afterBuffer.beforeOnNext(__ ->
                        assertThat("Unexpected thread in onNext.", Thread.currentThread().getName(),
                                startsWith(THREAD_NAME_PREFIX)))
                        .beforeOnComplete(() ->
                                assertThat("Unexpected thread in onComplete.", Thread.currentThread().getName(),
                                        startsWith(THREAD_NAME_PREFIX))
        ));
    }

    @Test
    public void contextIsPreserved() throws Exception {
        AsyncContext.put(CTX_KEY, 0);
        runTest(beforeBuffer -> beforeBuffer.beforeOnSubscribe(__ -> AsyncContext.put(CTX_KEY, 1)),
                afterBuffer -> afterBuffer.beforeOnNext(__ ->
                        assertThat("Unexpected value in context.", AsyncContext.get(CTX_KEY),
                                is(1)))
                        .beforeOnComplete(() ->
                            assertThat("Unexpected value in context.", AsyncContext.get(CTX_KEY),
                                    is(1))));
    }

    @Test
    public void addingAndBoundaryEmission() throws Exception {
        TestPublisher<Integer> original = new TestPublisher<>();
        TestPublisher<Accumulator<Integer, Integer>> boundaries = new TestPublisher<>();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        CountDownLatch waitForBoundary = new CountDownLatch(1);
        CountDownLatch waitForAdd = new CountDownLatch(1);
        Accumulator<Integer, Integer> accumulator = new Accumulator<Integer, Integer>() {
            private int added;
            @Override
            public void accumulate(@Nullable final Integer integer) {
                waitForAdd.countDown();
                try {
                    waitForBoundary.await();
                    if (integer == null) {
                        return;
                    }
                    added = integer;
                } catch (InterruptedException e) {
                    fail();
                }
            }

            @Override
            public Integer finish() {
                return added;
            }
        };
        toSource(original.buffer(new BufferStrategy<Integer, Accumulator<Integer, Integer>, Integer>() {
            @Override
            public Publisher<Accumulator<Integer, Integer>> boundaries() {
                return boundaries;
            }

            @Override
            public int bufferSizeHint() {
                return 8;
            }
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        boundaries.onNext(accumulator); // initial boundary
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));

        CountDownLatch waitForOnNextReturn = new CountDownLatch(1);
        executorExtension.executor().submit(() -> original.onNext(1))
                .beforeFinally(waitForOnNextReturn::countDown).subscribe();
        waitForAdd.await();
        subscriber.awaitSubscription().request(1);
        boundaries.onNext(new SummingAccumulator());
        waitForBoundary.countDown();
        waitForOnNextReturn.await();

        boundaries.onNext(new SummingAccumulator()); // Last accumulator will be overwritten by add()
        assertThat("Unexpected result.", subscriber.takeOnNext(), is(1));

        original.onComplete();
        boundaries.onNext(new SummingAccumulator()); // Boundary has to complete for terminal to be emitted
        assertThat("Unexpected result.", subscriber.takeOnNext(), is(0)); // empty accumulator

        subscriber.awaitOnComplete();
    }

    private void runTest(final UnaryOperator<Publisher<Integer>> beforeBuffer,
                         final UnaryOperator<Publisher<Iterable<Integer>>> afterBuffer) throws Exception {
        final int maxRange = 1000;
        final int repeatMax = 100;
        final Executor executor = executorExtension.executor();
        Publisher<Integer> original = Publisher.range(0, maxRange)
                .repeatWhen(count -> count == repeatMax ? failed(DELIBERATE_EXCEPTION) :
                        executor.timer(ofMillis(1)));

        Publisher<Iterable<Integer>> buffered = beforeBuffer.apply(original)
                .buffer(BufferStrategies.forCountOrTime(maxRange / 20, ofMillis(500)));

        afterBuffer.apply(buffered).beforeOnNext(new Consumer<Iterable<Integer>>() {
            private int lastValue = -1;

            @Override
            public void accept(final Iterable<Integer> ints) {
                for (Integer anInt : ints) {
                    assertThat("Unexpected value", anInt, greaterThan(lastValue));
                    lastValue = anInt == (maxRange - 1) ? -1 : anInt;
                }
            }
        })
                .ignoreElements()
                .toFuture()
                .get();
    }

    private static class SummingAccumulator implements Accumulator<Integer, Integer> {
        private int sum;

        @Override
        public void accumulate(@Nullable final Integer item) {
            if (item == null) {
                return;
            }
            sum += item;
        }

        @Override
        public Integer finish() {
            return sum;
        }
    }
}
