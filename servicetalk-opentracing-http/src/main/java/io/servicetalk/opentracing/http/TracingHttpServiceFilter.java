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

import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import javax.annotation.Nullable;

import static io.opentracing.Tracer.SpanBuilder;
import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_SERVER;

/**
 * A {@link StreamingHttpService} that supports open tracing.
 */
public class TracingHttpServiceFilter extends AbstractTracingHttpFilter implements StreamingHttpServiceFilterFactory {

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     */
    public TracingHttpServiceFilter(Tracer tracer,
                                    String componentName) {
        this(tracer, componentName, true);
    }

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param validateTraceKeyFormat {@code true} to validate the contents of the trace ids.
     */
    public TracingHttpServiceFilter(Tracer tracer,
                                    String componentName,
                                    boolean validateTraceKeyFormat) {
        super(tracer, componentName, validateTraceKeyFormat);
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
        return tracker.track(response).subscribeShareContext();
    }

    private ScopeTracker newTracker(final StreamingHttpRequest request) {
        SpanBuilder spanBuilder = tracer.buildSpan(getOperationName(componentName, request))
                .withTag(SPAN_KIND.getKey(), SPAN_KIND_SERVER)
                .withTag(HTTP_METHOD.getKey(), request.method().name())
                .withTag(HTTP_URL.getKey(), request.path());
        SpanContext parentSpanContext = tracer.extract(formatter, request.headers());
        if (parentSpanContext != null) {
            spanBuilder = spanBuilder.asChildOf(parentSpanContext);
        }
        Scope scope = spanBuilder.startActive(true);
        return new ServiceScopeTracker(scope, parentSpanContext);
    }

    private final class ServiceScopeTracker extends ScopeTracker {

        @Nullable
        private SpanContext parentSpanContext;

        ServiceScopeTracker(final Scope scope, @Nullable final SpanContext parentSpanContext) {
            super(scope);
            this.parentSpanContext = parentSpanContext;
        }

        @Override
        void onResponseMeta(final HttpResponseMetaData metaData) {
            super.onResponseMeta(metaData);
            if (injectSpanContextIntoResponse(parentSpanContext)) {
                tracer.inject(currentScope.span().context(), formatter, metaData.headers());
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
