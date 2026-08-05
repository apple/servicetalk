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
package io.servicetalk.concurrent.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockingUtilsTest {

    @Test
    void singleReturnsValue() throws Exception {
        assertThat(BlockingUtils.blockingInvocation(Single.succeeded("foo")), is("foo"));
    }

    @Test
    void singleThrowsCauseDirectly() {
        IllegalStateException cause = new IllegalStateException("deliberate");
        assertThat(assertThrows(IllegalStateException.class,
                () -> BlockingUtils.blockingInvocation(Single.failed(cause))), sameInstance(cause));
    }

    @Test
    void completableThrowsCauseDirectly() {
        IllegalStateException cause = new IllegalStateException("deliberate");
        assertThat(assertThrows(IllegalStateException.class,
                () -> BlockingUtils.blockingInvocation(Completable.failed(cause))), sameInstance(cause));
    }

    @Test
    void awaitTerminationCompletes() throws Exception {
        BlockingUtils.awaitTermination(Completable.completed());
    }

    @Test
    void awaitTerminationThrowsCauseDirectly() {
        IllegalStateException cause = new IllegalStateException("deliberate");
        assertThat(assertThrows(IllegalStateException.class,
                () -> BlockingUtils.awaitTermination(Completable.failed(cause))), sameInstance(cause));
    }

    @Test
    void singleRestoresInterruptFlagOnInterrupt() throws InterruptedException {
        final InterruptOutcome outcome = runInterrupted(subscribed ->
                BlockingUtils.blockingInvocation(Single.never().afterOnSubscribe(c -> subscribed.countDown())));
        assertThat(outcome.thrown, is(instanceOf(InterruptedException.class)));
        assertThat("interrupt flag was not restored", outcome.interruptFlagRestored, is(true));
    }

    @Test
    void completableRestoresInterruptFlagOnInterrupt() throws InterruptedException {
        final InterruptOutcome outcome = runInterrupted(subscribed ->
                BlockingUtils.blockingInvocation(Completable.never().afterOnSubscribe(c -> subscribed.countDown())));
        assertThat(outcome.thrown, is(instanceOf(InterruptedException.class)));
        assertThat("interrupt flag was not restored", outcome.interruptFlagRestored, is(true));
    }

    @Test
    void singleCancelsSourceOnInterrupt() throws InterruptedException {
        final CountDownLatch cancelled = new CountDownLatch(1);
        final InterruptOutcome outcome = runInterrupted(subscribed ->
                BlockingUtils.blockingInvocation(Single.never()
                        .afterCancel(cancelled::countDown)
                        .afterOnSubscribe(c -> subscribed.countDown())));
        assertThat(outcome.thrown, is(instanceOf(InterruptedException.class)));
        cancelled.await();
    }

    @Test
    void completableCancelsSourceOnInterrupt() throws InterruptedException {
        final CountDownLatch cancelled = new CountDownLatch(1);
        final InterruptOutcome outcome = runInterrupted(subscribed ->
                BlockingUtils.blockingInvocation(Completable.never()
                        .afterCancel(cancelled::countDown)
                        .afterOnSubscribe(c -> subscribed.countDown())));
        assertThat(outcome.thrown, is(instanceOf(InterruptedException.class)));
        cancelled.await();
    }

    @Test
    void futureGetCancelsSourceOnInterrupt() throws InterruptedException {
        final CountDownLatch cancelled = new CountDownLatch(1);
        // toFuture() subscribes eagerly, so the source is already subscribed before futureGetCancelOnInterrupt blocks.
        final Future<?> future = Single.never().afterCancel(cancelled::countDown).toFuture();
        final InterruptOutcome outcome = runInterrupted(subscribed -> {
            subscribed.countDown();
            BlockingUtils.futureGetCancelOnInterrupt(future);
        });
        assertThat(outcome.thrown, is(instanceOf(InterruptedException.class)));
        cancelled.await();
    }

    @Test
    void awaitTerminationRestoresInterruptFlagButDoesNotCancel() throws InterruptedException {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final InterruptOutcome outcome = runInterrupted(subscribed ->
                BlockingUtils.awaitTermination(Completable.never()
                        .afterCancel(() -> cancelled.set(true))
                        .afterOnSubscribe(c -> subscribed.countDown())));
        assertThat(outcome.thrown, is(instanceOf(InterruptedException.class)));
        assertThat("interrupt flag was not restored", outcome.interruptFlagRestored, is(true));
        assertThat("close source must not be cancelled on interrupt", cancelled.get(), is(false));
    }

    /**
     * Runs {@code blockingCall} on a dedicated thread, interrupts it once it has subscribed, and captures the throwable
     * it propagated together with the thread's interrupt status observed immediately after the throw. Interrupting only
     * after subscription (rather than a timing-based sleep) makes the interrupt deterministic: even if it lands before
     * {@link Future#get()} is reached, {@code get()} observes the set flag on entry and throws.
     */
    private static InterruptOutcome runInterrupted(final InterruptibleInvocation blockingCall)
            throws InterruptedException {
        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final AtomicBoolean interruptFlagRestored = new AtomicBoolean();

        final Thread t = new Thread(() -> {
            try {
                blockingCall.invoke(subscribed);
            } catch (Throwable e) {
                thrown.set(e);
                // Must be captured before any further blocking call clears the flag again.
                interruptFlagRestored.set(Thread.currentThread().isInterrupted());
            } finally {
                done.countDown();
            }
        });
        t.start();

        subscribed.await();
        t.interrupt();
        done.await();
        t.join();

        assertThat("blocking call did not propagate a throwable", thrown.get(), is(notNullValue()));
        return new InterruptOutcome(thrown.get(), interruptFlagRestored.get());
    }

    private static final class InterruptOutcome {
        private final Throwable thrown;
        private final boolean interruptFlagRestored;

        private InterruptOutcome(final Throwable thrown, final boolean interruptFlagRestored) {
            this.thrown = thrown;
            this.interruptFlagRestored = interruptFlagRestored;
        }
    }

    @FunctionalInterface
    private interface InterruptibleInvocation {
        void invoke(CountDownLatch onSubscribed) throws Exception;
    }
}
