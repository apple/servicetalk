/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentelemetry.http;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

abstract class OpenTelemetryFilterBuilder<T extends OpenTelemetryFilterBuilder<T>> {
    List<String> capturedRequestHeaders = emptyList();
    List<String> capturedResponseHeaders = emptyList();
    boolean enableMetrics;
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

    abstract T thisInstance();

    // TODO: remove once we can remove the OpenTelemetryOptions
    final T applyOptions(final OpenTelemetryOptions options) {
        enableMetrics = options.enableMetrics();
        capturedRequestHeaders = options.capturedRequestHeaders();
        capturedResponseHeaders = options.capturedResponseHeaders();
        return thisInstance();
    }

    /**
     * Add the headers to be captured as extra span attributes.
     *
     * @param capturedRequestHeaders extra headers to be captured in client/server requests and
     *     added as extra span attributes
     * @return {@code this}
     * @see HttpClientAttributesExtractorBuilder#setCapturedRequestHeaders(List)
     * @see HttpServerAttributesExtractorBuilder#setCapturedRequestHeaders(List)
     */
    public final T capturedRequestHeaders(final List<String> capturedRequestHeaders) {
        requireNonNull(capturedRequestHeaders, "capturedRequestHeaders");
        this.capturedRequestHeaders =
                capturedRequestHeaders.isEmpty() ? emptyList() :
                        unmodifiableList(new ArrayList<>(capturedRequestHeaders));
        return thisInstance();
    }

    /**
     * Add the headers to be captured as extra span attributes.
     *
     * @param capturedResponseHeaders extra headers to be captured in client/server response and
     *     added as extra span attributes
     * @return {@code this}
     * @see HttpClientAttributesExtractorBuilder#setCapturedResponseHeaders(List)
     * @see HttpServerAttributesExtractorBuilder#setCapturedResponseHeaders(List)
     */
    public final T capturedResponseHeaders(final List<String> capturedResponseHeaders) {
        requireNonNull(capturedResponseHeaders, "capturedResponseHeaders");
        this.capturedResponseHeaders =
                capturedResponseHeaders.isEmpty() ? emptyList() :
                        unmodifiableList(new ArrayList<>(capturedResponseHeaders));
        return thisInstance();
    }

    /**
     * Whether to enable operation metrics or not.
     *
     * @param enableMetrics whether to enable operation metrics or not
     * @return {@code this}
     * @see InstrumenterBuilder#addOperationMetrics(OperationMetrics)
     * @see HttpClientMetrics
     * @see HttpServerMetrics
     */
    public final T enableMetrics(final boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
        return thisInstance();
    }

    /**
     * Set the {@link OpenTelemetry} instance to use for creating spans.
     *
     * @param openTelemetry the {@link OpenTelemetry} instance
     * @return {@code this}
     */
    public final T openTelemetry(OpenTelemetry openTelemetry) {
        this.openTelemetry = requireNonNull(openTelemetry, "openTelemetry");
        return thisInstance();
    }
}
