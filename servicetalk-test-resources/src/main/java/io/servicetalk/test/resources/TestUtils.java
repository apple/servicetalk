/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.test.resources;

import java.util.Objects;
import java.util.Queue;

/**
 * Test utility methods / helpers.
 */
public final class TestUtils {

    private TestUtils() {
        // No instances
    }

    /**
     * Helper method to check if a given {@link Queue} contains any errors,
     * usually produced through async sources. For ALL {@link Throwable}s found in the queue,
     * a suppressed error will be captured and surface through a single {@link AssertionError}.
     * @param errors The queue of captured errors.
     */
    public static void assertNoAsyncErrors(final Queue<? extends Throwable> errors) {
        assertNoAsyncErrors("Async errors occurred. See suppressed!", errors);
    }

    /**
     * Helper method to check if a given {@link Queue} contains any errors,
     * usually produced through async sources. For ALL {@link Throwable}s found in the queue,
     * a suppressed error will be captured and surface through a single {@link AssertionError}.
     * @param message message for the AssertionError.
     * @param errors The queue of captured errors.
     */
    public static void assertNoAsyncErrors(final String message, final Queue<? extends Throwable> errors) {
        if (errors.isEmpty()) {
            return;
        }

        final AssertionError error = new AssertionError(Objects.requireNonNull(message, "message"));
        Throwable t;
        while ((t = errors.poll()) != null) {
            error.addSuppressed(t);
        }

        throw error;
    }
}
