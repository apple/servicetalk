/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

final class SubscriberApiUtils {
    private static final Object NULL_TOKEN = new Object();

    private SubscriberApiUtils() {
        // no instances
    }

    static Object wrapNull(@Nullable Object o) {
        return o == null ? NULL_TOKEN : o;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    static <T> T unwrapNullUnchecked(Object o) {
        return o == NULL_TOKEN ? null : (T) o;
    }
}
