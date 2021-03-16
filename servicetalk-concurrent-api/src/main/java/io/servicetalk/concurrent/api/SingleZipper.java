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

import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static java.util.function.Function.identity;

final class SingleZipper {
    private SingleZipper() {
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, R> Single<R> zip(Single<? extends T1> s1, Single<? extends T2> s2,
                                     BiFunction<? super T1, ? super T2, ? extends R> zipper) {
        return from(s1.map(v -> new ZipArg(0, v)), s2.map(v -> new ZipArg(1, v)))
                .flatMapMergeSingle(identity(), 2)
                .collect(() -> new Object[2], (array, zipArg) -> {
                    array[zipArg.index] = zipArg.value;
                    return array;
                }).map(array -> zipper.apply((T1) array[0], (T2) array[1]));
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, T3, R> Single<R> zip(Single<? extends T1> s1, Single<? extends T2> s2, Single<? extends T3> s3,
                                         Function3<? super T1, ? super T2, ? super T3, ? extends R> zipper) {
        return from(s1.map(v -> new ZipArg(0, v)), s2.map(v -> new ZipArg(1, v)), s3.map(v -> new ZipArg(2, v)))
                .flatMapMergeSingle(identity(), 3)
                .collect(() -> new Object[3], (array, zipArg) -> {
                    array[zipArg.index] = zipArg.value;
                    return array;
                }).map(array -> zipper.apply((T1) array[0], (T2) array[1], (T3) array[2]));
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, T3, T4, R> Single<R> zip(
            Single<? extends T1> s1, Single<? extends T2> s2, Single<? extends T3> s3, Single<? extends T4> s4,
            Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> zipper) {
        return from(s1.map(v -> new ZipArg(0, v)), s2.map(v -> new ZipArg(1, v)),
                    s3.map(v -> new ZipArg(2, v)), s4.map(v -> new ZipArg(3, v)))
                .flatMapMergeSingle(identity(), 4)
                .collect(() -> new Object[4], (array, zipArg) -> {
                    array[zipArg.index] = zipArg.value;
                    return array;
                }).map(array -> zipper.apply((T1) array[0], (T2) array[1], (T3) array[2], (T4) array[3]));
    }

    static <R> Single<R> zip(Function<? super Object[], ? extends R> zipper, Single<?>... singles) {
        @SuppressWarnings("unchecked")
        Single<ZipArg>[] mappedSingles = new Single[singles.length];
        for (int i = 0; i < singles.length; ++i) {
            final int finalI = i;
            mappedSingles[i] = singles[i].map(v -> new ZipArg(finalI, v));
        }
        return from(mappedSingles)
                .flatMapMergeSingle(identity(), mappedSingles.length)
                .collect(() -> new Object[mappedSingles.length], (array, zipArg) -> {
                    array[zipArg.index] = zipArg.value;
                    return array;
                }).map(zipper);
    }

    private static final class ZipArg {
        private final int index;
        @Nullable
        private final Object value;

        private ZipArg(final int index, final Object value) {
            this.index = index;
            this.value = value;
        }
    }
}
