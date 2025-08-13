/*
 * Copyright © 2022-2023 Apple Inc. and the ServiceTalk project authors
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
 * The filter gets a {@link Tracer} with {@value #INSTRUMENTATION_SCOPE_NAME} instrumentation scope name.
 * <p>
 * Append this filter before others that are expected to see {@link Scope} for this request/response. Filters
 * appended after this filter that use operators with the <strong>after*</strong> prefix on
 * {@link io.servicetalk.http.api.StreamingHttpClient#request(StreamingHttpRequest) response meta data} or the
 * {@link StreamingHttpResponse#transformMessageBody(UnaryOperator)} response message body.
 * (e.g. {@link Publisher#afterFinally(Runnable)}) will execute after this filter invokes {@link Scope#close()} and
 * therefore will not see the {@link Span} for the current request/response.
 * @deprecated use {@link OpenTelemetryHttpRequesterFilter.Builder} instead.
 */
@Deprecated // FIXME: 0.43 - remove deprecated class
public final class OpenTelemetryHttpRequestFilter extends AbstractOpenTelemetryHttpRequesterFilter {

    /**
     * Create a new instance.
     *
     * @param openTelemetry the {@link OpenTelemetry}.
     * @param componentName The component name used during building new spans.
     * @deprecated this method is internal, no user should be setting the {@link OpenTelemetry} as it is obtained by
     * using {@link GlobalOpenTelemetry#get()} and there should be no other implementations but the one available in
     * the classpath, this constructor will be removed in the future releases.
     * Use {@link OpenTelemetryHttpRequesterFilter.Builder} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    public OpenTelemetryHttpRequestFilter(final OpenTelemetry openTelemetry, String componentName) {
        super(new OpenTelemetryHttpRequesterFilter.Builder()
                .openTelemetry(openTelemetry)
                .componentName(componentName));
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available.
     *
     * @param componentName The component name used during building new spans.
     * @deprecated Use {@link OpenTelemetryHttpRequesterFilter.Builder} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    public OpenTelemetryHttpRequestFilter(final String componentName) {
        super(new OpenTelemetryHttpRequesterFilter.Builder().componentName(componentName));
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available.
     *
     * @param componentName        The component name used during building new spans.
     * @param opentelemetryOptions extra options to create the opentelemetry filter
     * @deprecated Use {@link OpenTelemetryHttpRequesterFilter.Builder} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    public OpenTelemetryHttpRequestFilter(final String componentName, final OpenTelemetryOptions opentelemetryOptions) {
        super(new OpenTelemetryHttpRequesterFilter.Builder()
                .applyOptions(opentelemetryOptions)
                .componentName(componentName));
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available,
     * using the hostname as the component name.
     * @deprecated Use {@link OpenTelemetryHttpRequesterFilter.Builder} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    public OpenTelemetryHttpRequestFilter() {
        super(new OpenTelemetryHttpRequesterFilter.Builder());
    }
}
