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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * A {@link Subscription} that tracks requests and cancellation.
 */
public final class TestSubscription implements Subscription {

    private final AtomicLong requested = new AtomicLong();
    private final AtomicReference<Thread> waitingThreadRef = new AtomicReference<>();
    private volatile boolean cancelled;

    @Override
    public void request(final long n) {
        requested.accumulateAndGet(n, FlowControlUtil::addWithOverflowProtection);
        Thread waitingThread = waitingThreadRef.get();
        if (waitingThread != null) {
            LockSupport.unpark(waitingThread);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
        Thread waitingThread = waitingThreadRef.get();
        if (waitingThread != null) {
            LockSupport.unpark(waitingThread);
        }
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
        if (!waitingThreadRef.compareAndSet(null, Thread.currentThread())) {
            throw new IllegalStateException("only a single waiter thread at a time is supported");
        }

        // Before we park we must check the condition to avoid deadlock, so no do/while.
        while (requested.get() < amount) {
            LockSupport.park();
        }

        waitingThreadRef.set(null);
    }

    /**
     * Wait until {@link #cancel()} is called.
     */
    public void waitUntilCancelled() {
        if (!waitingThreadRef.compareAndSet(null, Thread.currentThread())) {
            throw new IllegalStateException("only a single waiter thread at a time is supported");
        }

        // Before we park we must check the condition to avoid deadlock, so no do/while.
        while (!cancelled) {
            LockSupport.park();
        }

        waitingThreadRef.set(null);
    }
}
