/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ThreadInterruptingCancellableTest {

    @AfterEach
    void clearInterrupt() {
        // A leaked interrupt would otherwise bleed into the next test sharing this thread.
        Thread.interrupted();
    }

    @Test
    void cancelRacingWithSuccessfulCompletionLeaksNoInterrupt() throws Exception {
        final Thread boundThread = Thread.currentThread();
        final CountDownLatch atCheckpoint = new CountDownLatch(1);
        final CountDownLatch completionDone = new CountDownLatch(1);

        // The checkpoint forces cancel() to observe the bound thread, then pause until the operation has already
        // completed via setDone(), so the interrupt is delivered strictly inside the completion window.
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(boundThread) {
            @Override
            void beforeInterrupt() {
                atCheckpoint.countDown();
                try {
                    completionDone.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        final Thread canceller = new Thread(cancellable::cancel);
        canceller.start();

        atCheckpoint.await();
        cancellable.setDone();
        completionDone.countDown();

        // The interrupt lands on this (bound) thread; spin rather than join() so an interruptible wait does not
        // consume the very flag under test.
        while (canceller.isAlive()) {
            Thread.yield();
        }

        assertThat("cancel() leaked an interrupt after successful completion",
                boundThread.isInterrupted(), is(false));
    }

    @Test
    void cancelLeaksInterruptOntoNextPooledTask() throws Exception {
        // A single-threaded pool stands in for a ServiceTalk offload pool: tasks run FIFO on one reused thread.
        // cancel() claims the interrupt but stalls (via the checkpoint) until after the operation completes and the
        // pool thread has picked up an unrelated follow-up task; the late interrupt() then lands on that task.
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            final AtomicReference<ThreadInterruptingCancellable> ref = new AtomicReference<>();
            final CountDownLatch ticReady = new CountDownLatch(1);
            final CountDownLatch atCheckpoint = new CountDownLatch(1);
            final CountDownLatch releaseInterrupt = new CountDownLatch(1);
            final CountDownLatch nextTaskRunning = new CountDownLatch(1);
            final CountDownLatch nextTaskDone = new CountDownLatch(1);
            final CountDownLatch neverSignaled = new CountDownLatch(1);
            final AtomicBoolean nextTaskInterrupted = new AtomicBoolean();

            // The cancelled operation, running on the pool thread.
            pool.execute(() -> {
                final ThreadInterruptingCancellable tic =
                        new ThreadInterruptingCancellable(Thread.currentThread()) {
                            @Override
                            void beforeInterrupt() {
                                atCheckpoint.countDown();
                                await(releaseInterrupt);
                            }
                        };
                ref.set(tic);
                ticReady.countDown();
                await(atCheckpoint);   // cancel() has claimed the bound thread and is stalled before interrupt()
                tic.setDone();         // operation completes normally
            });

            // The next, unrelated task on the same pool thread.
            pool.execute(() -> {
                nextTaskRunning.countDown();
                try {
                    neverSignaled.await(10, SECONDS);
                } catch (InterruptedException e) {
                    nextTaskInterrupted.set(true);
                }
                nextTaskDone.countDown();
            });

            ticReady.await();
            final Thread canceller = new Thread(() -> ref.get().cancel());
            canceller.start();

            nextTaskRunning.await();       // the follow-up task now owns the pool thread
            releaseInterrupt.countDown();  // let the stalled cancel() deliver its interrupt
            canceller.join();
            nextTaskDone.await();

            assertThat("cancel() leaked an interrupt onto an unrelated pooled task",
                    nextTaskInterrupted.get(), is(false));
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
