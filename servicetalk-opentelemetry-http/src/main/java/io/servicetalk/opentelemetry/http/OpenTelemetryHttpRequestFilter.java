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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;

import java.util.function.UnaryOperator;

/**
 * An HTTP filter that supports <a href="https://opentelemetry.io/docs/instrumentation/java/">open telemetry</a>.
 * <p>
 * The filter gets a {@link Tracer} with {@value #INSTRUMENTATION_SCOPE_NAME} instrumentation scope name.
 * <p>
 * Append this filter before others that are expected to see {@link Scope} for this request/response. Filters
 * appended after this filter that use operators with the <strong>after*</strong> prefix on
 * {@link io.servicetalk.http.api.StreamingHttpClient#request(StreamingHttpRequest) response meta data} or the
 * {@link StreamingHttpResponse#transformMessageBody(UnaryOperator)} response message body}
 * (e.g. {@link Publisher#afterFinally(Runnable)}) will execute after this filter invokes {@link Scope#close()} and
 * therefore will not see the {@link Span} for the current request/response.
 */
public final class OpenTelemetryHttpRequestFilter extends AbstractOpenTelemetryFilter
    implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

    /**
     * Create a new instance.
     *
     * @param openTelemetry the {@link OpenTelemetry}.
     * @param componentName The component name used during building new spans.
     * @deprecated this method is internal, no user should be setting the {@link OpenTelemetry} as it is obtained by
     * using {@link GlobalOpenTelemetry#get()} and there should be no other implementations but the one available in
     * the classpath, this constructor will be removed in the future releases.
     * Use {@link #OpenTelemetryHttpRequestFilter(String, OpenTelemetryOptions)} or
     * {@link #OpenTelemetryHttpRequestFilter()} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    @SuppressWarnings("DeprecatedIsStillUsed")
    public OpenTelemetryHttpRequestFilter(final OpenTelemetry openTelemetry, String componentName) {
        this(openTelemetry, componentName, DEFAULT_OPTIONS);
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available.
     *
     * @param componentName The component name used during building new spans.
     */
    public OpenTelemetryHttpRequestFilter(final String componentName) {
        this(componentName, DEFAULT_OPTIONS);
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available.
     *
     * @param componentName        The component name used during building new spans.
     * @param opentelemetryOptions extra options to create the opentelemetry filter
     */
    public OpenTelemetryHttpRequestFilter(final String componentName, final OpenTelemetryOptions opentelemetryOptions) {
        this(GlobalOpenTelemetry.get(), componentName, opentelemetryOptions);
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available,
     * using the hostname as the component name.
     */
    public OpenTelemetryHttpRequestFilter() {
        this("");
    }

    /**
     * Create a new instance.
     *
     * @param openTelemetry        the {@link OpenTelemetry}.
     * @param componentName        The component name used during building new spans.
     * @param opentelemetryOptions extra options to create the opentelemetry filter.
     */
    OpenTelemetryHttpRequestFilter(final OpenTelemetry openTelemetry, String componentName,
                                   final OpenTelemetryOptions opentelemetryOptions) {
        super(openTelemetry);
        SpanNameExtractor<HttpRequestMetaData> clientSpanNameExtractor =
                HttpSpanNameExtractor.create(ServiceTalkHttpAttributesGetter.CLIENT_INSTANCE);
        InstrumenterBuilder<HttpRequestMetaData, HttpResponseMetaData> clientInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, clientSpanNameExtractor);
        clientInstrumenterBuilder.setSpanStatusExtractor(ServicetalkSpanStatusExtractor.INSTANCE);

        clientInstrumenterBuilder
                .addAttributesExtractor(
                        HttpClientAttributesExtractor
                        .builder(ServiceTalkHttpAttributesGetter.CLIENT_INSTANCE,
                                ServiceTalkNetAttributesGetter.CLIENT_INSTANCE)
                        .setCapturedRequestHeaders(opentelemetryOptions.capturedRequestHeaders())
                        .setCapturedResponseHeaders(opentelemetryOptions.capturedResponseHeaders())
                        .build())
                .addAttributesExtractor(NetClientAttributesExtractor.create(
                        ServiceTalkNetAttributesGetter.CLIENT_INSTANCE));
        if (opentelemetryOptions.enableMetrics()) {
            clientInstrumenterBuilder.addOperationMetrics(HttpClientMetrics.get());
        }
        componentName = componentName.trim();
        if (!componentName.isEmpty()) {
            clientInstrumenterBuilder.addAttributesExtractor(
                    AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, componentName));
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
        final Context parentContext = Context.current();
        System.out.println(OpenTelemetryHttpRequestFilter.class.getSimpleName() + ": Current context: " + parentContext + ", thread: " + Thread.currentThread());
        final Context context = instrumenter.start(parentContext, request);

        final Scope scope = context.makeCurrent();
        final ScopeTracker tracker = new ScopeTracker(scope, context, request, instrumenter);
        Single<StreamingHttpResponse> response;
        try {
            response = delegate.request(request);
        } catch (Throwable t) {
            tracker.onError(t);
            return Single.failed(t);
        }
        return tracker.track(response);
    }
}
