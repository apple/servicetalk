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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.drainSingleConsumerQueueDelayThrow;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.executeExclusive;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public final class ConcurrentUtilsTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private static final AtomicIntegerFieldUpdater<ConcurrentUtilsTest> drainingUpdater =
            newUpdater(ConcurrentUtilsTest.class, "draining");

    @SuppressWarnings("unused")
    private volatile int draining;
    private QueueHolder qHolder;
    private ExecutorService executor;
    private AtomicInteger drainCounter;

    @Before
    public void setUp() {
        qHolder = new QueueHolder();
        executor = newCachedThreadPool();
        drainCounter = new AtomicInteger();
    }

    @After
    public void tearDown() throws Exception {
        qHolder.q.clear();
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @Test
    public void testDrainSingleConsumerQueueNoConcurrency() {
        qHolder.fillRange(1, 4);
        drainAndAssert(4);
    }

    @Test
    public void testDrainSingleConsumerQueueConcurrent() throws Exception {
        qHolder.fillRange(1, 4);
        Future<?> submit1 = executor.submit(() -> {
            qHolder.q.add(5);
            drain();
        });
        Future<?> submit2 = executor.submit(() -> {
            qHolder.q.add(6);
            drain();
        });
        submit1.get();
        submit2.get();
        assertEquals("Unexpected number of items drained.", drainCounter.get(), 6);
    }

    @Test
    public void testExecuteExclusive() throws Exception {
        final Task[] tasks = new Task[1000];
        final AtomicInteger runCount = new AtomicInteger();
        final CountDownLatch allOthersDone = new CountDownLatch(1);
        final AtomicReference<Throwable> causeRef = new AtomicReference<>();
        final CyclicBarrier startExecuteBarrier = new CyclicBarrier(tasks.length + 1);

        for (int i = 0; i < tasks.length; ++i) {
            final Task currentTask = tasks[i] = new Task(runCount, allOthersDone, causeRef);
            executor.execute(() -> {
                try {
                    startExecuteBarrier.await();
                    currentTask.executeBefore.countDown();
                    executeExclusive(currentTask, drainingUpdater, this);
                    currentTask.executeAfter.countDown();
                } catch (Throwable cause) {
                    causeRef.set(cause);
                }
            });
        }

        startExecuteBarrier.await();
        assertNull(causeRef.get());

        int runningIndex = -1;
        for (int i = 0; i < tasks.length; ++i) {
            final Task currentTask = tasks[i];
            currentTask.executeBefore.await();

            while (!currentTask.executeAfter.await(50, MILLISECONDS)) {
                if (currentTask.running.await(50, MILLISECONDS)) {
                    if (runningIndex != -1) {
                        throw new AssertionError("index: " + i + " and runningIndex: " + runningIndex + " are both running");
                    }
                    runningIndex = i;
                    break;
                }
            }
        }

        assertNotEquals(-1, runningIndex);
        allOthersDone.countDown();
        tasks[runningIndex].executeAfter.await();
        assertEquals(2, runCount.get());
        assertNull(causeRef.get());
    }

    private void drainAndAssert(int expectedDrainCount) {
        drain();
        assertEquals("Unexpected number of items drained.", drainCounter.get(), expectedDrainCount);
    }

    private void drain() {
        drainSingleConsumerQueueDelayThrow(qHolder.getQueue(), integer -> drainCounter.incrementAndGet(),
                QueueHolder.drainingUpdater, qHolder);
    }

    private static final class QueueHolder {

        private static final AtomicIntegerFieldUpdater<QueueHolder> drainingUpdater =
                newUpdater(QueueHolder.class, "draining");
        @SuppressWarnings("unused")
        private volatile int draining;

        private final Queue<Integer> q = new ConcurrentLinkedQueue<>();

        Queue<Integer> getQueue() {
            return q;
        }

        void fillRange(int start, int end) {
            for (int i = start; i <= end; i++) {
                q.add(i);
            }
        }
    }

    private static final class Task implements Runnable {
        private final AtomicInteger runCount;
        private final CountDownLatch allOthersDone;
        private final AtomicReference<Throwable> causeRef;
        final CountDownLatch executeBefore = new CountDownLatch(1);
        final CountDownLatch executeAfter = new CountDownLatch(1);
        final CountDownLatch running = new CountDownLatch(1);

        Task(AtomicInteger runCount, CountDownLatch allOthersDone, AtomicReference<Throwable> causeRef) {
            this.runCount = runCount;
            this.allOthersDone = allOthersDone;
            this.causeRef = causeRef;
        }

        @Override
        public void run() {
            running.countDown();
            try {
                allOthersDone.await();
                runCount.incrementAndGet();
            } catch (Throwable cause) {
                causeRef.set(cause);
            }
        }
    }
}
