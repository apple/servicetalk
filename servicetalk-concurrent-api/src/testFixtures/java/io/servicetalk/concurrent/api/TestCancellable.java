/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;

/**
 * A {@link Cancellable} that tracks cancellation.
 */
public class TestCancellable implements Cancellable {

    private volatile boolean cancelled;
    final Object waitingLock = new Object();

    @Override
    public final void cancel() {
        cancelled = true;
        wakeupWaiters();
    }

    /**
     * Returns {@code true} if {@link #cancel()} has been called, {@code false} otherwise.
     *
     * @return {@code true} if {@link #cancel()} has been called, {@code false} otherwise.
     */
    public final boolean isCancelled() {
        return cancelled;
    }

    /**
     * Wait until {@link #cancel()} is called.
     *
     * @throws InterruptedException If this thread is interrupted while waiting.
     */
    public final void awaitCancelled() throws InterruptedException {
        synchronized (waitingLock) {
            while (!cancelled) {
                waitingLock.wait();
            }
        }
    }

    /**
     * Wait until {@link #cancel()} is called without being interrupted. This method catches an
     * {@link InterruptedException} and discards it silently.
     * @deprecated Use {@link #awaitCancelled()} instead.
     */
    @Deprecated
    public final void awaitCancelledUninterruptibly() {
        boolean interrupted = false;
        synchronized (waitingLock) {
            while (!cancelled) {
                try {
                    waitingLock.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    final void wakeupWaiters() {
        synchronized (waitingLock) {
            waitingLock.notifyAll();
        }
    }
}
