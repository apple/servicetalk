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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.FlowControlUtil;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Subscription} that tracks requests and cancellation.
 */
public final class TestSubscription implements Subscription {

    private final AtomicLong requested = new AtomicLong();
    private final Object waitingLock = new Object();
    private volatile boolean cancelled;

    @Override
    public void request(final long n) {
        requested.accumulateAndGet(n, FlowControlUtil::addWithOverflowProtection);
        wakeupWaiters();
    }

    @Override
    public void cancel() {
        cancelled = true;
        wakeupWaiters();
    }

    /**
     * Returns the cumulative total of {@code n} from {@link #request(long)}s.
     *
     * @return the cumulative total of {@code n} from {@link #request(long)}s.
     */
    public long requested() {
        return requested.get();
    }

    /**
     * Returns {@code true} if {@link #cancel()} has been called, {@code false} otherwise.
     *
     * @return {@code true} if {@link #cancel()} has been called, {@code false} otherwise.
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Wait until the {@link Subscription#request(long)} amount exceeds {@code amount}.
     *
     * @param amount the amount to wait for.
     */
    public void waitUntilRequested(long amount) {
        if (requested.get() >= amount) {
            return;
        }

        boolean interrupted = false;
        do {
            synchronized (waitingLock) {
                try {
                    waitingLock.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } while (requested.get() < amount);

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait until {@link #cancel()} is called.
     */
    public void waitUntilCancelled() {
        if (cancelled) {
            return;
        }

        boolean interrupted = false;
        do {
            synchronized (waitingLock) {
                try {
                    waitingLock.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } while (!cancelled);

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void wakeupWaiters() {
        synchronized (waitingLock) {
            waitingLock.notifyAll();
        }
    }
}
