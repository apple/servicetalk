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
package io.servicetalk.opentracing.inmemory;

import io.servicetalk.opentracing.inmemory.api.InMemoryTraceState;

import javax.annotation.Nullable;

/**
 * Default implementation of {@link InMemoryTraceState}.
 */
public final class DefaultInMemoryTraceState implements InMemoryTraceState {
    private final String traceIdHex;
    private final String spanIdHex;
    @Nullable
    private final String parentSpanIdHex;
    @Nullable
    private final Boolean sampled;

    /**
     * Constructs an instance.
     *
     * @param traceIdHex      trace ID
     * @param spanIdHex       span ID
     * @param parentSpanIdHex parent span ID, optional
     * @param sampled         whether the trace is sampled
     */
    public DefaultInMemoryTraceState(String traceIdHex, String spanIdHex, @Nullable String parentSpanIdHex,
                                     @Nullable Boolean sampled) {
        this.traceIdHex = traceIdHex;
        this.spanIdHex = spanIdHex;
        this.parentSpanIdHex = parentSpanIdHex;
        this.sampled = sampled;
    }

    @Override
    public String traceIdHex() {
        return traceIdHex;
    }

    @Override
    public String spanIdHex() {
        return spanIdHex;
    }

    @Override
    @Nullable
    public String parentSpanIdHex() {
        return parentSpanIdHex;
    }

    @Override
    public Boolean isSampled() {
        return sampled;
    }
}
