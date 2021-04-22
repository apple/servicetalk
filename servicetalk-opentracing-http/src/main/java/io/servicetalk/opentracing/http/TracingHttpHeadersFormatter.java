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
package io.servicetalk.opentracing.http;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTraceState;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContext;
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceState;
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceStateFormat;
import io.servicetalk.opentracing.internal.ZipkinHeaderNames;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.opentracing.internal.HexUtils.validateHexBytes;
import static java.lang.Boolean.TRUE;
import static java.lang.String.valueOf;

final class TracingHttpHeadersFormatter implements InMemoryTraceStateFormat<HttpHeaders> {
    private static final Logger logger = LoggerFactory.getLogger(TracingHttpHeadersFormatter.class);
    private static final CharSequence TRACE_ID = newAsciiString(ZipkinHeaderNames.TRACE_ID);
    private static final CharSequence SPAN_ID = newAsciiString(ZipkinHeaderNames.SPAN_ID);
    private static final CharSequence PARENT_SPAN_ID = newAsciiString(ZipkinHeaderNames.PARENT_SPAN_ID);
    private static final CharSequence SAMPLED = newAsciiString(ZipkinHeaderNames.SAMPLED);
    static final InMemoryTraceStateFormat<HttpHeaders> FORMATTER_VALIDATION = new TracingHttpHeadersFormatter(true);
    static final InMemoryTraceStateFormat<HttpHeaders> FORMATTER_NO_VALIDATION =
            new TracingHttpHeadersFormatter(false);

    private final boolean verifyExtractedValues;

    /**
     * Create a new instance.
     *
     * @param verifyExtractedValues {@code true} to make a best effort verification that the extracted values are of the
     * correct format.
     */
    private TracingHttpHeadersFormatter(boolean verifyExtractedValues) {
        this.verifyExtractedValues = verifyExtractedValues;
    }

    static InMemoryTraceStateFormat<HttpHeaders> traceStateFormatter(boolean validateTraceKeyFormat) {
        return validateTraceKeyFormat ? FORMATTER_VALIDATION : FORMATTER_NO_VALIDATION;
    }

    @Override
    public void inject(final InMemorySpanContext context, final HttpHeaders carrier) {
        final InMemoryTraceState state = context.traceState();
        carrier.set(TRACE_ID, state.traceIdHex());
        carrier.set(SPAN_ID, state.spanIdHex());
        String parentSpanIdHex = state.parentSpanIdHex();
        if (parentSpanIdHex != null) {
            carrier.set(PARENT_SPAN_ID, parentSpanIdHex);
        }
        carrier.set(SAMPLED, context.isSampled() ? "1" : "0");
    }

    @Nullable
    @Override
    public InMemoryTraceState extract(final HttpHeaders carrier) {
        CharSequence traceId = carrier.get(TRACE_ID);
        if (traceId == null) {
            return null;
        }

        CharSequence spanId = carrier.get(SPAN_ID);
        if (spanId == null) {
            return null;
        }

        CharSequence parentSpanId = carrier.get(PARENT_SPAN_ID);
        if (verifyExtractedValues) {
            validateHexBytes(traceId);
            validateHexBytes(spanId);
            if (parentSpanId != null) {
                validateHexBytes(parentSpanId);
                if (contentEqualsIgnoreCase(parentSpanId, spanId)) {
                    logger.warn("SpanId cannot be the same as ParentSpanId, value={}", parentSpanId);
                    return null;
                }
            }
        }

        CharSequence sampleId = carrier.get(SAMPLED);
        return new DefaultInMemoryTraceState(traceId.toString(), spanId.toString(),
                valueOf(parentSpanId), sampleId != null ? (sampleId.length() == 1 && sampleId.charAt(0) != '0') : null);
    }
}
