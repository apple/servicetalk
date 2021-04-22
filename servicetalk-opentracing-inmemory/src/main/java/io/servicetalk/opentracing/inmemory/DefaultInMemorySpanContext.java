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

import io.servicetalk.opentracing.inmemory.api.InMemorySpanContext;
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map.Entry;

import static io.servicetalk.opentracing.inmemory.SingleLineValue.format;
import static java.util.Objects.requireNonNull;

/**
 * Span object used by the {@link DefaultInMemoryTracer}.
 */
class DefaultInMemorySpanContext implements InMemorySpanContext {
    private static final Logger logger = LoggerFactory.getLogger(DefaultInMemorySpanContext.class);

    final InMemoryTraceState traceState;
    final boolean isSampledOverride;

    DefaultInMemorySpanContext(InMemoryTraceState state) {
        this(state, state.isSampled() != null && requireNonNull(state.isSampled()));
    }

    DefaultInMemorySpanContext(InMemoryTraceState state,
                               boolean isSampledOverride) {
        this.traceState = requireNonNull(state);
        this.isSampledOverride = isSampledOverride;
    }

    @Override
    public InMemoryTraceState traceState() {
        return traceState;
    }

    @Override
    public boolean isSampled() {
        return isSampledOverride;
    }

    @Override
    public Iterable<Entry<String, String>> baggageItems() {
        logger.debug("baggageItems() is not supported");
        return Collections.<String, String>emptyMap().entrySet();
    }

    @Override
    public String toString() {
        return format(traceState.traceIdHex(), traceState.spanIdHex(), traceState.parentSpanIdHex());
    }
}
