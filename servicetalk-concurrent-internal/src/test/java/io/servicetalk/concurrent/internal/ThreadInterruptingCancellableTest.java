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
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ThreadInterruptingCancellableTest {

    @AfterEach
    void clearInterrupt() {
        // A leaked interrupt would otherwise bleed into the next test sharing this thread.
        Thread.interrupted();
    }

    @Test
    void cancelBeforeSetDoneClearsInterrupt() {
        final Thread boundThread = Thread.currentThread();
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(boundThread);

        cancellable.cancel();
        assertThat("cancel() did not interrupt the bound thread", boundThread.isInterrupted(), is(true));

        cancellable.setDone();
        assertThat("setDone() did not clear the interrupt delivered by cancel()",
                boundThread.isInterrupted(), is(false));
    }

    @Test
    void cancelBeforeSetDoneWithCauseClearsInterrupt() {
        final Thread boundThread = Thread.currentThread();
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(boundThread);

        cancellable.cancel();
        cancellable.setDone(new IllegalStateException());
        assertThat(boundThread.isInterrupted(), is(false));
    }

    @Test
    void setDoneLatchesOutLaterCancel() {
        final Thread boundThread = Thread.currentThread();
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(boundThread);

        cancellable.setDone();
        cancellable.cancel();
        assertThat("cancel() interrupted the bound thread after setDone()", boundThread.isInterrupted(), is(false));
    }

    @Test
    void setDoneWithInterruptedExceptionClearsInterrupt() {
        final Thread boundThread = Thread.currentThread();
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(boundThread);

        boundThread.interrupt();
        cancellable.setDone(new InterruptedException());
        assertThat(boundThread.isInterrupted(), is(false));
    }

    @Test
    @Timeout(30)
    void concurrentCancelAndSetDoneNeverLeakInterrupt() throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            final AtomicReference<ThreadInterruptingCancellable> ref = new AtomicReference<>();
            final CountDownLatch ready = new CountDownLatch(1);
            final AtomicBoolean go = new AtomicBoolean();
            final CountDownLatch done = new CountDownLatch(1);
            final AtomicBoolean leaked = new AtomicBoolean();

            final Thread bound = new Thread(() -> {
                final ThreadInterruptingCancellable tic =
                        new ThreadInterruptingCancellable(Thread.currentThread());
                ref.set(tic);
                ready.countDown();
                // Non-interruptible spin so the start barrier can't consume the interrupt under test.
                while (!go.get()) {
                    Thread.yield();
                }
                tic.setDone();
                leaked.set(Thread.currentThread().isInterrupted());
                done.countDown();
            });
            bound.start();

            ready.await();
            go.set(true);
            ref.get().cancel();   // races setDone() on the bound thread
            done.await();
            bound.join();

            assertThat("iteration " + i + ": setDone() left a leaked interrupt after a racing cancel()",
                    leaked.get(), is(false));
        }
    }
}
