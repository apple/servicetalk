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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.function.UnaryOperator;

/**
 * An HTTP filter that supports <a href="https://opentelemetry.io/docs/instrumentation/java/">open telemetry</a>.
 * <p>
 * The filter gets a {@link Tracer} with {@value Singletons#INSTRUMENTATION_SCOPE_NAME} instrumentation scope name.
 * <p>
 * Append this filter before others that are expected to see {@link Scope} for this request/response. Filters
 * appended after this filter that use operators with the <strong>after*</strong> prefix on
 * {@link io.servicetalk.http.api.StreamingHttpClient#request(StreamingHttpRequest) response meta data} or the
 * {@link StreamingHttpResponse#transformMessageBody(UnaryOperator)} response message body}
 * (e.g. {@link Publisher#afterFinally(Runnable)}) will execute after this filter invokes {@link Scope#close()} and
 * therefore will not see the {@link Span} for the current request/response.
 */
public final class OpenTelemetryHttpRequesterFilter extends AbstractOpenTelemetryHttpRequesterFilter {

    /**
     * Create a new instance, searching for any instance of an opentelemetry available.
     *
     * @param componentName The component name used during building new spans.
     */
    public OpenTelemetryHttpRequesterFilter(final String componentName) {
        this(componentName, Singletons.DEFAULT_OPTIONS);
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available.
     *
     * @param componentName        The component name used during building new spans.
     * @param opentelemetryOptions extra options to create the opentelemetry filter
     */
    public OpenTelemetryHttpRequesterFilter(final String componentName,
                                            final OpenTelemetryOptions opentelemetryOptions) {
        this(GlobalOpenTelemetry.get(), componentName, opentelemetryOptions);
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available,
     * using the hostname as the component name.
     */
    public OpenTelemetryHttpRequesterFilter() {
        this("");
    }

    OpenTelemetryHttpRequesterFilter(final OpenTelemetry openTelemetry, String componentName,
                                     final OpenTelemetryOptions opentelemetryOptions) {
        super(openTelemetry, componentName, opentelemetryOptions);
    }
}
