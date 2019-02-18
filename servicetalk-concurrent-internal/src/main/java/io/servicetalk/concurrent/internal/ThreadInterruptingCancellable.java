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

import io.servicetalk.concurrent.Cancellable;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.lang.Thread.interrupted;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Cancellable} that will {@link Thread#interrupt() interrupt a thread}.
 * <p>
 * It is important that {@link #done()} (or {@link #done(Throwable)}) is called after the associated blocking
 * operation completes to avoid "spurious" thread interrupts.
 */
public final class ThreadInterruptingCancellable implements Cancellable {
    private static final AtomicIntegerFieldUpdater<ThreadInterruptingCancellable> statusUpdater =
            AtomicIntegerFieldUpdater.newUpdater(ThreadInterruptingCancellable.class, "status");

    @SuppressWarnings("unused")
    private volatile int status;
    private final Thread threadToInterrupt;

    /**
     * Create a new instance.
     * @param threadToInterrupt The thread {@link Thread#interrupt() interrupt} in {@link #cancel()}.
     */
    public ThreadInterruptingCancellable(Thread threadToInterrupt) {
        this.threadToInterrupt = requireNonNull(threadToInterrupt);
    }

    @Override
    public void cancel() {
        if (statusUpdater.compareAndSet(this, 0, 1)) {
            threadToInterrupt.interrupt();
        }
    }

    /**
     * Indicates the operation associated with this {@link Cancellable} is done and future calls to {@link #cancel()}
     * should be NOOPs.
     */
    public void done() {
        status = 1;
    }

    /**
     * Indicates the operation associated with this {@link Cancellable} is done and future calls to {@link #cancel()}
     * should be NOOPs.
     *
     * @param cause The operation failed, and this is the {@link Throwable} that indicates why. If this is
     * {@link InterruptedException} then {@link Thread#interrupted()} will be called for the current thread to clear
     * the interrupt status.
     */
    public void done(Throwable cause) {
        done();
        if (cause instanceof InterruptedException) {
            interrupted();
        }
    }
}
