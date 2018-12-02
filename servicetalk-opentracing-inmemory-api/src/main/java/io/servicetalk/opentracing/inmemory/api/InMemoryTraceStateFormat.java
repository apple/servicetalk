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
package io.servicetalk.opentracing.inmemory.api;

import io.opentracing.propagation.Format;

import javax.annotation.Nullable;

/**
 * A {@link Format} compatible with {@link InMemoryTraceState}.
 * @param <C> the carrier type.
 */
public interface InMemoryTraceStateFormat<C> extends Format<C> {
    /**
     * Inject a trace state into a carrier.
     *
     * @param state   trace state
     * @param carrier carrier to inject into
     */
    void inject(InMemoryTraceState state, C carrier);

    /**
     * Extract the trace state from a carrier.
     *
     * @param carrier carrier to extract from
     * @return extracted trace state, may be {@code null} if the carrier doesn't contain a valid span
     * @throws Exception if any parsing error happened during extraction
     */
    @Nullable
    InMemoryTraceState extract(C carrier) throws Exception;
}
