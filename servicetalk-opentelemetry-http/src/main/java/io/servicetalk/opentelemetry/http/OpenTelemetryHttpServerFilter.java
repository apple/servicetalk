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

import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

/**
 * A {@link StreamingHttpService} that supports
 * <a href="https://opentelemetry.io/docs/instrumentation/java/">open telemetry</a>.
 * <p>
 * The filter gets a {@link Tracer} with {@value #INSTRUMENTATION_SCOPE_NAME} instrumentation scope name.
 * <p>
 * This filter propagates the OpenTelemetry {@link Context} (thus {@link Span}) so the ordering of filters is crucial.
 * <ul>
 *     <li>Append this filter before others that are expected to see the {@link Span} for this request/response.</li>
 *     <li>If you want to see the correct {@link Span} information for auto-drained requests
 *     (when a streaming request body was not consumed by the service), add the
 *     {@link io.servicetalk.http.utils.HttpRequestAutoDrainingServiceFilter} immediately after.</li>
 *     <li>To ensure tracing sees the same result status codes as the calling client, add the
 *     {@link io.servicetalk.http.api.HttpExceptionMapperServiceFilter} after this filter.</li>
 *     <li>If you intend to use a {@link io.servicetalk.http.api.HttpLifecycleObserver}, add it using the
 *     HttpLifecycleObserverServiceFilter after the tracing filter to ensure the correct {@link Span} information is
 *     present.</li>
 * </ul>
 * Be sure to use the
 * {@link io.servicetalk.http.api.HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)}
 * method for adding these filters as non-offloading filters are always added before offloading filters.
 * @deprecated use {@link OpenTelemetryHttpServiceFilter} instead.
 */
@Deprecated // FIXME: 0.43 - remove deprecated class
public final class OpenTelemetryHttpServerFilter extends AbstractOpenTelemetryHttpServiceFilter {

    /**
     * Create a new instance.
     *
     * @param openTelemetry the {@link OpenTelemetry}.
     * @deprecated this method is internal, no user should be setting the {@link OpenTelemetry} as it is obtained by
     * using {@link GlobalOpenTelemetry#get()} and there should be no other implementations but the one available in
     * the classpath, this constructor will be removed in the future releases.
     * Use {@link #OpenTelemetryHttpServerFilter(OpenTelemetryOptions)} or {@link #OpenTelemetryHttpServerFilter()}
     * instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    @SuppressWarnings("DeprecatedIsStillUsed")
    public OpenTelemetryHttpServerFilter(final OpenTelemetry openTelemetry) {
        super(new OpenTelemetryHttpServiceFilter.Builder().openTelemetry(openTelemetry));
    }

    /**
     * Create a new instance using the {@link OpenTelemetry} from {@link GlobalOpenTelemetry#get()} with default
     * {@link OpenTelemetryOptions}.
     */
    public OpenTelemetryHttpServerFilter() {
        super(new OpenTelemetryHttpServiceFilter.Builder());
    }

    /**
     * Create a new instance.
     *
     * @param openTelemetryOptions extra options to create the opentelemetry filter
     */
    public OpenTelemetryHttpServerFilter(final OpenTelemetryOptions openTelemetryOptions) {
        super(new OpenTelemetryHttpServiceFilter.Builder().applyOptions(openTelemetryOptions));
    }
}
