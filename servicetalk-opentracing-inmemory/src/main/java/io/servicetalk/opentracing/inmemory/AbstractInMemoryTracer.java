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
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContextExtractor;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContextInjector;
import io.servicetalk.opentracing.inmemory.api.InMemoryTracer;

import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.opentracing.propagation.Format.Builtin.HTTP_HEADERS;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_EXTRACT;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP_INJECT;

/**
 * Base class for {@link InMemoryTracer} tracer implementations.
 */
abstract class AbstractInMemoryTracer implements InMemoryTracer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractInMemoryTracer.class);

    @Override
    public final <C> void inject(InMemorySpanContext spanContext, Format<C> format, C carrier) {
        try {
            if (format instanceof InMemorySpanContextInjector) {
                @SuppressWarnings("unchecked")
                final InMemorySpanContextInjector<C> injector = ((InMemorySpanContextInjector<C>) format);
                injector.inject(spanContext, carrier);
            } else if (format == TEXT_MAP || format == TEXT_MAP_INJECT || format == HTTP_HEADERS) {
                TextMapFormatter.INSTANCE.inject(spanContext, (TextMapInject) carrier);
            } else {
                throw new UnsupportedOperationException("Format " + format + " is not supported");
            }
        } catch (Exception e) {
            // Tracing should be low impact, so don't throw if formatting failed
            LOGGER.warn("Failed to inject SpanContext into carrier", e);
        }
    }

    @Nullable
    @Override
    public final <C> InMemorySpanContext extract(Format<C> format, C carrier) {
        try {
            if (format instanceof InMemorySpanContextExtractor) {
                @SuppressWarnings("unchecked")
                final InMemorySpanContextExtractor<C> extractor = ((InMemorySpanContextExtractor<C>) format);
                return extractor.extract(carrier);
            } else if (format == TEXT_MAP || format == TEXT_MAP_EXTRACT || format == HTTP_HEADERS) {
                return TextMapFormatter.INSTANCE.extract((TextMapExtract) carrier);
            } else {
                throw new UnsupportedOperationException("Format " + format + " is not supported");
            }
        } catch (Exception e) {
            // Tracing should be low impact, so don't throw if formatting failed
            LOGGER.warn("Failed to inject SpanContext into carrier", e);
            return null;
        }
    }
}
