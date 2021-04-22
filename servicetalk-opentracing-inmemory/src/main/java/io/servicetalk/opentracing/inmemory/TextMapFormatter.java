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

import io.opentracing.propagation.TextMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.opentracing.internal.HexUtils.validateHexBytes;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.PARENT_SPAN_ID;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.SAMPLED;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.SPAN_ID;
import static io.servicetalk.opentracing.internal.ZipkinHeaderNames.TRACE_ID;

/**
 * Zipkin-styled header serialization format.
 */
final class TextMapFormatter implements InMemoryTraceStateFormat<TextMap> {
    public static final TextMapFormatter INSTANCE = new TextMapFormatter();
    private static final Logger logger = LoggerFactory.getLogger(TextMapFormatter.class);

    private TextMapFormatter() {
        // singleton
    }

    @Override
    public void inject(final InMemorySpanContext context, final TextMap carrier) {
        final InMemoryTraceState state = context.traceState();
        carrier.put(TRACE_ID, state.traceIdHex());
        carrier.put(SPAN_ID, state.spanIdHex());
        if (state.parentSpanIdHex() != null) {
            carrier.put(PARENT_SPAN_ID, state.parentSpanIdHex());
        }
        carrier.put(SAMPLED, context.isSampled() ? "1" : "0");
    }

    @Nullable
    @Override
    public InMemoryTraceState extract(TextMap carrier) {
        String traceId = null;
        String spanId = null;
        String parentSpanId = null;
        boolean sampled = false;
        for (Map.Entry<String, String> e : carrier) {
            String key = e.getKey();
            String value = e.getValue().trim();

            if (TRACE_ID.equalsIgnoreCase(key)) {
                if (value.isEmpty()) {
                    logger.warn("TraceId is empty");
                    continue;
                }
                traceId = validateHexBytes(value);
            } else if (SPAN_ID.equalsIgnoreCase(key)) {
                if (value.isEmpty()) {
                    logger.warn("SpanId is empty");
                    continue;
                }
                spanId = validateHexBytes(value);
            } else if (PARENT_SPAN_ID.equalsIgnoreCase(key)) {
                if (value.isEmpty()) {
                    logger.warn("ParentSpanId is empty");
                    continue;
                }
                parentSpanId = validateHexBytes(value);
            } else if (SAMPLED.equalsIgnoreCase(key)) {
                sampled = "1".equals(value);
            }
        }

        // Some basic validation
        if (traceId == null || spanId == null) {
            return null;
        }
        if (parentSpanId != null && parentSpanId.equals(spanId)) {
            logger.warn("SpanId cannot be the same as ParentSpanId, value={}", parentSpanId);
            return null;
        }

        return new DefaultInMemoryTraceState(traceId, spanId, parentSpanId, sampled);
    }
}
