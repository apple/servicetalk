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
package io.servicetalk.examples.http.service.composition;

import io.servicetalk.concurrent.api.Single;

// This class exists to fill the gap for missing operators in our async primitives.
final class AsyncUtils {

    private AsyncUtils() {
        // No instances.
    }

    /**
     * This operator is not currently available in ServiceTalk hence we provide a workaround to achieve the same
     * results as a zip. This operator merges heterogeneous {@link Single} into a single holder object.
     *
     * @param first First {@link Single} to be zipped.
     * @param second Second {@link Single} to be zipped.
     * @param third Third {@link Single} to be zipped.
     * @param zipper {@link Zipper} function to combine the results of three {@link Single}s.
     * @param <T1> Type of item emitted by the first {@link Single}.
     * @param <T2> Type of item emitted by the second {@link Single}.
     * @param <T3> Type of item emitted by the third {@link Single}.
     * @param <R> Type of the final holder object.
     * @return A {@link Single} that will emit the final holder object.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    static <T1, T2, T3, R> Single<R> zip(Single<T1> first, Single<T2> second, Single<T3> third,
                                         Zipper<T1, T2, T3, R> zipper) {

        @SuppressWarnings({"unchecked", "rawtypes"})
        Single<R> resp = first.concatWith((Single) second).concatWith(third)
                .reduce(Collector::new, (collector, aEntity) -> {
                    @SuppressWarnings("unchecked")
                    Collector<T1, T2, T3, R> c = (Collector<T1, T2, T3, R>) collector;
                    return c.add(aEntity);
                })
                .map(collector -> {
                    @SuppressWarnings("unchecked")
                    Collector<T1, T2, T3, R> c = (Collector<T1, T2, T3, R>) collector;
                    return c.zip(zipper);
                });

        return resp;
    }

    @FunctionalInterface
    interface Zipper<T1, T2, T3, R> {

        R zip(T1 first, T2 second, T3 third);
    }

    private static final class Collector<T1, T2, T3, R> {

        private final Object[] holder = new Object[3];
        private int index;

        Collector<T1, T2, T3, R> add(Object entity) {
            holder[index++] = entity;
            return this;
        }

        @SuppressWarnings("unchecked")
        R zip(Zipper<T1, T2, T3, R> zipper) {
            return zipper.zip((T1) holder[0], (T2) holder[1], (T3) holder[2]);
        }
    }
}
