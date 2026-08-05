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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void singlePreservesInterruptAndCancels() {
        AtomicBoolean cancelled = new AtomicBoolean();
        Single<String> source = Single.<String>never().whenCancel(() -> cancelled.set(true));
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> BlockingUtils.blockingInvocation(source));
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt status should be preserved");
            assertTrue(cancelled.get(), "source should be cancelled on interrupt");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void completableThrowsCauseDirectly() {
        IllegalStateException cause = new IllegalStateException("deliberate");
        assertThat(assertThrows(IllegalStateException.class,
                () -> BlockingUtils.blockingInvocation(Completable.failed(cause))), sameInstance(cause));
    }

    @Test
    void completablePreservesInterruptAndCancels() {
        AtomicBoolean cancelled = new AtomicBoolean();
        Completable source = Completable.never().whenCancel(() -> cancelled.set(true));
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> BlockingUtils.blockingInvocation(source));
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt status should be preserved");
            assertTrue(cancelled.get(), "source should be cancelled on interrupt");
        } finally {
            Thread.interrupted();
        }
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
    void awaitTerminationPreservesInterruptButDoesNotCancel() {
        AtomicBoolean cancelled = new AtomicBoolean();
        Completable source = Completable.never().whenCancel(() -> cancelled.set(true));
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> BlockingUtils.awaitTermination(source));
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt status should be preserved");
            assertFalse(cancelled.get(), "close source must not be cancelled on interrupt");
        } finally {
            Thread.interrupted();
        }
    }
}
