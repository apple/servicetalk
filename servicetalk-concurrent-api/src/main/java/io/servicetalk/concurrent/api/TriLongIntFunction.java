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

/**
 * A special function that takes a {@code long}, an {@code int} and a custom argument and returns the result.
 *
 * @param <T> The third argument to this function.
 * @param <R> Result of this function.
 */
@FunctionalInterface
public interface TriLongIntFunction<T, R> {

    /**
     * Evaluates this function on the given arguments.
     *
     * @param a The first {@code long} argument.
     * @param b The second {@code int} argument.
     * @param t The third {@link T} argument.
     * @return Result {@link R} of this function.
     */
    R apply(long a, int b, T t);
}
