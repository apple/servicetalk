/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceStateFormat;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_CLIENT;
import static io.servicetalk.opentracing.http.TracingHttpHeadersFormatter.traceStateFormatter;
import static io.servicetalk.opentracing.http.TracingUtils.handlePrematureException;
import static io.servicetalk.opentracing.http.TracingUtils.tagErrorAndClose;
import static io.servicetalk.opentracing.http.TracingUtils.tracingMapper;
import static java.util.Objects.requireNonNull;

/**
 * An HTTP filter that supports open tracing.
 */
public class TracingHttpRequesterFilter implements HttpClientFilterFactory, HttpConnectionFilterFactory {

    private final Tracer tracer;
    private final String componentName;
    private final InMemoryTraceStateFormat<HttpHeaders> formatter;

    /**
     * Create a new instance.
     *
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     */
    public TracingHttpRequesterFilter(Tracer tracer,
                                      String componentName) {
        this(tracer, componentName, true);
    }

    /**
     * Create a new instance.
     *
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param validateTraceKeyFormat {@code true} to validate the contents of the trace ids.
     */
    public TracingHttpRequesterFilter(Tracer tracer,
                                      String componentName,
                                      boolean validateTraceKeyFormat) {
        this.tracer = requireNonNull(tracer);
        this.componentName = requireNonNull(componentName);
        this.formatter = traceStateFormatter(validateTraceKeyFormat);
    }

    @Override
    public StreamingHttpClientFilter create(final StreamingHttpClient client, final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return TracingHttpRequesterFilter.this.request(delegate, strategy, request);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final StreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return TracingHttpRequesterFilter.this.request(delegate(), strategy, request);
            }
        };
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request) {
        return Single.deferShareContext(() -> {
            Scope tempChildScope = null;
            // We may interact with the Scope/Span from multiple threads (Subscriber & Subscription), and the
            // Scope/Span class does not provide thread safe behavior. So we ensure that we only close (and add
            // trailing meta data) from a single thread at any given time via this atomic variable.
            AtomicBoolean tempScopeClosed = null;
            final Single<StreamingHttpResponse> responseSingle;
            try {
                SpanBuilder spanBuilder = tracer.buildSpan(componentName)
                        .withTag(SPAN_KIND.getKey(), SPAN_KIND_CLIENT)
                        .withTag(HTTP_METHOD.getKey(), request.method().methodName())
                        .withTag(HTTP_URL.getKey(), request.path());
                Scope currentScope = tracer.scopeManager().active();
                if (currentScope != null) {
                    spanBuilder = spanBuilder.asChildOf(currentScope.span());
                }

                tempChildScope = spanBuilder.startActive(true);
                tempScopeClosed = new AtomicBoolean();
                tracer.inject(tempChildScope.span().context(), formatter, request.headers());
                responseSingle = requireNonNull(delegate.request(strategy, request));
            } catch (Throwable cause) {
                handlePrematureException(tempChildScope, tempScopeClosed);
                throw cause;
            }
            final Scope childScope = tempChildScope;
            final AtomicBoolean scopeClosed = tempScopeClosed;
            return responseSingle.map(tracingMapper(childScope, scopeClosed, TracingHttpRequesterFilter.this::isError))
                    .doOnError(cause -> tagErrorAndClose(childScope, scopeClosed))
                    .doOnCancel(() -> tagErrorAndClose(childScope, scopeClosed));
        });
    }

    /**
     * Determine if a {@link HttpResponseMetaData} should be considered an error from a tracing perspective.
     * @param metaData The {@link HttpResponseMetaData} to test.
     * @return {@code true} if the {@link HttpResponseMetaData} should be considered an error for tracing.
     */
    protected boolean isError(HttpResponseMetaData metaData) {
        return TracingUtils.isError(metaData);
    }
}
