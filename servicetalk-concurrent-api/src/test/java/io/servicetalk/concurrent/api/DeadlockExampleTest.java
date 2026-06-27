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
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.SECONDS;

final class DeadlockExampleTest {
    private final ReentrantLock lockA = new ReentrantLock();
    private final ReentrantLock lockB = new ReentrantLock();

    @Test
    @Timeout(value = 3, unit = SECONDS)
    void deadlock() throws InterruptedException {
        // Both threads grab their first lock, rendezvous, then try to grab the other's lock -> deadlock.
        final CountDownLatch firstLocksHeld = new CountDownLatch(2);

        Thread other = new Thread(() -> {
            lockB.lock();
            try {
                firstLocksHeld.countDown();
                firstLocksHeld.await();
                lockA.lockInterruptibly(); // blocks: the test thread holds lockA
                lockA.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lockB.unlock();
            }
        }, "deadlock-other");
        other.setDaemon(true);
        other.start();

        lockA.lock();
        try {
            firstLocksHeld.countDown();
            firstLocksHeld.await();
            lockB.lockInterruptibly(); // blocks: "deadlock-other" holds lockB -> deadlock
            lockB.unlock();
        } finally {
            lockA.unlock();
        }
    }
}
