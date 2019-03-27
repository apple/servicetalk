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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DeliberateException;

final class TckUtils {

    private TckUtils() {
        // no instances
    }

    /**
     * Creates a new {@link Publisher} that will emit {@code numElements}.
     * @param numElements the number of elements to emit.
     * @return the publisher.
     */
    static Publisher<Integer> newPublisher(int numElements) {
        return Publisher.from(newArray(numElements));
    }

    static Integer[] newArray(int numElements) {
        Integer[] values = new Integer[numElements];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        return values;
    }

    static int maxElementsFromPublisher() {
        return 1024 * 1024;
    }

    static int requestNToInt(long n) {
        assert n <= Integer.MAX_VALUE :
                "Must be <= Integer.MAX_VALUE as we enforced this via maxElementsFromPublisher()";
        return (int) n;
    }

    static <T> Publisher<T> newFailedPublisher() {
        return Publisher.failed(DeliberateException.DELIBERATE_EXCEPTION);
    }
}
