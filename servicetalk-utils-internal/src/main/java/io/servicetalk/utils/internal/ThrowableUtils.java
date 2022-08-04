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
package io.servicetalk.utils.internal;

import javax.annotation.Nullable;

/**
 * Utilities for working with {@link Throwable}s.
 */
public final class ThrowableUtils {

    private ThrowableUtils() {
        // No instances
    }

    /**
     * Raises an exception bypassing compiler checks for checked exceptions.
     *
     * @param t The {@link Throwable} to throw.
     * @param <T> The expected type
     * @return nothing actually will be returned from this method because it rethrows the specified exception. Making
     * this method return an arbitrary type makes the caller method easier as they do not have to add a return statement
     * after calling this method.
     */
    public static <T> T throwException(final Throwable t) {
        return PlatformDependent0.throwException(t);
    }

    /**
     * Combine two potential {@link Throwable}s into one.
     * If both parameters are {@link Throwable}, the {@code second} one will be
     * {@link Throwable#addSuppressed(Throwable) suppressed} by the {@code first} one.
     *
     * @param first the first argument that can be {@link Throwable}.
     * @param second the second argument that can be {@link Throwable}.
     * @return combined {@link Throwable}.
     */
    @Nullable
    public static Throwable combine(@Nullable final Object first, @Nullable final Object second) {
        if (first instanceof Throwable) {
            if (second instanceof Throwable) {
                return addSuppressed((Throwable) first, (Throwable) second);
            } else {
                return (Throwable) first;
            }
        } else if (second instanceof Throwable) {
            return (Throwable) second;
        } else {
            return null;
        }
    }

    /**
     * Adds suppressed exception avoiding self-suppression.
     *
     * @param original the original {@link Throwable}
     * @param suppressed the {@link Throwable} to be suppressed
     * @return the original {@link Throwable}
     */
    public static Throwable addSuppressed(final Throwable original, final Throwable suppressed) {
        if (original != suppressed) {
            original.addSuppressed(suppressed);
        }
        return original;
    }
}
