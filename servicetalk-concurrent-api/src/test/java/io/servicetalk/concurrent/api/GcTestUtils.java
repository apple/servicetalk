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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertSame;

final class GcTestUtils {
    private static final int MAX_GC_ATTEMPTS = 10;
    private static final long QUEUE_WAIT_MILLIS = 1_000;

    private GcTestUtils() {
        // No instances.
    }

    static <T> void assertEventuallyEnqueued(Object keepAlive, WeakReference<T> expected,
                                             ReferenceQueue<T> queue) throws InterruptedException {
        Reference<? extends T> actual = null;
        for (int i = 0; i < MAX_GC_ATTEMPTS && actual == null; ++i) {
            System.gc();
            actual = queue.remove(QUEUE_WAIT_MILLIS);
        }
        assertSame(expected, actual, "Weak reference was not enqueued after " + MAX_GC_ATTEMPTS +
                " GC attempts; referent was " + (expected.get() == null ? "cleared" : "still reachable") +
                "; parent: " + keepAlive);
    }
}
