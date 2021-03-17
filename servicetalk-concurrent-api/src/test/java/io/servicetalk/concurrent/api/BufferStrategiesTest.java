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

import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.BufferStrategies.forCountOrTime;
import static io.servicetalk.concurrent.api.Completable.defer;
import static io.servicetalk.concurrent.api.Publisher.range;
import static java.time.Duration.ofDays;
import static java.util.Collections.unmodifiableCollection;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BufferStrategiesTest {
    private final BlockingQueue<TestCompletable> timers = new LinkedBlockingDeque<>();
    private final Executor executor = mock(Executor.class);
    private final java.util.concurrent.ExecutorService jdkExecutor = Executors.newCachedThreadPool();

    public BufferStrategiesTest() {
        when(executor.timer(any(Duration.class))).thenAnswer(invocation -> defer(() -> {
            TestCompletable timer = new TestCompletable();
            timers.add(timer);
            return timer;
        }));
    }

    @AfterEach
    public void tearDown() {
        jdkExecutor.shutdownNow();
    }

    @Disabled("https://github.com/apple/servicetalk/issues/1259")
    @Test
    public void sizeOrDurationConcurrent() throws Exception {
        final int maxBoundaries = 1_000;
        final Collection<Integer> items = unmodifiableCollection(range(1, 1_000).toFuture().get());
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

        Future<Object> timersFuture = jdkExecutor.submit(() -> {
            accumulateAndTimerStarted.await();
            while (boundariesDone.getCount() > 0) {
                timers.take().onComplete();
            }
            return null;
        });

        Future<Object> accumulateFuture = jdkExecutor.submit(() -> {
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
        });

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
}
