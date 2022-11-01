/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
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
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * An HTTP filter that supports <a href="https://opentelemetry.io/docs/instrumentation/java/">open telemetry</a>.
 * <p>
 * Append this filter before others that are expected to see {@link Scope} for this request/response. Filters
 * appended after this filter that use operators with the <strong>after*</strong> prefix on
 * {@link io.servicetalk.http.api.StreamingHttpClient#request(StreamingHttpRequest) response meta data} or the
 * {@link StreamingHttpResponse#transformMessageBody(UnaryOperator)} response message body}
 * (e.g. {@link Publisher#afterFinally(Runnable)}) will execute after this filter invokes {@link Scope#close()} and
 * therefore will not see the {@link Span} for the current request/response.
 */
public final class OpenTelemetryHttpRequestFilter
    implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    private static final TextMapSetter<HttpHeaders> setter = HeadersPropagatorSetter.INSTANCE;

    private final String componentName;
    private final Tracer tracer;
    private final ContextPropagators propagators;

    /**
     * Create a new instance.
     *
     * @param openTelemetry the {@link OpenTelemetry}.
     * @param componentName The component name used during building new spans.
     */
    public OpenTelemetryHttpRequestFilter(final OpenTelemetry openTelemetry, String componentName) {
        this.tracer = openTelemetry.getTracer("io.servicetalk");
        this.propagators = openTelemetry.getPropagators();
        this.componentName = Objects.requireNonNull(componentName);
    }

    /**
     * Create a new instance, searching for any instance of an opentelemetry available.
     *
     * @param componentName The component name used during building new spans.
     */
    public OpenTelemetryHttpRequestFilter(String componentName) {
        this(GlobalOpenTelemetry.get(), componentName);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
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
        Context context = Context.current();
        final Span span = RequestTagExtractor.reportTagsAndStart(tracer
            .spanBuilder(componentName)
            .setParent(context)
            .setSpanKind(SpanKind.CLIENT), request);

        final Scope scope = span.makeCurrent();
        final ScopeTracker tracker = new ScopeTracker(scope, span);
        Single<StreamingHttpResponse> response;
        try {
            propagators.getTextMapPropagator().inject(Context.current(), request.headers(), setter);
            response = delegate.request(request);
        } catch (Throwable t) {
            tracker.onError(t);
            return Single.failed(t);
        }
        return tracker.track(response);
    }
}
