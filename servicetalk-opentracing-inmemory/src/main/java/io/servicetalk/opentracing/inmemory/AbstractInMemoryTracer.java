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
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceStateFormat;
import io.servicetalk.opentracing.inmemory.api.InMemoryTracer;

import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Base class for {@link InMemoryTraceState} tracer implementations.
 */
abstract class AbstractInMemoryTracer implements InMemoryTracer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractInMemoryTracer.class);

    @SuppressWarnings("unchecked")
    @Override
    public final <C> void inject(InMemorySpanContext spanContext, Format<C> format, C carrier) {
        requireNonNull(spanContext);
        requireNonNull(format);
        requireNonNull(carrier);

        try {
            if (format instanceof InMemoryTraceStateFormat) {
                ((InMemoryTraceStateFormat<C>) format).inject(spanContext.traceState(), carrier);
            } else if (format == Format.Builtin.TEXT_MAP) {
                TextMapFormatter.INSTANCE.inject(spanContext.traceState(), (TextMap) carrier);
            } else {
                throw new UnsupportedOperationException("Format " + format + " is not supported");
            }
        } catch (Exception e) {
            // Tracing should be low impact, so don't throw if formatting failed
            LOGGER.warn("Failed to inject SpanContext into carrier", e);
        }
    }

    @Nullable
    @Override
    public final <C> InMemorySpanContext extract(Format<C> format, C carrier) {
        requireNonNull(format);
        requireNonNull(carrier);

        try {
            final InMemoryTraceState state;
            if (format instanceof InMemoryTraceStateFormat) {
                state = ((InMemoryTraceStateFormat<C>) format).extract(carrier);
            } else if (format == Format.Builtin.TEXT_MAP) {
                state = TextMapFormatter.INSTANCE.extract((TextMap) carrier);
            } else {
                throw new UnsupportedOperationException("Format " + format + " is not supported");
            }
            return state != null ? newSpanContext(state) : null;
        } catch (Exception e) {
            // Tracing should be low impact, so don't throw if formatting failed
            LOGGER.warn("Failed to inject SpanContext into carrier", e);
            return null;
        }
    }

    /**
     * Create a span context with given trace state. Called when extracting from carriers.
     *
     * @param state The state for the trace.
     * @return span context
     */
    protected abstract InMemorySpanContext newSpanContext(InMemoryTraceState state);
}
