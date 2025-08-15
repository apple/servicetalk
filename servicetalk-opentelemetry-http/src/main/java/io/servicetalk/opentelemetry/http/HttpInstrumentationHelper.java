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
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.INSTRUMENTATION_SCOPE_NAME;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.PEER_SERVICE;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.withContext;

/**
 * Helper class that encapsulates HTTP-specific OpenTelemetry instrumentation logic.
 * <p>
 * This helper handles the creation of HTTP instrumenters and provides methods to track
 * HTTP requests with proper span lifecycle management and HTTP semantic conventions.
 */
final class HttpInstrumentationHelper extends InstrumentationHelper {

    private final Instrumenter<RequestInfo, HttpResponseMetaData> instrumenter;
    private final boolean isClient;

    private HttpInstrumentationHelper(boolean isClient, Instrumenter<RequestInfo, HttpResponseMetaData> instrumenter,
                                      boolean ignoreSpanSuppression) {
        super(instrumenter, ignoreSpanSuppression);
        this.instrumenter = instrumenter;
        this.isClient = isClient;
    }

    /**
     * Tracks an HTTP request using HTTP-specific OpenTelemetry instrumentation.
     *
     * @param requestHandler function to execute the actual request
     * @param requestInfo the HTTP request and connection info
     * @param parentContext the currently active context
     * @return instrumented response single
     */
    @Override
    Single<StreamingHttpResponse> doTrackRequest(
            Function<StreamingHttpRequest, Single<StreamingHttpResponse>> requestHandler,
            RequestInfo requestInfo,
            Context parentContext) {
        final Context context = instrumenter.start(parentContext, requestInfo);
        try (Scope unused = context.makeCurrent()) {
            final HttpScopeTracker tracker = isClient ? HttpScopeTracker.client(context, requestInfo, instrumenter) :
                    HttpScopeTracker.server(context, requestInfo, instrumenter);
            try {
                Single<StreamingHttpResponse> response = requestHandler.apply(requestInfo.request());
                return withContext(tracker.track(response), context);
            } catch (Throwable t) {
                tracker.onError(t);
                return Single.failed(t);
            }
        }
    }

    /**
     * Creates an HTTP server instrumentation helper.
     *
     * @param builder OpenTelemetryHttpServiceFilter configuration options
     * @return server instrumentation helper
     */
    static HttpInstrumentationHelper forServer(OpenTelemetryHttpServiceFilter.Builder builder) {
        OpenTelemetry openTelemetry = builder.openTelemetry;
        SpanNameExtractor<RequestInfo> serverSpanNameExtractor =
                HttpSpanNameExtractor.create(HttpAttributesGetter.SERVER_INSTANCE);
        InstrumenterBuilder<RequestInfo, HttpResponseMetaData> serverInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, serverSpanNameExtractor);
        serverInstrumenterBuilder.setSpanStatusExtractor(HttpSpanStatusExtractor.SERVER_INSTANCE);

        serverInstrumenterBuilder
                .addAttributesExtractor(HttpServerAttributesExtractor
                        .builder(HttpAttributesGetter.SERVER_INSTANCE)
                        .setCapturedRequestHeaders(builder.capturedRequestHeaders)
                        .setCapturedResponseHeaders(builder.capturedResponseHeaders)
                        .build());
        if (builder.enableMetrics) {
            serverInstrumenterBuilder.addOperationMetrics(HttpServerMetrics.get());
        }

        Instrumenter<RequestInfo, HttpResponseMetaData> instrumenter =
                serverInstrumenterBuilder.buildServerInstrumenter(RequestHeadersPropagatorGetter.INSTANCE);

        return new HttpInstrumentationHelper(false, instrumenter, builder.ignoreSpanSuppression);
    }

    /**
     * Creates an HTTP client instrumentation helper.
     *
     * @param builder OpenTelemetry configuration options
     * @return client instrumentation helper
     */
    static HttpInstrumentationHelper forClient(final OpenTelemetryHttpRequesterFilter.Builder builder) {
        OpenTelemetry openTelemetry = builder.openTelemetry;
        SpanNameExtractor<RequestInfo> clientSpanNameExtractor =
                HttpSpanNameExtractor.create(HttpAttributesGetter.CLIENT_INSTANCE);
        InstrumenterBuilder<RequestInfo, HttpResponseMetaData> clientInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, clientSpanNameExtractor);
        clientInstrumenterBuilder
                .setSpanStatusExtractor(HttpSpanStatusExtractor.CLIENT_INSTANCE)
                .addAttributesExtractor(new DeferredHttpClientAttributesExtractor(builder));

        if (builder.enableMetrics) {
            clientInstrumenterBuilder.addOperationMetrics(HttpClientMetrics.get());
        }
        String componentName = builder.componentName;
        if (!componentName.isEmpty()) {
            clientInstrumenterBuilder.addAttributesExtractor(
                    AttributesExtractor.constant(PEER_SERVICE, componentName));
        }

        Instrumenter<RequestInfo, HttpResponseMetaData> instrumenter =
                clientInstrumenterBuilder.buildClientInstrumenter(RequestHeadersPropagatorSetter.INSTANCE);

        return new HttpInstrumentationHelper(true, instrumenter, builder.ignoreSpanSuppression);
    }

    private static final class DeferredHttpClientAttributesExtractor implements
            AttributesExtractor<RequestInfo, HttpResponseMetaData> {

        private final AttributesExtractor<RequestInfo, HttpResponseMetaData> delegate;

        DeferredHttpClientAttributesExtractor(OpenTelemetryHttpRequesterFilter.Builder builder) {
            this.delegate = HttpClientAttributesExtractor
                    .builder(HttpAttributesGetter.CLIENT_INSTANCE)
                    .setCapturedRequestHeaders(builder.capturedRequestHeaders)
                    .setCapturedResponseHeaders(builder.capturedResponseHeaders)
                    .build();
        }

        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext,
                            RequestInfo requestInfo) {
            // noop: we will defer this until the `onEnd` call.
        }

        @Override
        public void onEnd(AttributesBuilder attributes, Context context,
                          RequestInfo requestInfo, @Nullable HttpResponseMetaData responseMetaData,
                          @Nullable Throwable error) {
            delegate.onStart(attributes, context, requestInfo);
            delegate.onEnd(attributes, context, requestInfo, responseMetaData, error);
        }
    }
}
