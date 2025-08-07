/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

/** A set of options for configuring OpenTelemetry filters. */
public final class OpenTelemetryOptions {

    private final List<String> capturedRequestHeaders;
    private final List<String> capturedResponseHeaders;
    private final boolean enableMetrics;
    private final OpenTelemetry openTelemetry;

    // Client-specific options (ignored by server filters)
    private final String componentName;

    OpenTelemetryOptions(
            final List<String> capturedRequestHeaders,
            final List<String> capturedResponseHeaders,
            final boolean enableMetrics,
            final OpenTelemetry openTelemetry,
            final String componentName) {
        this.capturedRequestHeaders = capturedRequestHeaders;
        this.capturedResponseHeaders = capturedResponseHeaders;
        this.enableMetrics = enableMetrics;
        this.openTelemetry = openTelemetry;
        this.componentName = componentName;
    }

    /**
     * List of request headers to be captured as extra span attributes.
     *
     * @return List of request headers to be captured as extra span attributes
     * @see HttpClientAttributesExtractorBuilder#setCapturedRequestHeaders(List)
     * @see HttpServerAttributesExtractorBuilder#setCapturedRequestHeaders(List)
     */
    public List<String> capturedRequestHeaders() {
        return capturedRequestHeaders;
    }

    /**
     * List of response headers to be captured as extra span attributes.
     *
     * @return List of response headers to be captured as extra span attributes.
     * @see HttpClientAttributesExtractorBuilder#setCapturedResponseHeaders(List)
     * @see HttpServerAttributesExtractorBuilder#setCapturedResponseHeaders(List)
     */
    public List<String> capturedResponseHeaders() {
        return capturedResponseHeaders;
    }

    /**
     * Whether to enable operation metrics or not.
     *
     * @return {@code true} when operation metrics should be enabled, {@code false} otherwise
     * @see InstrumenterBuilder#addOperationMetrics(OperationMetrics)
     * @see HttpClientMetrics
     * @see HttpServerMetrics
     */
    public boolean enableMetrics() {
        return enableMetrics;
    }

    /**
     * The {@link OpenTelemetry} instance to use for creating spans.
     *
     * @return the {@link OpenTelemetry} instance
     */
    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    /**
     * The component name used during building new spans.
     * <p>
     * This is a client-specific option that maps to the {@code peer.service} attribute
     * and will be ignored when used with server filters.
     *
     * @return the component name
     */
    public String componentName() {
        return componentName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpenTelemetryOptions)) {
            return false;
        }

        final OpenTelemetryOptions that = (OpenTelemetryOptions) o;
        if (enableMetrics != that.enableMetrics) {
            return false;
        }
        if (!capturedRequestHeaders.equals(that.capturedRequestHeaders)) {
            return false;
        }
        return capturedResponseHeaders.equals(that.capturedResponseHeaders);
    }

    @Override
    public int hashCode() {
        int result = capturedRequestHeaders.hashCode();
        result = 31 * result + capturedResponseHeaders.hashCode();
        result = 31 * result + (enableMetrics ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OpenTelemetryOptions{" +
                "capturedRequestHeaders=" + capturedRequestHeaders +
                ", capturedResponseHeaders=" + capturedResponseHeaders +
                ", enableMetrics=" + enableMetrics +
                ", openTelemetry=" + openTelemetry +
                ", componentName='" + componentName + '\'' +
                '}';
    }

    /** A builder for {@link OpenTelemetryOptions}. */
    public static final class Builder {
        private List<String> capturedRequestHeaders = emptyList();
        private List<String> capturedResponseHeaders = emptyList();
        private boolean enableMetrics;
        private OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

        // Client-specific options (ignored by server filters)
        private String componentName = "";

        /**
         * Create a copy of an existing builder.
         * @param openTelemetryOptions the {@link Builder} to copy.
         */
        public Builder(OpenTelemetryOptions openTelemetryOptions) {
            this.capturedRequestHeaders = openTelemetryOptions.capturedRequestHeaders;
            this.capturedResponseHeaders = openTelemetryOptions.capturedResponseHeaders;
            this.enableMetrics = openTelemetryOptions.enableMetrics;
            this.openTelemetry = openTelemetryOptions.openTelemetry;
            this.componentName = openTelemetryOptions.componentName;
        }

        /**
         * Create a builder using the default options.
         */
        public Builder() {
        }

        /**
         * Add the headers to be captured as extra span attributes.
         *
         * @param capturedRequestHeaders extra headers to be captured in client/server requests and
         *     added as extra span attributes
         * @return {@code this}
         * @see #capturedRequestHeaders()
         * @see HttpClientAttributesExtractorBuilder#setCapturedRequestHeaders(List)
         * @see HttpServerAttributesExtractorBuilder#setCapturedRequestHeaders(List)
         */
        public Builder capturedRequestHeaders(final List<String> capturedRequestHeaders) {
            this.capturedRequestHeaders =
                    capturedRequestHeaders.isEmpty() ? emptyList() :
                            unmodifiableList(new ArrayList<>(capturedRequestHeaders));
            return this;
        }

        /**
         * Add the headers to be captured as extra span attributes.
         *
         * @param capturedResponseHeaders extra headers to be captured in client/server response and
         *     added as extra span attributes
         * @return {@code this}
         * @see #capturedResponseHeaders()
         * @see HttpClientAttributesExtractorBuilder#setCapturedResponseHeaders(List)
         * @see HttpServerAttributesExtractorBuilder#setCapturedResponseHeaders(List)
         */
        public Builder capturedResponseHeaders(final List<String> capturedResponseHeaders) {
            this.capturedResponseHeaders =
                    capturedResponseHeaders.isEmpty() ? emptyList() :
                            unmodifiableList(new ArrayList<>(capturedResponseHeaders));
            return this;
        }

        /**
         * Whether to enable operation metrics or not.
         *
         * @param enableMetrics whether to enable operation metrics or not
         * @return {@code this}
         * @see #enableMetrics()
         * @see InstrumenterBuilder#addOperationMetrics(OperationMetrics)
         * @see HttpClientMetrics
         * @see HttpServerMetrics
         */
        public Builder enableMetrics(final boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
            return this;
        }

        /**
         * Set the {@link OpenTelemetry} instance to use for creating spans.
         *
         * @param openTelemetry the {@link OpenTelemetry} instance
         * @return {@code this}
         */
        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = requireNonNull(openTelemetry, "openTelemetry");
            return this;
        }

        /**
         * Set the component name used during building new spans.
         * <p>
         * This is a client-specific option that maps to the {@code peer.service} attribute
         * and will be ignored when used with server filters.
         *
         * @param componentName the component name
         * @return {@code this}
         */
        public Builder componentName(String componentName) {
            this.componentName = requireNonNull(componentName, "componentName");
            return this;
        }

        /**
         * Builds a new {@link OpenTelemetryOptions}.
         *
         * @return a new {@link OpenTelemetryOptions}
         */
        public OpenTelemetryOptions build() {
            return new OpenTelemetryOptions(
                    capturedRequestHeaders, capturedResponseHeaders, enableMetrics,
                    openTelemetry, componentName);
        }
    }
}
