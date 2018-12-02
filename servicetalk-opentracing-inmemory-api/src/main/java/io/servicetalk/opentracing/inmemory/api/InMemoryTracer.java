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

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * A {@link Tracer} that works with {@link InMemorySpan} instances.
 */
public interface InMemoryTracer extends Tracer {
    @Override
    InMemoryScopeManager scopeManager();

    @Override
    InMemorySpan activeSpan();

    @Override
    InMemorySpanBuilder buildSpan(String operationName);

    /**
     * {@inheritDoc}
     * @throws ClassCastException if {@code spanContext} is not of type {@link InMemorySpanContext}.
     */
    @Override
    default <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        inject((InMemorySpanContext) spanContext, format, carrier);
    }

    /**
     * Same as {@link #inject(SpanContext, Format, Object)} but requires a {@link InMemorySpanContext}.
     * @param spanContext The {@link InMemorySpanContext} to inject into {@code carrier}.
     * @param format The format to in which to inject into {@code carrier}.
     * @param carrier The carrier to be injected into.
     * @param <C> The type of carrier.
     */
    <C> void inject(InMemorySpanContext spanContext, Format<C> format, C carrier);

    @Override
    <C> InMemorySpanContext extract(Format<C> format, C carrier);
}
