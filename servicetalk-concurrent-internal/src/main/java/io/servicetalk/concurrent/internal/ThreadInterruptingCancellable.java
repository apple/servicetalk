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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.lang.Thread.interrupted;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Cancellable} that will {@link Thread#interrupt() interrupt a thread}.
 * <p>
 * It is important that {@link #setDone()} (or {@link #setDone(Throwable)}) is called after the associated blocking
 * operation completes to avoid "spurious" thread interrupts.
 */
public final class ThreadInterruptingCancellable implements Cancellable {
    private static final AtomicReferenceFieldUpdater<ThreadInterruptingCancellable, Object> threadUpdater =
            AtomicReferenceFieldUpdater.newUpdater(ThreadInterruptingCancellable.class, Object.class, "thread");
    private static final Object CANCELLED = new Object();
    private static final Object DONE = new Object();
    @Nullable
    private volatile Object thread;

    /**
     * Create a new instance.
     * @param threadToInterrupt The thread {@link Thread#interrupt() interrupt} in {@link #cancel()}.
     */
    public ThreadInterruptingCancellable(Thread threadToInterrupt) {
        // thread is not final, so it is possible for the constructor to exit and this object to be used before
        // thread state is initialized. To make this code safe we atomically set only if null.
        // https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.5
        if (!threadUpdater.compareAndSet(this, null, requireNonNull(threadToInterrupt))) {
            handleInitFail(threadToInterrupt);
        }
    }

    private void handleInitFail(Thread threadToInterrupt) {
        if (thread == CANCELLED) {
            threadToInterrupt.interrupt();
        }
    }

    @Override
    public void cancel() {
        final Object currThread = threadUpdater.getAndAccumulate(this, CANCELLED,
                (prev, x) -> prev == DONE ? DONE : CANCELLED);
        if (currThread instanceof Thread) {
            ((Thread) currThread).interrupt();
        }
    }

    /**
     * Indicates the operation associated with this {@link Cancellable} is done and future calls to {@link #cancel()}
     * should be NOOPs.
     */
    public void setDone() {
        thread = DONE;
    }

    /**
     * Indicates the operation associated with this {@link Cancellable} is done and future calls to {@link #cancel()}
     * should be NOOPs.
     *
     * @param cause The operation failed, and this is the {@link Throwable} that indicates why. If this is
     * {@link InterruptedException} then {@link Thread#interrupted()} will be called for the current thread to clear
     * the interrupt status.
     */
    public void setDone(Throwable cause) {
        setDone();
        if (cause instanceof InterruptedException) {
            interrupted();
        }
    }
}
