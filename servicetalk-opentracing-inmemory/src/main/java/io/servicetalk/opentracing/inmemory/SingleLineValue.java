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

import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.opentracing.internal.HexUtils.hexBytesOfLong;
import static io.servicetalk.opentracing.internal.TracingIdUtils.idOrNullAsValue;

/**
 * Wrapper for supporting injecting and extracting single-string values.
 */
public interface SingleLineValue {
    /**
     * Gets the value.
     *
     * @return the value
     */
    @Nullable
    String get();

    /**
     * Sets the value.
     *
     * @param value the value
     */
    void set(String value);

    /**
     * Returns a {@link SingleLineValue} backed by a fixed string.
     *
     * @param value string value
     * @return a read-only {@link SingleLineValue}
     */
    static SingleLineValue of(@Nullable String value) {
        return new SingleLineValue() {
            @Nullable
            @Override
            public String get() {
                return value;
            }

            @Override
            public void set(String value) {
                throw new UnsupportedOperationException("Inject not supported");
            }
        };
    }

    /**
     * Returns a {@link SingleLineValue} which supports injection.
     *
     * @param consumer consumer to call when a value is injected
     * @return a write-only {@link SingleLineValue}
     */
    static SingleLineValue to(Consumer<String> consumer) {
        return new SingleLineValue() {
            @Nullable
            @Override
            public String get() {
                throw new UnsupportedOperationException("Extract not supported");
            }

            @Override
            public void set(String value) {
                consumer.accept(value);
            }
        };
    }

    /**
     * Formats the provided trace information.
     *
     * @param traceId      the trace ID.
     * @param spanId       the span ID.
     * @param parentSpanId the parent span ID, or {@code null} if none available.
     * @return the formatted trace information as {@link String}.
     */
    static String format(long traceId, long spanId, @Nullable Long parentSpanId) {
        return format(hexBytesOfLong(traceId), hexBytesOfLong(spanId), parentSpanId == null ? null :
                hexBytesOfLong(parentSpanId));
    }

    /**
     * Formats the provided trace information.
     *
     * @param traceIdHex      the trace ID as HEX {@link String}.
     * @param spanIdHex       the span ID as HEX {@link String}.
     * @param parentSpanIdHex the parent span ID as HEX {@link String}, or {@code null} if none available.
     * @return the formatted trace information as {@link String}.
     */
    static String format(String traceIdHex, String spanIdHex, @Nullable String parentSpanIdHex) {
        return traceIdHex + '.' + spanIdHex + "<:" + idOrNullAsValue(parentSpanIdHex);
    }

    /**
     * Formats the provided trace information.
     *
     * @param traceIdHex the trace ID as HEX {@link String}.
     * @param spanIdHex the span ID as HEX {@link String}.
     * @param parentSpanIdHex the parent span ID as HEX {@link String}, or {@code null} if none available.
     * @param isSampled {@code true} if the span is sampled.
     * @return the formatted trace information as {@link String}.
     */
    static String format(String traceIdHex, String spanIdHex, @Nullable String parentSpanIdHex, boolean isSampled) {
        return format(traceIdHex, spanIdHex, parentSpanIdHex) + (isSampled ? ":1" : ":0");
    }
}
