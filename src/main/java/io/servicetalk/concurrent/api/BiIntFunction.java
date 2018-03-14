/**
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
package io.servicetalk.concurrent.api;

/**
 * A special function that takes an {@code int} and a custom argument and returns the result.
 *
 * @param <T> The other argument to this function.
 * @param <R> Result of this function.
 */
@FunctionalInterface
public interface BiIntFunction<T, R> {

    /**
     * Evaluates this function on the given arguments.
     *
     * @param i The {@code int} argument.
     * @param t The {@link T} argument.
     * @return Result {@link R} of this function.
     */
    R apply(int i, T t);
}
