/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.TriConsumer;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContext;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContextFormat;
import io.servicetalk.opentracing.internal.ZipkinHeaderNames;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.opentracing.internal.HexUtils.validateHexBytes;
import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;

public class B3KeyValueFormatter<T> implements InMemorySpanContextFormat<T> {
    private final TriConsumer<T, CharSequence, CharSequence> carrierInjector;
    private final BiFunction<T, CharSequence, CharSequence> carrierExtractor;

    private static final Logger logger = LoggerFactory.getLogger(B3KeyValueFormatter.class);
    private static final CharSequence TRACE_ID = newAsciiString(ZipkinHeaderNames.TRACE_ID);
    private static final CharSequence SPAN_ID = newAsciiString(ZipkinHeaderNames.SPAN_ID);
    private static final CharSequence PARENT_SPAN_ID = newAsciiString(ZipkinHeaderNames.PARENT_SPAN_ID);
    private static final CharSequence SAMPLED = newAsciiString(ZipkinHeaderNames.SAMPLED);

    private final boolean verifyExtractedValues;

    /**
     * Create a new instance.
     *
     * @param carrierInjector A {@link TriConsumer} used to inject entries to a Key-Value carrier.
     * @param carrierExtractor A {@link BiFunction} used to extract to extract values from a Key-Value carrier.
     * @param verifyExtractedValues {@code true} to make a best effort verification that the extracted values are of the
     * correct format.
     */
    public B3KeyValueFormatter(TriConsumer<T, CharSequence, CharSequence> carrierInjector,
                               BiFunction<T, CharSequence, CharSequence> carrierExtractor,
                               boolean verifyExtractedValues) {
        this.carrierInjector = requireNonNull(carrierInjector);
        this.carrierExtractor = requireNonNull(carrierExtractor);
        this.verifyExtractedValues = verifyExtractedValues;
    }

    @Override
    public void inject(final InMemorySpanContext context, final T carrier) {
        carrierInjector.accept(carrier, TRACE_ID, context.toTraceId());
        carrierInjector.accept(carrier, SPAN_ID, context.toSpanId());
        String parentSpanIdHex = context.parentSpanId();
        if (parentSpanIdHex != null) {
            carrierInjector.accept(carrier, PARENT_SPAN_ID, parentSpanIdHex);
        }

        final Boolean isSampled = context.isSampled();
        if (isSampled != null) {
            carrierInjector.accept(carrier, SAMPLED, isSampled ? "1" : "0");
        }
    }

    @Nullable
    @Override
    public InMemorySpanContext extract(final T carrier) {
        CharSequence traceId = carrierExtractor.apply(carrier, TRACE_ID);
        if (traceId == null) {
            return null;
        }

        CharSequence spanId = carrierExtractor.apply(carrier, SPAN_ID);
        if (spanId == null) {
            return null;
        }

        CharSequence parentSpanId = carrierExtractor.apply(carrier, PARENT_SPAN_ID);
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

        CharSequence sampleId = carrierExtractor.apply(carrier, SAMPLED);
        return new DefaultInMemorySpanContext(traceId.toString(), spanId.toString(),
                valueOf(parentSpanId), sampleId != null ? (sampleId.length() == 1 && sampleId.charAt(0) != '0') : null);
    }
}
