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

/**
 * Utility for creating static {@link Throwable}s.
 */
public final class ThrowableUtil {

    private ThrowableUtil() { }

    /**
     * Set the {@link StackTraceElement} for the given {@link Throwable}, using the {@link Class} and method name.
     * @param cause The cause to initialize.
     * @param clazz The class where the {@code cause} is thrown from.
     * @param <T> The type of {@link Throwable}.
     * @param method The method where the {@code cause} is thrown from.
     * @return {@code cause} after the stack trace has been initialized.
     */
    public static <T extends Throwable> T unknownStackTrace(T cause, Class<?> clazz, String method) {
        cause.setStackTrace(new StackTraceElement[] {new StackTraceElement(clazz.getName(), method, null, -1)});
        return cause;
    }

    /**
     * Finds if the passed {@code original} or any of its causes are an instance of {@code toMatch}.
     *
     * @param original {@link Throwable} to search.
     * @param toMatch {@link Throwable} to find in {@code original}.
     *
     * @return {@code true} if passed {@code original} or any of its causes are an instance of {@code toMatch}.
     */
    public static boolean matches(Throwable original, Class<? extends Throwable> toMatch) {
        if (original.getClass().isAssignableFrom(toMatch)) {
            return true;
        }
        Throwable lhs = original.getCause();
        while (lhs != null) {
            if (lhs.getClass().isAssignableFrom(toMatch)) {
                return true;
            }
            lhs = lhs.getCause();
        }
        return false;
    }
}
