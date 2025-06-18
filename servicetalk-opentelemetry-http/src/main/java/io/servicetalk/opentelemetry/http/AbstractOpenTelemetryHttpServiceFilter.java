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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;

abstract class AbstractOpenTelemetryHttpServiceFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpServiceFilterFactory {
    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

    AbstractOpenTelemetryHttpServiceFilter(final OpenTelemetry openTelemetry,
                                           final OpenTelemetryOptions openTelemetryOptions) {
        SpanNameExtractor<HttpRequestMetaData> serverSpanNameExtractor =
                HttpSpanNameExtractor.create(ServiceTalkHttpAttributesGetter.SERVER_INSTANCE);
        InstrumenterBuilder<HttpRequestMetaData, HttpResponseMetaData> serverInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, Singletons.INSTRUMENTATION_SCOPE_NAME, serverSpanNameExtractor);
        serverInstrumenterBuilder.setSpanStatusExtractor(ServicetalkSpanStatusExtractor.SERVER_INSTANCE);

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
