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
                final Throwable result = (Throwable) first;
                result.addSuppressed((Throwable) second);
                return result;
            } else {
                return (Throwable) first;
            }
        } else if (second instanceof Throwable) {
            return (Throwable) second;
        } else {
            return null;
        }
    }
}
