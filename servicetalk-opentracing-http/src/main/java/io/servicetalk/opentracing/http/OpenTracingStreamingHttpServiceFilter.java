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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.opentracing.core.internal.InMemoryTraceStateFormat;

import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import javax.annotation.Nullable;

import static io.opentracing.Tracer.SpanBuilder;
import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_SERVER;
import static io.servicetalk.opentracing.http.OpenTracingHttpHeadersFormatter.traceStateFormatter;
import static io.servicetalk.opentracing.http.TracingUtils.tagErrorAndClose;
import static io.servicetalk.opentracing.http.TracingUtils.tracingMapper;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpService} that supports open tracing.
 */
public class OpenTracingStreamingHttpServiceFilter extends StreamingHttpService {
    private final StreamingHttpService next;
    private final Tracer tracer;
    private final String componentName;
    private final InMemoryTraceStateFormat<HttpHeaders> formatter;

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param next The next {@link StreamingHttpService} in the filter chain.
     */
    public OpenTracingStreamingHttpServiceFilter(Tracer tracer,
                                                 String componentName,
                                                 StreamingHttpService next) {
        this(tracer, componentName, next, true);
    }

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param validateTraceKeyFormat {@code true} to validate the contents of the trace ids.
     * @param next The next {@link StreamingHttpService} in the filter chain.
     */
    public OpenTracingStreamingHttpServiceFilter(Tracer tracer,
                                                 String componentName,
                                                 StreamingHttpService next,
                                                 boolean validateTraceKeyFormat) {
        this.tracer = requireNonNull(tracer);
        this.componentName = requireNonNull(componentName);
        this.next = requireNonNull(next);
        formatter = traceStateFormatter(validateTraceKeyFormat);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                SpanContext parentSpanContext = tracer.extract(formatter, request.headers());
                SpanBuilder spanBuilder = tracer.buildSpan(getOperationName(componentName, request))
                        .withTag(SPAN_KIND.getKey(), SPAN_KIND_SERVER)
                        .withTag(HTTP_METHOD.getKey(), request.method().getName())
                        .withTag(HTTP_URL.getKey(), request.path());
                if (parentSpanContext != null) {
                    spanBuilder = spanBuilder.asChildOf(parentSpanContext);
                }

                Scope currentScope = spanBuilder.startActive(true);
                next.handle(ctx, request, responseFactory).map(resp -> {
                    if (injectSpanContextIntoResponse(parentSpanContext)) {
                        tracer.inject(currentScope.span().context(), formatter, resp.headers());
                    }
                    return tracingMapper(resp, currentScope, OpenTracingStreamingHttpServiceFilter.this::isError);
                }).doOnError(cause -> tagErrorAndClose(currentScope))
                  .doOnCancel(() -> tagErrorAndClose(currentScope))
                  .subscribe(subscriber);
            }
        };
    }

    @Override
    public Completable closeAsync() {
        return next.closeAsync();
    }

    /**
     * Get the operation name to build the span with.
     * @param componentName The component name.
     * @param metaData The {@link HttpRequestMetaData}.
     * @return the operation name to build the span with.
     */
    protected String getOperationName(String componentName, HttpRequestMetaData metaData) {
        return componentName + ' ' + metaData.method().getName() + ' ' + metaData.requestTarget();
    }

    /**
     * Determine if the current span context should be injected into the response.
     * @param parentSpanContext The parent span extracted from the request.
     * @return {@code true} if the current span context should be injected into the response.
     */
    protected boolean injectSpanContextIntoResponse(@Nullable SpanContext parentSpanContext) {
        return parentSpanContext == null;
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
