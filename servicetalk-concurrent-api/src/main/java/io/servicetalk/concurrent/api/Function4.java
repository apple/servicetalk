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

import javax.annotation.Nullable;

/**
 * A functional interface that accepts 3 arguments and generates a return value.
 * @param <T1> The type of the first argument.
 * @param <T2> The type of the second argument.
 * @param <T3> The type of the third argument.
 * @param <T4> The type of the forth argument.
 * @param <R> The result of the function.
 */
@FunctionalInterface
public interface Function4<T1, T2, T3, T4, R> {
    /**
     * Applies the function to the given arguments.
     * @param t1 the first value.
     * @param t2 the second value.
     * @param t3 the third value.
     * @param t4 the fourth value.
     * @return the result value.
     */
    @Nullable
    R apply(@Nullable T1 t1, @Nullable T2 t2, @Nullable T3 t3, @Nullable T4 t4);
}
