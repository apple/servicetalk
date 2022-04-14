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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_SERVER;

/**
 * A {@link StreamingHttpService} that supports open tracing.
 * <p>
 * Append this filter before others that are expected to to see {@link Scope} for this request/response. Filters
 * appended after this filter that use operators with the <strong>after*</strong> prefix on
 * {@link StreamingHttpService#handle(HttpServiceContext, StreamingHttpRequest, StreamingHttpResponseFactory)
 * response meta data} or the {@link StreamingHttpResponse#transformMessageBody(UnaryOperator) response message body}
 * (e.g. {@link Publisher#afterFinally(Runnable)}) will execute after this filter invokes {@link Scope#close()} and
 * therefore will not see the {@link Span} for the current request/response.
 */
public class TracingHttpServiceFilter extends AbstractTracingHttpFilter implements StreamingHttpServiceFilterFactory {

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     */
    public TracingHttpServiceFilter(Tracer tracer, String componentName) {
        this(tracer, componentName, true);
    }

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param validateTraceKeyFormat {@code true} to validate the contents of the trace ids.
     */
    public TracingHttpServiceFilter(Tracer tracer, String componentName, boolean validateTraceKeyFormat) {
        super(tracer, componentName, validateTraceKeyFormat);
    }

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param format the {@link Format} to use to inject/extract trace info to/from {@link HttpHeaders}.
     */
    public TracingHttpServiceFilter(final Tracer tracer, final String componentName, final Format<HttpHeaders> format) {
        super(tracer, componentName, format);
    }

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param format the {@link Format} to use to inject/extract trace info to/from {@link TextMap}.
     * @param componentName The component name used during building new spans.
     */
    public TracingHttpServiceFilter(final Tracer tracer, final Format<TextMap> format, final String componentName) {
        super(tracer, format, componentName);
    }

    @Override
    public final StreamingHttpServiceFilter create(final StreamingHttpService service) {
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
        ScopeTracker tracker = newTracker(request);
        Single<StreamingHttpResponse> response;
        try {
            response = delegate.handle(ctx, request, responseFactory);
        } catch (Throwable t) {
            tracker.onError(t);
            return Single.failed(t);
        }
        return tracker.track(response);
    }

    private ScopeTracker newTracker(final StreamingHttpRequest request) {
        SpanContext parentSpanContext = extractor.apply(request.headers());
        Span span = tracer.buildSpan(getOperationName(componentName, request))
                .asChildOf(parentSpanContext)
                .withTag(SPAN_KIND.getKey(), SPAN_KIND_SERVER)
                .withTag(HTTP_METHOD.getKey(), request.method().name())
                .withTag(HTTP_URL.getKey(), request.path())
                .start();
        Scope scope = tracer.activateSpan(span);
        return new ServiceScopeTracker(scope, span, parentSpanContext);
    }

    private final class ServiceScopeTracker extends ScopeTracker {

        @Nullable
        private final SpanContext parentSpanContext;

        ServiceScopeTracker(Scope scope, final Span span, @Nullable final SpanContext parentSpanContext) {
            super(scope, span);
            this.parentSpanContext = parentSpanContext;
        }

        @Override
        void onResponseMeta(final HttpResponseMetaData metaData) {
            super.onResponseMeta(metaData);
            if (injectSpanContextIntoResponse(parentSpanContext)) {
                injector.accept(getSpan().context(), metaData.headers());
            }
        }
    }

    /**
     * Get the operation name to build the span with.
     * @param componentName The component name.
     * @param metaData The {@link HttpRequestMetaData}.
     * @return the operation name to build the span with.
     */
    protected String getOperationName(String componentName, HttpRequestMetaData metaData) {
        return componentName + ' ' + metaData.method().name() + ' ' + metaData.requestTarget();
    }

    /**
     * Determine if the current span context should be injected into the response.
     * @param parentSpanContext The parent span extracted from the request.
     * @return {@code true} if the current span context should be injected into the response.
     */
    protected boolean injectSpanContextIntoResponse(@Nullable SpanContext parentSpanContext) {
        return parentSpanContext == null;
    }
}
