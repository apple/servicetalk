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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map.Entry;
import javax.annotation.Nullable;

import static io.servicetalk.opentracing.inmemory.SingleLineValue.format;

/**
 * SpanContext object used by the {@link DefaultInMemoryTracer}.
 */
public final class DefaultInMemorySpanContext implements InMemorySpanContext {
    private static final Logger logger = LoggerFactory.getLogger(DefaultInMemorySpanContext.class);

    private final String traceId;
    private final String spanId;
    @Nullable
    private final String parentSpanId;
    @Nullable
    private final Boolean sampled;

    /**
     * Constructs an instance.
     *
     * @param traceId      trace ID
     * @param spanId       span ID
     * @param parentSpanId parent span ID, optional
     * @param sampled      whether the trace is sampled
     */
    public DefaultInMemorySpanContext(String traceId, String spanId, @Nullable String parentSpanId,
                                      @Nullable Boolean sampled) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sampled = sampled;
    }

    @Override
    public Boolean isSampled() {
        return sampled;
    }

    @Override
    public Iterable<Entry<String, String>> baggageItems() {
        logger.debug("baggageItems() is not supported");
        return Collections.<String, String>emptyMap().entrySet();
    }

    @Override
    public String toSpanId() {
        return spanId;
    }

    @Override
    public String toTraceId() {
        return traceId;
    }

    @Override
    public String parentSpanId() {
        return parentSpanId;
    }

    @Override
    public String toString() {
        return format(traceId, spanId, parentSpanId);
    }
}
