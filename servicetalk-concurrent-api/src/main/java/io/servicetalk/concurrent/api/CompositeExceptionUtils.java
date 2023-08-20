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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class CompositeExceptionUtils {
    /**
     * Default to {@code 1} so {@link Throwable#addSuppressed(Throwable)} will not be used by default.
     */
    private static final int DEFAULT_MAX_EXCEPTIONS = 1;

    private CompositeExceptionUtils() {
    }

    static <T> Throwable addPendingError(AtomicReferenceFieldUpdater<T, Throwable> causeUpdater,
                                         AtomicIntegerFieldUpdater<T> countUpdater, T owner, int maxDelayedErrors,
                                         Throwable causeToAdd) {
        Throwable currPendingError = causeUpdater.get(owner);
        if (currPendingError == null) {
            if (causeUpdater.compareAndSet(owner, null, causeToAdd)) {
                currPendingError = causeToAdd;
            } else {
                currPendingError = causeUpdater.get(owner);
                assert currPendingError != null;
                addPendingError(countUpdater, owner, maxDelayedErrors, currPendingError, causeToAdd);
            }
        } else {
            addPendingError(countUpdater, owner, maxDelayedErrors, currPendingError, causeToAdd);
        }
        return currPendingError;
    }

    private static <T> void addPendingError(AtomicIntegerFieldUpdater<T> updater, T owner, int maxDelayedErrors,
                                            Throwable original, Throwable causeToAdd) {
        if (original == causeToAdd) {
            return;
        }
        for (;;) {
            final int size = updater.get(owner);
            if (size >= maxDelayedErrors) {
                break;
            } else if (updater.compareAndSet(owner, size, size + 1)) {
                original.addSuppressed(causeToAdd); // original is not equal to causeToAdd, safe to add suppressed.
                break;
            }
        }
    }

    static int maxDelayedErrors(boolean delayError) {
        return delayError ? DEFAULT_MAX_EXCEPTIONS : 0;
    }
}
