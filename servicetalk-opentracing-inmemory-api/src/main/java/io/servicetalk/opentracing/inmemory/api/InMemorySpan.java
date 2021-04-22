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

import io.opentracing.Span;

import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.opentracing.internal.HexUtils.longOfHexBytes;
import static io.servicetalk.opentracing.internal.TracingConstants.NO_PARENT_ID;

/**
 * A span that allows reading values at runtime.
 */
public interface InMemorySpan extends Span {
    @Override
    InMemorySpanContext context();

    /**
     * The hex representation of the traceId.
     * @return hex representation of the traceId.
     */
    String traceIdHex();

    /**
     * The hex representation of the traceId.
     * @return hex representation of the traceId.
     */
    String spanIdHex();

    /**
     * The hex representation of the parent's spanId.
     * @return hex representation of the parent's spanId, or {@code null} if there is no parent.
     */
    @Nullable
    String parentSpanIdHex();

    /**
     * Determine if this span is sampled.
     * @return {@code true} if this span is sampled.
     */
    boolean isSampled();

    /**
     * Returns the operation name.
     *
     * @return operation name
     */
    String operationName();

    /**
     * Returns an immutable list of references.
     *
     * @return list of references
     */
    Iterable<? extends InMemoryReference> references();

    /**
     * Returns the low 64 bits of trace ID.
     *
     * @return low 64 bits of the trace ID
     */
    default long traceId() {
        String traceIdHex = traceIdHex();
        return longOfHexBytes(traceIdHex, traceIdHex.length() >= 32 ? 16 : 0);
    }

    /**
     * Returns the high 64 bits for 128-bit trace IDs, or {@code 0L} for 64-bit trace IDs.
     *
     * @return high 64 bits of the trace ID
     */
    default long traceIdHigh() {
        String traceIdHex = traceIdHex();
        return traceIdHex.length() >= 32 ? longOfHexBytes(traceIdHex, 0) : 0;
    }

    /**
     * Returns the span ID.
     *
     * @return span ID
     */
    default long spanId() {
        return longOfHexBytes(spanIdHex(), 0);
    }

    /**
     * Returns the parent span ID, could be null.
     *
     * @return parent span ID
     */
    @Nullable
    default Long parentSpanId() {
        String parentSpanIdHex = parentSpanIdHex();
        return parentSpanIdHex == null ? null : longOfHexBytes(parentSpanIdHex, 0);
    }

    /**
     * Returns the parent span ID in hex. Returns {@code "null"} if the parent span ID is not present.
     *
     * @return parent span ID in hex
     */
    default String nonnullParentSpanIdHex() {
        String parentSpanIdHex = parentSpanIdHex();
        return parentSpanIdHex == null ? NO_PARENT_ID : parentSpanIdHex;
    }

    /**
     * Returns an unmodifiable view of the tags.
     *
     * @return the tags
     */
    Map<String, Object> tags();

    /**
     * Returns an unmodifiable view of logs. This may return null if the logs are not persisted.
     *
     * @return the logs
     */
    @Nullable
    Iterable<? extends InMemorySpanLog> logs();

    /**
     * Returns the starting epoch in milliseconds. May return -1 if the span is not sampled.
     *
     * @return starting epoch in milliseconds
     */
    long startEpochMicros();
}
