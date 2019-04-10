/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.http;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;

import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_CLIENT;

/**
 * An HTTP filter that supports open tracing.
 */
public class TracingHttpRequesterFilter extends AbstractTracingHttpFilter
        implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory,
                   HttpExecutionStrategyInfluencer {

    /**
     * Create a new instance.
     *
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     */
    public TracingHttpRequesterFilter(final Tracer tracer,
                                      final String componentName) {
        this(tracer, componentName, true);
    }

    /**
     * Create a new instance.
     *
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param validateTraceKeyFormat {@code true} to validate the contents of the trace ids.
     */
    public TracingHttpRequesterFilter(final Tracer tracer,
                                      final String componentName,
                                      boolean validateTraceKeyFormat) {
        super(tracer, componentName, validateTraceKeyFormat);
    }

    @Override
    public final StreamingHttpClientFilter create(final FilterableStreamingHttpClient client,
                                                  final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return Single.defer(() -> trackRequest(delegate, strategy, request));
            }
       };
    }

    @Override
    public final StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {

            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return Single.defer(() -> trackRequest(delegate(), strategy, request));
            }
       };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }

    private Single<StreamingHttpResponse> trackRequest(final StreamingHttpRequester delegate,
                                                       final HttpExecutionStrategy strategy,
                                                       final StreamingHttpRequest request) {
        ScopeTracker tracker = newTracker(request);
        Single<StreamingHttpResponse> response;
        try {
            response = delegate.request(strategy, request);
        } catch (Throwable t) {
            tracker.onError(t);
            return Single.failed(t);
        }
        return tracker.track(response).subscribeShareContext();
    }

    private ScopeTracker newTracker(final HttpRequestMetaData request) {
        SpanBuilder spanBuilder = tracer.buildSpan(componentName)
                .withTag(SPAN_KIND.getKey(), SPAN_KIND_CLIENT)
                .withTag(HTTP_METHOD.getKey(), request.method().name())
                .withTag(HTTP_URL.getKey(), request.path());
        final Span activeSpan = tracer.activeSpan();
        if (activeSpan != null) {
            spanBuilder = spanBuilder.asChildOf(activeSpan);
        }
        Scope scope = spanBuilder.startActive(true);
        tracer.inject(scope.span().context(), formatter, request.headers());
        return new ScopeTracker(scope);
    }
}
