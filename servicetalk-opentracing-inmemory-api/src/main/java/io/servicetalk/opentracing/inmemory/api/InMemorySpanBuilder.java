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
package io.servicetalk.opentracing.inmemory.api;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer.SpanBuilder;

/**
 * A {@link SpanBuilder} that works with {@link InMemorySpan} instances.
 */
public interface InMemorySpanBuilder extends SpanBuilder {
    @Override
    InMemorySpanBuilder asChildOf(SpanContext parent);

    @Override
    InMemorySpanBuilder asChildOf(Span parent);

    /**
     * {@inheritDoc}
     * @throws ClassCastException if {@code referencedContext} is not of type {@link InMemorySpanContext}.
     */
    @Override
    default InMemorySpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        return addReference(referenceType, (InMemorySpanContext) referencedContext);
    }

    /**
     * Same as {@link #addReference(String, SpanContext)} but requires a {@link InMemorySpanContext}.
     * @param referenceType the reference type.
     * @param referencedContext the reference context.
     * @return {@code this}.
     */
    InMemorySpanBuilder addReference(String referenceType, InMemorySpanContext referencedContext);

    @Override
    InMemorySpanBuilder ignoreActiveSpan();

    @Override
    InMemorySpanBuilder withTag(String key, String value);

    @Override
    InMemorySpanBuilder withTag(String key, boolean value);

    @Override
    InMemorySpanBuilder withTag(String key, Number value);

    @Override
    InMemorySpanBuilder withStartTimestamp(long microseconds);

    @Override
    InMemorySpan start();
}
