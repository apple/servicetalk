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

import io.opentracing.SpanContext;

/**
 * A span that allows reading values at runtime.
 */
public interface InMemorySpanContext extends SpanContext {
    /**
     * Get the {@link InMemoryTraceState} associated with this object.
     * @return the {@link InMemoryTraceState} associated with this object.
     */
    InMemoryTraceState traceState();

    /**
     * Returns whether the span should be sampled.
     * <p>
     * Note this may differ from {@link InMemorySpan#isSampled()} from {@link #traceState()} if the value is overridden
     * based upon some sampling policy.
     *
     * @return whether the span should be sampled
     */
    default boolean isSampled() {
        return traceState().isSampled();
    }

    default String toTraceId() {
        return traceState().traceIdHex();
    }

    default String toSpanId() {
        return traceState().spanIdHex();
    }
}
