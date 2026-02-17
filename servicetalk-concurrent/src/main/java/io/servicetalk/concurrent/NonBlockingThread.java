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
package io.servicetalk.concurrent;

/**
 * Marker interface for {@link Thread}s that must never be blocked (blocking operations not permitted),
 * like I/O threads.
 * <p>
 * Blocking operations known to ServiceTalk may throw {@link IllegalBlockingOperationException} if they are invoked on a
 * {@link Thread} that implements this interface.
 */
public interface NonBlockingThread {

    /**
     * This exception is thrown when a blocking operation is performed on a {@link NonBlockingThread}.
     */
    @SuppressWarnings("PMD.ConstantsInInterface")   // false positive
    final class IllegalBlockingOperationException extends IllegalStateException {
        private static final long serialVersionUID = 3300479887443202673L;

        /**
         * Creates a new instance.
         *
         * @param message description message
         */
        public IllegalBlockingOperationException(final String message) {
            super(message);
        }
    }
}
