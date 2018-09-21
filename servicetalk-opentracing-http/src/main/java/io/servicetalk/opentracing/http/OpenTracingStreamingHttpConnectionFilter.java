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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionAdapter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.opentracing.core.internal.InMemoryTraceStateFormat;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;

import static io.opentracing.tag.Tags.ERROR;
import static io.opentracing.tag.Tags.HTTP_METHOD;
import static io.opentracing.tag.Tags.HTTP_STATUS;
import static io.opentracing.tag.Tags.HTTP_URL;
import static io.opentracing.tag.Tags.SPAN_KIND;
import static io.opentracing.tag.Tags.SPAN_KIND_CLIENT;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.opentracing.http.OpenTracingHttpHeadersFormatter.FORMATTER_NO_VALIDATION;
import static io.servicetalk.opentracing.http.OpenTracingHttpHeadersFormatter.FORMATTER_VALIDATION;
import static io.servicetalk.opentracing.http.OpenTracingStreamingHttpServiceFilter.tagErrorAndClose;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpConnection} that supports open tracing.
 */
public class OpenTracingStreamingHttpConnectionFilter extends StreamingHttpConnectionAdapter {
    private final Tracer tracer;
    private final String componentName;
    private final InMemoryTraceStateFormat<HttpHeaders> formatter;

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param delegate The {@link StreamingHttpConnection} to delegate all calls to.
     */
    public OpenTracingStreamingHttpConnectionFilter(Tracer tracer,
                                                    String componentName,
                                                    StreamingHttpConnection delegate) {
        this(tracer, componentName, delegate, true);
    }

    /**
     * Create a new instance.
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param validateTraceKeyFormat {@code true} to validate the contents of the trace ids.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     */
    public OpenTracingStreamingHttpConnectionFilter(Tracer tracer,
                                                    String componentName,
                                                    StreamingHttpConnection next,
                                                    boolean validateTraceKeyFormat) {
        super(next);
        this.tracer = requireNonNull(tracer);
        this.componentName = requireNonNull(componentName);
        formatter = validateTraceKeyFormat ? FORMATTER_VALIDATION : FORMATTER_NO_VALIDATION;
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        SpanBuilder spanBuilder = tracer.buildSpan(componentName)
                .withTag(SPAN_KIND.getKey(), SPAN_KIND_CLIENT)
                .withTag(HTTP_METHOD.getKey(), request.method().getName())
                .withTag(HTTP_URL.getKey(), request.path());
        Scope currentScope = tracer.scopeManager().active();
        if (currentScope != null) {
            spanBuilder = spanBuilder.asChildOf(currentScope.span());
        }

        Scope childScope = spanBuilder.startActive(true);
        tracer.inject(childScope.span().context(), formatter, request.headers());
        return getDelegate().request(request).map(resp -> resp.transformRawPayloadBody(pub ->
                pub.doOnError(cause -> tagErrorAndClose(childScope))
                   .doOnCancel(() -> tagErrorAndClose(childScope))
                   .doOnComplete(() -> {
                       HTTP_STATUS.set(childScope.span(), resp.status().code());
                       try {
                           if (isError(resp)) {
                               ERROR.set(childScope.span(), true);
                           }
                       } finally {
                           childScope.close();
                       }
                   }))
                ).doOnError(cause -> tagErrorAndClose(childScope))
                 .doOnCancel(() -> tagErrorAndClose(childScope));
    }

    /**
     * Determine if a {@link HttpResponseMetaData} should be considered an error from a tracing perspective.
     * @param metaData The {@link HttpResponseMetaData} to test.
     * @return {@code true} if the {@link HttpResponseMetaData} should be considered an error for tracing.
     */
    protected boolean isError(HttpResponseMetaData metaData) {
        return metaData.status().statusClass().equals(SERVER_ERROR_5XX);
    }
}
