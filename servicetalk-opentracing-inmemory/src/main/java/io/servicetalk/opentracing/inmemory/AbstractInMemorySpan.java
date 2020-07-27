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
package io.servicetalk.opentracing.inmemory;

import io.servicetalk.opentracing.inmemory.api.InMemoryReference;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceState;

import io.opentracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import javax.annotation.Nullable;

import static java.util.Collections.unmodifiableList;

/**
 * Span object used by the {@link DefaultInMemoryTracer}.
 */
abstract class AbstractInMemorySpan extends DefaultInMemorySpanContext implements InMemorySpan {
    private static final Logger logger = LoggerFactory.getLogger(AbstractInMemorySpan.class);

    String operationName;
    final List<InMemoryReference> references;

    /**
     * Instantiates a new {@link AbstractInMemorySpan}.
     *
     * @param operationName the operation name.
     * @param references a {@link List} of {@link InMemoryReference}s.
     * @param state the {@link InMemoryTraceState} associated with this {@link Span}
     */
    AbstractInMemorySpan(String operationName, List<InMemoryReference> references, InMemoryTraceState state) {
        super(state);
        this.operationName = operationName;
        this.references = references;
    }

    @Override
    public final DefaultInMemorySpanContext context() {
        return this;
    }

    @Override
    public final String operationName() {
        return operationName;
    }

    @Override
    public final List<InMemoryReference> references() {
        return unmodifiableList(references);
    }

    @Override
    public final Span setOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    @Override
    public final Span setBaggageItem(String key, String value) {
        // Not supported, silently ignore to avoid breaking third party code.
        logger.debug("setBaggageItem() is not supported");
        return this;
    }

    @Nullable
    @Override
    public final String getBaggageItem(String key) {
        logger.debug("getBaggageItem() is not supported");
        return null;
    }

    @Override
    public final String traceIdHex() {
        return traceState.traceIdHex();
    }

    @Override
    public final String spanIdHex() {
        return traceState.spanIdHex();
    }

    @Nullable
    @Override
    public final String parentSpanIdHex() {
        return traceState.parentSpanIdHex();
    }

    @Override
    public String toTraceId() {
        return traceIdHex();
    }

    @Override
    public String toSpanId() {
        return spanIdHex();
    }
}
