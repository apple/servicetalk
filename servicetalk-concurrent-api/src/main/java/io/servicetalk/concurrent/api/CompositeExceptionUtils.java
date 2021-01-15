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
package io.servicetalk.concurrent.api;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

final class CompositeExceptionUtils {
    /**
     * Default to {@code 1} so {@link Throwable#addSuppressed(Throwable)} will not be used by default.
     */
    private static final int DEFAULT_MAX_EXCEPTIONS = 1;

    private CompositeExceptionUtils() {
    }

    static <T> void addPendingError(AtomicIntegerFieldUpdater<T> updater, T owner, int maxDelayedErrors,
                                    Throwable original, Throwable causeToAdd) {
        // optimistically increment, recover after the fact if necessary.
        final int newSize = updater.incrementAndGet(owner);
        if (newSize < 0) {
            updater.set(owner, Integer.MAX_VALUE);
        } else if (newSize < maxDelayedErrors && original != causeToAdd) {
            original.addSuppressed(causeToAdd);
        } else {
            updater.decrementAndGet(owner);
        }
    }

    static int maxDelayedErrors(boolean delayError) {
        return delayError ? DEFAULT_MAX_EXCEPTIONS : 0;
    }
}
