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
package io.servicetalk.concurrent.internal;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static java.lang.Thread.currentThread;

/**
 * A set of utilities for interacting with {@link Future}.
 */
public final class FutureUtils {
    private static final Object DUMMY = new Object();

    private FutureUtils() {
        // No instances.
    }

    /**
     * Await the completion of the passed {@link Future}.
     *
     * @param future {@link Future} to await termination.
     */
    public static void awaitTermination(Future<Void> future) {
        awaitResult(future);
    }

    /**
     * Await the completion of the passed {@link Future} and return its result.
     *
     * @param future {@link Future} to await completion.
     * @param <T> the result type.
     * @return the result of the {@link Future}.
     */
    public static <T> T awaitResult(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            currentThread().interrupt(); // Reset the interrupted flag.
            throwException(e);
        } catch (ExecutionException e) {
            throwException(e.getCause());
        }

        return uncheckedCast(); // Used to fool the compiler, but actually should never be invoked at runtime.
    }

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast() {
        return (T) DUMMY;
    }
}
