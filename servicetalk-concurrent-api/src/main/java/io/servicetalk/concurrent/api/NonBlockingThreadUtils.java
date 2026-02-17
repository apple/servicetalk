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

import io.servicetalk.concurrent.NonBlockingThread;
import io.servicetalk.concurrent.NonBlockingThread.IllegalBlockingOperationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Boolean.getBoolean;

final class NonBlockingThreadUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NonBlockingThreadUtils.class);

    private static final String WARN_ON_NON_BLOCKING_THREAD_PROPERTY =
            "io.servicetalk.concurrent.api.warnOnNonBlockingThread";
    private static final boolean WARN_ON_NON_BLOCKING_THREAD = getBoolean(WARN_ON_NON_BLOCKING_THREAD_PROPERTY);

    static {
        if (WARN_ON_NON_BLOCKING_THREAD) {
            LOGGER.warn("-D{}: {}. Setting this property to 'true' may be used temporarily to unblock deployment but " +
                            "exposes the service to the risk of deadlocks or increased I/O latencies which can be " +
                            "very difficult to diagnose.",
                    WARN_ON_NON_BLOCKING_THREAD_PROPERTY, WARN_ON_NON_BLOCKING_THREAD);
        } else {
            LOGGER.debug("-D{}: {}", WARN_ON_NON_BLOCKING_THREAD_PROPERTY, WARN_ON_NON_BLOCKING_THREAD);
        }
    }

    private NonBlockingThreadUtils() {
        // No instances
    }

    static void checkNonBlockingThread(final boolean done) {
        if (done) {
            return;
        }
        final Thread thread = Thread.currentThread();
        if (thread instanceof NonBlockingThread) {
            final IllegalBlockingOperationException e = new IllegalBlockingOperationException(
                    "Not allowed to block a NonBlockingThread: " + thread);
            if (WARN_ON_NON_BLOCKING_THREAD) {
                LOGGER.warn("Not allowed to block a NonBlockingThread: {}", thread, e);
            } else {
                throw e;
            }
        }
    }
}
