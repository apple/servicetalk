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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;

/**
 * A {@link StreamingHttpService} that supports
 * <a href="https://opentelemetry.io/docs/instrumentation/java/">open telemetry</a>.
 * <p>
 * The filter gets a {@link Tracer} with {@value #INSTRUMENTATION_SCOPE_NAME} instrumentation scope name.
 * <p>
 * This filter propagates the OpenTelemetry {@link Context} so the ordering of filters is crucial.
 * <ul>
 *     <li>Append this filter before others that are expected to see {@link Context} for this request/response.</li>
 *     <li>If auto-draining of the request body is desired, add the
 *     {@link io.servicetalk.http.utils.HttpRequestAutoDrainingServiceFilter} immediately after.</li>
 *     <li>To ensure tracing sees the same result status codes as the calling client, add the
 *     {@link io.servicetalk.http.api.HttpExceptionMapperServiceFilter} after this filter.</li>
 *     <li>If you intend to use a {@link io.servicetalk.http.api.HttpLifecycleObserver}, add it using the the
 *     HttpLifecycleObserverServiceFilter after the tracing filter to ensure the correct span information is present.
 *     </li>
 * </ul>
 * Be sure to use the
 * {@link io.servicetalk.http.api.HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)}
 * method for adding these filters as non-offloading filters are always added before offloading filters.
 */
public final class OpenTelemetryHttpServerFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpServiceFilterFactory {
    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

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
        this(openTelemetry, DEFAULT_OPTIONS);
    }

    /**
     * Create a new instance using the {@link OpenTelemetry} from {@link GlobalOpenTelemetry#get()} with default
     * {@link OpenTelemetryOptions}.
     */
    public OpenTelemetryHttpServerFilter() {
        this(DEFAULT_OPTIONS);
    }

    /**
     * Create a new instance.
     *
     * @param openTelemetryOptions extra options to create the opentelemetry filter
     */
    public OpenTelemetryHttpServerFilter(final OpenTelemetryOptions openTelemetryOptions) {
        this(GlobalOpenTelemetry.get(), openTelemetryOptions);
    }

    OpenTelemetryHttpServerFilter(final OpenTelemetry openTelemetry, final OpenTelemetryOptions openTelemetryOptions) {
        SpanNameExtractor<HttpRequestMetaData> serverSpanNameExtractor =
                HttpSpanNameExtractor.create(ServiceTalkHttpAttributesGetter.SERVER_INSTANCE);
        InstrumenterBuilder<HttpRequestMetaData, HttpResponseMetaData> serverInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, serverSpanNameExtractor);
        serverInstrumenterBuilder.setSpanStatusExtractor(ServicetalkSpanStatusExtractor.INSTANCE);

        serverInstrumenterBuilder
                .addAttributesExtractor(HttpServerAttributesExtractor
                        .builder(ServiceTalkHttpAttributesGetter.SERVER_INSTANCE)
                        .setCapturedRequestHeaders(openTelemetryOptions.capturedRequestHeaders())
                        .setCapturedResponseHeaders(openTelemetryOptions.capturedResponseHeaders())
                        .build());
        if (openTelemetryOptions.enableMetrics()) {
            serverInstrumenterBuilder.addOperationMetrics(HttpServerMetrics.get());
        }
        instrumenter =
                serverInstrumenterBuilder.buildServerInstrumenter(RequestHeadersPropagatorGetter.INSTANCE);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return trackRequest(delegate(), ctx, request, responseFactory);
            }
        };
    }

    private Single<StreamingHttpResponse> trackRequest(final StreamingHttpService delegate,
                                                       final HttpServiceContext ctx,
                                                       final StreamingHttpRequest request,
                                                       final StreamingHttpResponseFactory responseFactory) {
        final Context parentContext = Context.root();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return delegate.handle(ctx, request, responseFactory);
        }
        final Context context = instrumenter.start(parentContext, request);
        try (Scope unused = context.makeCurrent()) {
            final ScopeTracker tracker = ScopeTracker.server(context, request, instrumenter);
            try {
                Single<StreamingHttpResponse> response = delegate.handle(ctx, request, responseFactory);
                return withContext(tracker.track(response), context);
            } catch (Throwable t) {
                tracker.onError(t);
                return Single.failed(t);
            }
        }
    }
}
