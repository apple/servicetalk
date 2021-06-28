/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.calculateSourceRequested;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConcurrentUtilsSourceRequestedTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout(2, MINUTES);

    private static final AtomicLongFieldUpdater<ConcurrentUtilsSourceRequestedTest> requestNUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentUtilsSourceRequestedTest.class, "requestN");
    private static final AtomicLongFieldUpdater<ConcurrentUtilsSourceRequestedTest> sourceRequestedUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentUtilsSourceRequestedTest.class, "sourceRequested");
    private static final AtomicLongFieldUpdater<ConcurrentUtilsSourceRequestedTest> emittedUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentUtilsSourceRequestedTest.class, "emitted");
    private volatile long requestN;
    private volatile long sourceRequested;
    private volatile long emitted;

    @Test
    public void calculateSourceRequestedSteadyState() {
        emitted = 9;
        requestN = 9;
        sourceRequested = 9;
        assertEquals(0, calculateSourceRequested(requestNUpdater, sourceRequestedUpdater, emittedUpdater, 9, this));
        assertEquals(9, emitted);
        assertEquals(9, requestN);
        assertEquals(9, sourceRequested);
    }

    @Test
    public void calculateSourceRequestedOutstandingAgainstLimit() {
        emitted = 1;
        requestN = 15;
        sourceRequested = 11;
        assertEquals(0, calculateSourceRequested(requestNUpdater, sourceRequestedUpdater, emittedUpdater, 10, this));
        assertEquals(1, emitted);
        assertEquals(15, requestN);
        assertEquals(11, sourceRequested);
    }

    @Test
    public void calculateSourceRequestedOutstandingNotAgainstLimit() {
        emitted = 3;
        requestN = 15;
        sourceRequested = 11;
        assertEquals(2, calculateSourceRequested(requestNUpdater, sourceRequestedUpdater, emittedUpdater, 10, this));
        assertEquals(3, emitted);
        assertEquals(15, requestN);
        assertEquals(13, sourceRequested);
    }

    @Test
    public void calculateSourceRequestedDemandDoesNotExceedLimit() {
        emitted = 13;
        requestN = 91;
        sourceRequested = 13;
        assertEquals(7, calculateSourceRequested(requestNUpdater, sourceRequestedUpdater, emittedUpdater, 7, this));
        assertEquals(13, emitted);
        assertEquals(91, requestN);
        assertEquals(20, sourceRequested);
    }

    @Test
    public void calculateSourceRequestedWithLargerValuesDoesNotOverflow() {
        emitted = Long.MAX_VALUE - 4;
        requestN = Long.MAX_VALUE;
        sourceRequested = Long.MAX_VALUE - 3;
        assertEquals(3, calculateSourceRequested(requestNUpdater, sourceRequestedUpdater, emittedUpdater,
                Integer.MAX_VALUE, this));
        assertEquals(Long.MAX_VALUE - 4, emitted);
        assertEquals(Long.MAX_VALUE, requestN);
        assertEquals(Long.MAX_VALUE, sourceRequested);
    }

    @Test
    public void calculateSourceRequestedWithLargerValuesCanDemandIntMax() {
        emitted = Long.MAX_VALUE - Integer.MAX_VALUE;
        requestN = Long.MAX_VALUE;
        sourceRequested = Long.MAX_VALUE - Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, calculateSourceRequested(requestNUpdater, sourceRequestedUpdater,
                emittedUpdater, Integer.MAX_VALUE, this));
        assertEquals(Long.MAX_VALUE - Integer.MAX_VALUE, emitted);
        assertEquals(Long.MAX_VALUE, requestN);
        assertEquals(Long.MAX_VALUE, sourceRequested);
    }

    @Test
    public void calculateSourceRequestedConcurrentA() throws Exception {
        calculateSourceRequestedConcurrentLoop(5, 1, 3, 10, 10);
    }

    @Test
    public void calculateSourceRequestedConcurrentB() throws Exception {
        calculateSourceRequestedConcurrentLoop(5, 5, 6, 100, 123456);
    }

    @Test
    public void calculateSourceRequestedConcurrentC() throws Exception {
        calculateSourceRequestedConcurrentLoop(5, 1, 3, 2, 900103);
    }

    @Test
    public void calculateSourceRequestedConcurrentD() throws Exception {
        calculateSourceRequestedConcurrentLoop(5, 1, 5635, 483, 800026);
    }

    @Test
    public void calculateSourceRequestedConcurrentE() throws Exception {
        calculateSourceRequestedConcurrentLoop(5, 6, 512, Integer.MAX_VALUE, 1000001);
    }

    private void calculateSourceRequestedConcurrentLoop(
            final int iterations, final int consumerThreads, final int limit, final int maxRequest,
            final int totalExpectedCount) throws Exception {
        for (int i = 0; i < iterations; ++i) {
            calculateSourceRequestedConcurrent(consumerThreads, limit, maxRequest, totalExpectedCount);
        }
    }

    private void calculateSourceRequestedConcurrent(final int consumerThreads, final int limit, final int maxRequest,
                                                    final int totalExpectedCount) throws Exception {
        assert consumerThreads > 0;
        requestN = 0;
        sourceRequested = 0;
        emitted = 0;
        ExecutorService executorService = Executors.newFixedThreadPool(1 + consumerThreads);
        try {
            final CyclicBarrier barrier = new CyclicBarrier(2 + consumerThreads);
            final List<Future<?>> futures = new ArrayList<>(1 + consumerThreads);
            final AtomicLong totalCount = new AtomicLong();
            final AtomicLong totalCountNotConsumed = new AtomicLong();
            final AtomicLong totalCountConsumed = new AtomicLong();

            futures.add(executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new IllegalStateException("unexpected exception", e);
                }
                int produced = 0;
                final Random random = ThreadLocalRandom.current();
                while (produced < totalExpectedCount) {
                    int localProduced = random.nextInt(maxRequest) + 1;
                    if (produced > totalExpectedCount - localProduced) {
                        localProduced = totalExpectedCount - produced;
                        produced = totalExpectedCount;
                    } else {
                        produced += localProduced;
                    }
                    requestNUpdater.addAndGet(this, localProduced);
                    long amount = calculateSourceRequested(requestNUpdater, sourceRequestedUpdater, emittedUpdater,
                            limit, this);
                    assertTrue("invalid increment: " + amount, amount <= limit && amount >= 0);
                    totalCount.addAndGet(amount);
                    totalCountNotConsumed.addAndGet(amount);
                }
            }));

            for (int i = 0; i < consumerThreads; ++i) {
                futures.add(executorService.submit(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        throw new IllegalStateException("unexpected exception", e);
                    }
                    final Random random = ThreadLocalRandom.current();
                    for (;;) {
                        int localConsumed;
                        for (;;) {
                            long totalNotConsumed = totalCountNotConsumed.get();
                            if (totalNotConsumed == 0) {
                                if (totalExpectedCount == totalCountConsumed.get()) {
                                    return;
                                }
                                Thread.yield();
                                continue;
                            }
                            localConsumed = random.nextInt((int) min(Integer.MAX_VALUE, totalNotConsumed)) + 1;
                            if (totalCountNotConsumed.compareAndSet(totalNotConsumed,
                                    totalNotConsumed - localConsumed)) {
                                break;
                            }
                        }
                        totalCountConsumed.addAndGet(localConsumed);
                        emittedUpdater.addAndGet(this, localConsumed);
                        long amount = calculateSourceRequested(requestNUpdater, sourceRequestedUpdater, emittedUpdater,
                                limit, this);
                        assertTrue("invalid decrement: " + amount, amount <= limit && amount >= 0);
                        totalCount.addAndGet(amount);
                        totalCountNotConsumed.addAndGet(amount);
                    }
                }));
            }

            barrier.await();

            for (Future<?> f : futures) {
                f.get();
            }

            assertEquals(totalExpectedCount, totalCount.get());
            assertEquals(totalExpectedCount, totalCountConsumed.get());
            assertEquals(0, totalCountNotConsumed.get());
            assertEquals(totalExpectedCount, requestN);
            assertEquals(totalExpectedCount, sourceRequested);
            assertEquals(totalExpectedCount, emitted);
        } finally {
            executorService.shutdown();
        }
    }
}
