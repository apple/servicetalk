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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.internal.BlockingUtils.blockingInvocation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class BlockingUtilsTest {

    @Test
    void singleRestoresInterruptFlagOnInterrupt() throws InterruptedException {
        assertInterruptFlagRestored(subscribed ->
                blockingInvocation(Single.never().afterOnSubscribe(cancellable -> subscribed.countDown())));
    }

    @Test
    void completableRestoresInterruptFlagOnInterrupt() throws InterruptedException {
        assertInterruptFlagRestored(subscribed ->
                blockingInvocation(Completable.never().afterOnSubscribe(cancellable -> subscribed.countDown())));
    }

    /**
     * Runs {@code blockingCall} on a dedicated thread, interrupts it once it has subscribed, and asserts that the
     * helper both propagates the {@link InterruptedException} and restores the thread's interrupt flag before returning
     * to the caller. Prior to the fix the flag was silently cleared by the JVM and never restored.
     */
    private static void assertInterruptFlagRestored(final InterruptibleInvocation blockingCall)
            throws InterruptedException {
        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final AtomicBoolean interruptedAfterThrow = new AtomicBoolean();

        final Thread t = new Thread(() -> {
            try {
                blockingCall.invoke(subscribed);
            } catch (Throwable e) {
                thrown.set(e);
                // Must be captured before any further blocking call clears the flag again.
                interruptedAfterThrow.set(Thread.currentThread().isInterrupted());
            } finally {
                done.countDown();
            }
        });
        t.start();

        // Wait until the worker has subscribed (and is about to block in Future.get()) before delivering the
        // interrupt, rather than relying on a timing-based sleep. Even if the interrupt lands before the worker
        // reaches Future.get(), get() observes the set flag on entry and throws InterruptedException.
        subscribed.await();
        t.interrupt();
        done.await();
        t.join();

        assertThat("blocking call did not propagate a throwable", thrown.get(), is(notNullValue()));
        assertThat("interrupt not propagated as InterruptedException", thrown.get(),
                is(instanceOf(InterruptedException.class)));
        assertThat("interrupt flag was not restored after InterruptedException", interruptedAfterThrow.get(), is(true));
    }

    @FunctionalInterface
    private interface InterruptibleInvocation {
        void invoke(CountDownLatch onSubscribed) throws Exception;
    }
}
