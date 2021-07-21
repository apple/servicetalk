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

import io.servicetalk.opentracing.inmemory.api.InMemoryReference;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContext;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import javax.annotation.Nullable;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * Span object used by the {@link DefaultInMemoryTracer}.
 */
abstract class AbstractInMemorySpan implements InMemorySpan {
    private static final Logger logger = LoggerFactory.getLogger(AbstractInMemorySpan.class);

    private final InMemorySpanContext context;
    private final List<InMemoryReference> references;
    String operationName;

    /**
     * Instantiates a new {@link AbstractInMemorySpan}.
     *
     * @param operationName the operation name.
     * @param references a {@link List} of {@link InMemoryReference}s.
     * @param context the {@link SpanContext} associated with this {@link Span}
     */
    AbstractInMemorySpan(String operationName, List<InMemoryReference> references, InMemorySpanContext context) {
        this.context = requireNonNull(context);
        this.operationName = operationName;
        this.references = references;
    }

    @Override
    public final InMemorySpanContext context() {
        return context;
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
}
