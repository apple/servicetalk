/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseReentrantLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireReentrantLock;
import static io.servicetalk.concurrent.internal.TimeoutTracingInfoExtension.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ConcurrentUtilsTest {
    private static final AtomicIntegerFieldUpdater<ConcurrentUtilsTest> lockUpdater =
            AtomicIntegerFieldUpdater.newUpdater(ConcurrentUtilsTest.class, "lock");
    private static final AtomicLongFieldUpdater<ConcurrentUtilsTest> reentrantLockUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentUtilsTest.class, "reentrantLock");
    @SuppressWarnings("unused")
    private volatile int lock;
    @SuppressWarnings("unused")
    private volatile long reentrantLock;
    private ExecutorService executor;

    @BeforeEach
    public void setUp() {
        executor = newCachedThreadPool();
    }

    @AfterEach
    public void tearDown() throws Exception {
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @Test
    public void lockSingleThread() {
        assertTrue(tryAcquireLock(lockUpdater, this));
        assertTrue(releaseLock(lockUpdater, this));
    }

    @Test
    public void reentrantLockSingleThread() {
        long acquireId = tryAcquireReentrantLock(reentrantLockUpdater, this);
        assertThat(acquireId, greaterThan(0L));
        long acquireId2 = tryAcquireReentrantLock(reentrantLockUpdater, this);
        assertThat(acquireId2, is(-acquireId));
        assertTrue(releaseReentrantLock(reentrantLockUpdater, acquireId2, this));
        assertTrue(releaseReentrantLock(reentrantLockUpdater, acquireId, this));
    }

    @Test
    public void lockFromDifferentThread() throws Exception {
        assertTrue(tryAcquireLock(lockUpdater, this));
        executor.submit(() -> assertFalse(tryAcquireLock(lockUpdater, this))).get();

        // we expect false because we are expected to re-acquire and release the lock. This is a feature of the lock
        // that requires checking the condition protected by the lock again.
        assertFalse(releaseLock(lockUpdater, this));

        assertTrue(tryAcquireLock(lockUpdater, this));
        assertTrue(releaseLock(lockUpdater, this));
    }

    @Test
    public void reentrantLockFromDifferentThread() throws Exception {
        long acquireId = tryAcquireReentrantLock(reentrantLockUpdater, this);
        assertThat(acquireId, greaterThan(0L));
        executor.submit(() -> assertThat(tryAcquireReentrantLock(reentrantLockUpdater, this), is(0L))).get();

        // we expect false because we are expected to re-acquire and release the lock. This is a feature of the lock
        // that requires checking the condition protected by the lock again.
        assertFalse(releaseReentrantLock(reentrantLockUpdater, acquireId, this));

        acquireId = tryAcquireReentrantLock(reentrantLockUpdater, this);
        assertThat(acquireId, greaterThan(0L));
        assertTrue(releaseReentrantLock(reentrantLockUpdater, acquireId, this));
    }

    @Test
    public void lockFromDifferentThreadReAcquireFromDifferentThread() throws Exception {
        assertTrue(tryAcquireLock(lockUpdater, this));
        executor.submit(() -> assertFalse(tryAcquireLock(lockUpdater, this))).get();

        // we expect false because we are expected to re-acquire and release the lock. This is a feature of the lock
        // that requires checking the condition protected by the lock again.
        assertFalse(releaseLock(lockUpdater, this));

        executor.submit(() -> {
            assertTrue(tryAcquireLock(lockUpdater, this));
            assertTrue(releaseLock(lockUpdater, this));
        }).get();
    }

    @Test
    public void reentrantLockFromDifferentThreadReAcquireFromDifferentThread() throws Exception {
        long acquireId = tryAcquireReentrantLock(reentrantLockUpdater, this);
        assertThat(acquireId, greaterThan(0L));
        executor.submit(() -> assertThat(tryAcquireReentrantLock(reentrantLockUpdater, this), is(0L))).get();

        // we expect false because we are expected to re-acquire and release the lock. This is a feature of the lock
        // that requires checking the condition protected by the lock again.
        assertFalse(releaseReentrantLock(reentrantLockUpdater, acquireId, this));

        executor.submit(() -> {
            long acquireId2 = tryAcquireReentrantLock(reentrantLockUpdater, this);
            assertThat(acquireId2, greaterThan(0L));
            assertTrue(releaseReentrantLock(reentrantLockUpdater, acquireId2, this));
        }).get();
    }
}
