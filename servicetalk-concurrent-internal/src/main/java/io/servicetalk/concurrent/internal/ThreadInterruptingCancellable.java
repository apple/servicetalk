/*
 * Copyright © 2018, 2026 Apple Inc. and the ServiceTalk project authors
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

import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.interrupted;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Cancellable} that will {@link Thread#interrupt() interrupt a thread}.
 * <p>
 * It is important that {@link #setDone()} (or {@link #setDone(Throwable)}) is called on the interrupted
 * thread after the associated blocking operation completes to avoid "spurious" thread interrupts.
 */
public final class ThreadInterruptingCancellable implements Cancellable {
    // cancel() moves the live Thread through INTERRUPTING (interrupt() in progress) to INTERRUPTED so a concurrent
    // setDone() can wait for the interrupt to actually fire before clearing it.
    private static final Object INTERRUPTING = new Object();
    private static final Object INTERRUPTED = new Object();
    private static final Object DONE = new Object();

    // final field: safely published, so the bound thread is visible even under unsafe publication.
    private final AtomicReference<Object> thread;

    /**
     * Create a new instance.
     * @param threadToInterrupt The thread {@link Thread#interrupt() interrupt} in {@link #cancel()}.
     */
    public ThreadInterruptingCancellable(Thread threadToInterrupt) {
        thread = new AtomicReference<>(requireNonNull(threadToInterrupt));
    }

    @Override
    public void cancel() {
        final Object current = thread.get();
        // If current is not a Thread the state is already INTERRUPTING/INTERRUPTED/DONE; if the CAS loses, a concurrent
        // cancel()/setDone() won and there is nothing left to do. Either way cancel() is a NOOP.
        if (current instanceof Thread && thread.compareAndSet(current, INTERRUPTING)) {
            try {
                ((Thread) current).interrupt();
            } finally {
                thread.set(INTERRUPTED);
            }
        }
    }

    /**
     * Indicates the operation associated with this {@link Cancellable} is done and future calls to {@link #cancel()}
     * should be NOOPs.
     */
    public void setDone() {
        final Object current = thread.get();
        if (current instanceof Thread && thread.compareAndSet(current, DONE)) {
            return;
        }
        clearRacingInterrupt();
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
        final Object current = thread.get();
        if (current instanceof Thread && thread.compareAndSet(current, DONE)) {
            if (cause instanceof InterruptedException) {
                interrupted();
            }
            return;
        }
        clearRacingInterrupt();
    }

    private void clearRacingInterrupt() {
        // A concurrent cancel() delivered (or is delivering) an interrupt. Busy-wait until interrupt() has been called
        // (INTERRUPTED) so it is captured, then clear it to avoid a spurious interrupt on this (bound) thread. A DONE
        // state means a prior setDone() already handled completion, so there is no cancel interrupt to clear.
        while (thread.get() == INTERRUPTING) {
            Thread.yield();
        }
        if (thread.get() == INTERRUPTED) {
            interrupted();
        }
    }
}
