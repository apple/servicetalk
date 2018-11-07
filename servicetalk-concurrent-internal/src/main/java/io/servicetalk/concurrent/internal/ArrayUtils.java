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

import static java.util.Objects.requireNonNull;

/**
 * Utilities for arrays.
 */
public final class ArrayUtils {
    private ArrayUtils() {
        // no instances.
    }

    /**
     * Find the index of {@code l} in {@code array}.
     * @param l The element to find.
     * @param array The array to search in.
     * @param <X> The type of object.
     * @return The index of {@code l} in {@code array}, or {@code <0}.
     */
    public static <X> int indexOf(X l, X[] array) {
        requireNonNull(l);
        for (int i = 0; i < array.length; ++i) {
            if (l.equals(array[i])) {
                return i;
            }
        }
        return -1;
    }
}
