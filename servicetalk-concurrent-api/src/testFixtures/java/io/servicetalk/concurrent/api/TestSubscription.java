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
public final class TestSubscription extends TestCancellable implements Subscription {

    private final AtomicLong requested = new AtomicLong();

    @Override
    public void request(final long n) {
        requested.accumulateAndGet(n, FlowControlUtil::addWithOverflowProtection);
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
     * Wait until the {@link Subscription#request(long)} amount exceeds {@code amount}.
     *
     * @param amount the amount to wait for.
     * @throws InterruptedException If this thread is interrupted while waiting.
     */
    public void awaitRequestN(long amount) throws InterruptedException {
        synchronized (waitingLock) {
            while (requested.get() < amount) {
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
            while (requested.get() < amount) {
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
