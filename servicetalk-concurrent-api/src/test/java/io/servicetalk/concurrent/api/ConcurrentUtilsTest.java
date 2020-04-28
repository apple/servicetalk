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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ConcurrentUtilsTest {
    private static final AtomicIntegerFieldUpdater<ConcurrentUtilsTest> lockUpdater =
            AtomicIntegerFieldUpdater.newUpdater(ConcurrentUtilsTest.class, "lock");
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @SuppressWarnings("unused")
    private volatile int lock;
    private ExecutorService executor;

    @Before
    public void setUp() {
        executor = newCachedThreadPool();
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @Test
    public void singleThread() {
        assertTrue(tryAcquireLock(lockUpdater, this));
        assertTrue(releaseLock(lockUpdater, this));
    }

    @Test
    public void pendingFromDifferentThread() throws Exception {
        assertTrue(tryAcquireLock(lockUpdater, this));
        executor.submit(() -> assertFalse(tryAcquireLock(lockUpdater, this))).get();

        // we expect false because we are expected to re-acquire and release the lock. This is a feature of the lock
        // that requires checking the condition protected by the lock again.
        assertFalse(releaseLock(lockUpdater, this));

        assertTrue(tryAcquireLock(lockUpdater, this));
        assertTrue(releaseLock(lockUpdater, this));
    }

    @Test
    public void pendingFromDifferentThreadReAcquireFromDifferentThread() throws Exception {
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
}
