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

import java.util.concurrent.atomic.AtomicLong;

import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtection;

/**
 * A {@link Subscription} that tracks requests and cancellation.
 */
public final class TestSubscription extends TestCancellable implements Subscription {

    private static final long INVALID_REQUEST_N = Long.MIN_VALUE;
    private final AtomicLong requested = new AtomicLong();
    private volatile boolean requestCalled;

    @Override
    public void request(final long n) {
        requestCalled = true;
        requested.accumulateAndGet(n, (x, y) -> {
            if (x < 0 || y < 0) {
                return INVALID_REQUEST_N;
            }
            return addWithOverflowProtection(x, y);
        });
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
     * Determine if {@link #request(long)} has been called or not.
     *
     * @return {@code true} if {@link #request(long)} has been called or not.
     */
    public boolean isRequested() {
        return requestCalled;
    }

    /**
     * Wait until the {@link Subscription#request(long)} amount exceeds {@code amount}.
     *
     * @param amount the amount to wait for.
     * @throws InterruptedException If this thread is interrupted while waiting.
     */
    public void awaitRequestN(long amount) throws InterruptedException {
        synchronized (waitingLock) {
            for (;;) {
                long r = requested.get();
                if (r == INVALID_REQUEST_N || r >= amount) {
                    // requested is not going to change now.
                    return;
                }
                waitingLock.wait();
            }
        }
    }

    /**
     * Wait until the {@link Subscription#request(long)} amount exceeds {@code amount} without being interrupted. This
     * method catches an {@link InterruptedException} and discards it silently.
     *
     * @param amount the amount to wait for.
     */
    public void awaitRequestNUninterruptibly(long amount) {
        boolean interrupted = false;
        synchronized (waitingLock) {
            for (;;) {
                long r = requested.get();
                if (r == INVALID_REQUEST_N || r >= amount) {
                    // requested is not going to change now.
                    break;
                }
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
}
