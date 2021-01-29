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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;

/**
 * Metadata associated with a payload {@link Publisher} contained within an {@link StreamingHttpRequest} or
 * {@link StreamingHttpResponse}.
 */
interface PayloadInfo {
    /**
     * Returns {@code true} if and only if, the {@link Publisher} associated with this {@link PayloadInfo} can be safely
     * aggregated to bring all data in memory. Inputs to this decision is left to the user of the API.
     *
     * @return {@code true} if and only if, the {@link Publisher} associated with this {@link PayloadInfo} can be safely
     * aggregated to bring all data in memory.
     */
    boolean isSafeToAggregate();

    /**
     * Returns {@code true} if and only if, the {@link Publisher} associated with this {@link PayloadInfo} may also
     * contain trailers.
     *
     * @return {@code true} if and only if, the {@link Publisher} associated with this {@link PayloadInfo} may also
     * contain trailers.
     */
    boolean mayHaveTrailers();

    /**
     * Returns {@code true} if and only if, the {@link Publisher} associated with this {@link PayloadInfo} will only
     * emit {@link Buffer}s (independent of trailers).
     * @deprecated "raw" payload type support will be removed in future releases.
     * @return {@code true} if and only if, the {@link Publisher} associated with this {@link PayloadInfo} will only
     * emit {@link Buffer}s (independent of trailers).
     */
    @Deprecated
    boolean onlyEmitsBuffer();
}
