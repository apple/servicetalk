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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;

final class SingleZipper {
    private static final Comparator<? super ZipArg> ZIP_ARG_COMPARATOR = comparingInt(o -> o.index);

    private SingleZipper() {
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, R> Single<R> zip(Single<? extends T1> s1, Single<? extends T2> s2,
                                     BiFunction<? super T1, ? super T2, ? extends R> zipper) {
        return from(s1.map(v -> new ZipArg(0, v)), s2.map(v -> new ZipArg(1, v)))
                .flatMapMergeSingle(identity(), 2)
                .collect(ZipResult2::new, (result, zipArg) -> {
                    if (result.z1 == null) {
                        result.z1 = zipArg;
                    } else {
                        result.z2 = zipArg;
                    }
                    return result;
                }).map(result -> {
                    assert result.z1 != null && result.z2 != null;
                    return result.z1.index < result.z2.index ?
                            zipper.apply((T1) result.z1.value, (T2) result.z2.value) :
                            zipper.apply((T1) result.z2.value, (T2) result.z1.value);
                });
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, T3, R> Single<R> zip(Single<? extends T1> s1, Single<? extends T2> s2, Single<? extends T3> s3,
                                         Function3<? super T1, ? super T2, ? super T3, ? extends R> zipper) {
        return from(s1.map(v -> new ZipArg(0, v)), s2.map(v -> new ZipArg(1, v)), s3.map(v -> new ZipArg(2, v)))
                .flatMapMergeSingle(identity(), 3)
                .collect(ZipResult3::new, (result, zipArg) -> {
                    if (result.z1 == null) {
                        result.z1 = zipArg;
                    } else if (result.z2 == null) {
                        result.z2 = zipArg;
                    } else {
                        result.z3 = zipArg;
                    }
                    return result;
                }).map(result -> {
                    assert result.z1 != null && result.z2 != null && result.z3 != null;
                    if (result.z1.index < result.z2.index) {
                        if (result.z2.index < result.z3.index) {
                            return zipper.apply((T1) result.z1.value, (T2) result.z2.value, (T3) result.z3.value);
                        }
                        return result.z1.index < result.z3.index ?
                                zipper.apply((T1) result.z1.value, (T2) result.z3.value, (T3) result.z2.value) :
                                zipper.apply((T1) result.z3.value, (T2) result.z1.value, (T3) result.z2.value);
                    }
                    if (result.z2.index < result.z3.index) {
                        return result.z1.index < result.z3.index ?
                                zipper.apply((T1) result.z2.value, (T2) result.z1.value, (T3) result.z3.value) :
                                zipper.apply((T1) result.z2.value, (T2) result.z3.value, (T3) result.z1.value);
                    }
                    return zipper.apply((T1) result.z3.value, (T2) result.z2.value, (T3) result.z1.value);
                });
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, T3, T4, R> Single<R> zip(
            Single<? extends T1> s1, Single<? extends T2> s2, Single<? extends T3> s3, Single<? extends T4> s4,
            Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> zipper) {
        return from(s1.map(v -> new ZipArg(0, v)),
                    s2.map(v -> new ZipArg(1, v)),
                    s3.map(v -> new ZipArg(2, v)),
                    s4.map(v -> new ZipArg(3, v)))
                .flatMapMergeSingle(identity(), 4)
                .collect(() -> new ArrayList<ZipArg>(4), (list, zipArg) -> {
                    list.add(zipArg);
                    return list;
                }).map(list -> {
                    list.sort(ZIP_ARG_COMPARATOR);
                    return zipper.apply((T1) list.get(0).value, (T2) list.get(1).value, (T3) list.get(2).value,
                            (T4) list.get(3).value);
                });
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
                .collect(() -> new ArrayList<ZipArg>(mappedSingles.length), (list, element) -> {
                    list.add(element);
                    return list;
                }).map(list -> {
                    list.sort(ZIP_ARG_COMPARATOR);
                    Object[] result = new Object[list.size()];
                    for (int i = 0; i < result.length; ++i) {
                        result[i] = list.get(i).value;
                    }
                    return zipper.apply(result);
                });
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

    private static final class ZipResult2 {
        @Nullable
        private ZipArg z1;
        @Nullable
        private ZipArg z2;
    }

    private static final class ZipResult3 {
        @Nullable
        private ZipArg z1;
        @Nullable
        private ZipArg z2;
        @Nullable
        private ZipArg z3;
    }
}
