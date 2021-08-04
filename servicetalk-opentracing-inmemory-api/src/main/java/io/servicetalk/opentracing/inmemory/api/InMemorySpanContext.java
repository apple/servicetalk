/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.opentracing.SpanContext;

import javax.annotation.Nullable;

/**
 * A span that allows reading values at runtime.
 */
public interface InMemorySpanContext extends SpanContext {
    /**
     * Returns whether the span should be sampled or {@code null} if the sampling was not decided.
     * <p>
     * @return whether the span should be sampled
     */
    @Nullable
    Boolean isSampled();

    /**
     * Returns the parent span ID in hex, or {@code null} if the parent span ID is not present.
     *
     * @return parent span ID in hex
     */
    @Nullable
    String parentSpanId();
}
