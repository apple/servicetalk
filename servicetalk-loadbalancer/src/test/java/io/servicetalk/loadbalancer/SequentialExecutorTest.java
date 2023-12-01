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
package io.servicetalk.loadbalancer;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SequentialExecutorTest {

    SequentialExecutor executor = new SequentialExecutor(SequentialExecutorTest.class);

    @Test
    void tasksAreExecuted() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        // submit two tasks and they should both complete.
        executor.execute(() -> latch.countDown());
        executor.execute(() -> latch.countDown());
        latch.await();
    }

    @Test
    void firstTaskIsExecutedByCallingThread() {
        AtomicReference<Thread> executorThread = new AtomicReference<>();
        executor.execute(() -> executorThread.set(Thread.currentThread()));
        assertNotNull(executorThread.get());
        assertEquals(Thread.currentThread(), executorThread.get());
    }

    @Test
    void executionFailuresDontThrowOnSubmission() {
        executor.execute(() -> {
            throw null;
        });
    }

    @Test
    void queuedTasksAreExecuted() throws InterruptedException {
        final CountDownLatch l1 = new CountDownLatch(1);
        final CountDownLatch l2 = new CountDownLatch(1);
        Thread t = new Thread(() ->
        executor.execute(() -> {
            try {
                l1.countDown();
                l2.await();
            } catch (Exception ex) {
                throw new AssertionError("Unexpected failure", ex);
            }
        }));
        t.start();

        // wait for t1 to be in the execution loop then submit a task that should be queued.
        l1.await();

        // note that the behavior of the initial submitting thread executing queued tasks is not critical to the
        // primitive: we could envision another correct implementation where a submitter will  execute the task it just
        // submitted but if there are additional tasks the work gets shifted to a pooled thread to drain. If we switch
        // the model, the test should be adjusted to conform to the desired behavior.
        final AtomicReference<Thread> executingThread = new AtomicReference<>();
        executor.execute(() -> executingThread.set(Thread.currentThread()));
        assertNull(executingThread.get());

        // Now unblock the initial thread and it should also run the second task.
        l2.countDown();
        t.join();
        assertEquals(t, executingThread.get());
    }

    @Test
    void tasksAreNotRenentrant() {
        Queue<Integer> order = new ArrayDeque<>();
        executor.execute(() -> {
            // this should be queued for later.
            executor.execute(() -> order.add(2));
            order.add(1);
        });

        assertThat(order, contains(1, 2));
    }

    @Test
    void manyThreadsCanSubmitTasksConcurrently() throws InterruptedException {
        final int threadCount = 100;
        CountDownLatch completed = new CountDownLatch(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch barrier = new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    ready.countDown();
                    barrier.await();
                    executor.execute(() -> completed.countDown());
                } catch (Exception ex) {
                    throw new AssertionError("unexpected error", ex);
                }
            });
            t.start();
        }
        // wait for all the threads to have started
        ready.await();
        // release all the threads to submit their work to the executor.
        barrier.countDown();
        // all tasks should have completed. Note that all thread are racing with each other to
        // submit work so the order of work execution isn't important.
        completed.await();
    }
}
