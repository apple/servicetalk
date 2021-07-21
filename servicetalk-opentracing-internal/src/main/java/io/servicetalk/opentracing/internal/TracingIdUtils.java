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
package io.servicetalk.opentracing.internal;

import javax.annotation.Nullable;

public final class TracingIdUtils {

    private static final String NULL_VALUE = "null";

    private TracingIdUtils() {
        // no instantiation
    }

    /**
     * Returns the ID as provided or {@code "null"} if the ID was {@code null}.
     *
     * @param id The ID to be evaluated.
     * @return ID or {@code "null"}.
     */
    public static String idOrNullAsValue(@Nullable final String id) {
        return id == null ? NULL_VALUE : id;
    }
}
