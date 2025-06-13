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
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;

import javax.annotation.Nullable;

abstract class AbstractOpenTelemetryHttpRequesterFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");

    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

    /**
     * Create a new instance.
     *
     * @param openTelemetry        the {@link OpenTelemetry}.
     * @param componentName        The component name used during building new spans.
     * @param opentelemetryOptions extra options to create the opentelemetry filter.
     */
    AbstractOpenTelemetryHttpRequesterFilter(final OpenTelemetry openTelemetry, String componentName,
                                             final OpenTelemetryOptions opentelemetryOptions) {
        SpanNameExtractor<HttpRequestMetaData> clientSpanNameExtractor =
                HttpSpanNameExtractor.create(ServiceTalkHttpAttributesGetter.CLIENT_INSTANCE);
        InstrumenterBuilder<HttpRequestMetaData, HttpResponseMetaData> clientInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, clientSpanNameExtractor);
        clientInstrumenterBuilder
                .setSpanStatusExtractor(ServicetalkSpanStatusExtractor.CLIENT_INSTANCE)
                .addAttributesExtractor(new DeferredClientAttributesExtractor(opentelemetryOptions));

        if (opentelemetryOptions.enableMetrics()) {
            clientInstrumenterBuilder.addOperationMetrics(HttpClientMetrics.get());
        }
        componentName = componentName.trim();
        if (!componentName.isEmpty()) {
            clientInstrumenterBuilder.addAttributesExtractor(
                    AttributesExtractor.constant(PEER_SERVICE, componentName));
        }
        instrumenter =
                clientInstrumenterBuilder.buildClientInstrumenter(RequestHeadersPropagatorSetter.INSTANCE);
    }

    @Override
    public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return Single.defer(() -> trackRequest(delegate, request).shareContextOnSubscribe());
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return Single.defer(() -> trackRequest(delegate(), request).shareContextOnSubscribe());
            }
        };
    }

    private Single<StreamingHttpResponse> trackRequest(final StreamingHttpRequester delegate,
                                                       final StreamingHttpRequest request) {
        // This should be (essentially) the first filter in the client filter chain, so here is where we initialize
        // all the little bits and pieces that will end up being part of the request context. This includes setting
        // up the retry counter context entry since retries will happen further down in the filter chain.
        Context current = Context.current();
        if (!instrumenter.shouldStart(current, request)) {
            return delegate.request(request);
        }
        final Context context = instrumenter.start(current, request);
        try (Scope unused = context.makeCurrent()) {
            final ScopeTracker tracker = ScopeTracker.client(context, request, instrumenter);
            try {
                Single<StreamingHttpResponse> response = delegate.request(request);
                return withContext(tracker.track(response), context);
            } catch (Throwable t) {
                tracker.onError(t);
                return Single.failed(t);
            }
        }
    }

    private static final class DeferredClientAttributesExtractor implements
            AttributesExtractor<HttpRequestMetaData, HttpResponseMetaData> {

        private final AttributesExtractor<HttpRequestMetaData, HttpResponseMetaData> delegate;

        private DeferredClientAttributesExtractor(OpenTelemetryOptions openTelemetryOptions) {
            this.delegate = HttpClientAttributesExtractor
                    .builder(ServiceTalkHttpAttributesGetter.CLIENT_INSTANCE)
                    .setCapturedRequestHeaders(openTelemetryOptions.capturedRequestHeaders())
                    .setCapturedResponseHeaders(openTelemetryOptions.capturedResponseHeaders())
                    .build();
        }

        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, HttpRequestMetaData metaData) {
            // noop: we will defer this until the `onEnd` call.
        }

        @Override
        public void onEnd(AttributesBuilder attributes, Context context, HttpRequestMetaData requestMetaData,
                          @Nullable HttpResponseMetaData responseMetaData, @Nullable Throwable error) {
            delegate.onStart(attributes, context, requestMetaData);
            delegate.onEnd(attributes, context, requestMetaData, responseMetaData, error);
        }
    }
}
