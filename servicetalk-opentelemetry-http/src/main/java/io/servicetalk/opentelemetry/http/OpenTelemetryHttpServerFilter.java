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
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;

import java.util.function.UnaryOperator;

/**
 * A {@link StreamingHttpService} that supports
 * <a href="https://opentelemetry.io/docs/instrumentation/java/">open telemetry</a>.
 * <p>
 * The filter gets a {@link Tracer} with {@value #INSTRUMENTATION_SCOPE_NAME} instrumentation scope name.
 * <p>
 * Append this filter before others that are expected to see {@link Scope} for this request/response. Filters
 * appended after this filter that use operators with the <strong>after*</strong> prefix on
 * {@link StreamingHttpService#handle(HttpServiceContext, StreamingHttpRequest, StreamingHttpResponseFactory)
 * response meta data} or the {@link StreamingHttpResponse#transformMessageBody(UnaryOperator) response message body}
 * (e.g. {@link Publisher#afterFinally(Runnable)}) will execute after this filter invokes {@link Scope#close()} and
 * therefore will not see the {@link Span} for the current request/response.
 */
public final class OpenTelemetryHttpServerFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpServiceFilterFactory {
    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

    /**
     * Create a new instance.
     *
     * @param openTelemetry the {@link OpenTelemetry}.
     * @deprecated this method is internal, no user should be setting the {@link OpenTelemetry} as it is obtained by
     * using {@link GlobalOpenTelemetry#get()} and there should be no other implementations but the one available in
     * the classpath, this constructor will be removed in the future releases.
     * Use {@link #OpenTelemetryHttpServerFilter(OpenTelemetryOptions)} or {@link #OpenTelemetryHttpServerFilter()}
     * instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    @SuppressWarnings("DeprecatedIsStillUsed")
    public OpenTelemetryHttpServerFilter(final OpenTelemetry openTelemetry) {
        this(openTelemetry, DEFAULT_OPTIONS);
    }

    /**
     * Create a new Instance, searching for any instance of an opentelemetry available.
     */
    public OpenTelemetryHttpServerFilter() {
        this(DEFAULT_OPTIONS);
    }

    /**
     * Create a new instance.
     *
     * @param opentelemetryOptions extra options to create the opentelemetry filter
     */
    public OpenTelemetryHttpServerFilter(final OpenTelemetryOptions opentelemetryOptions) {
        this(GlobalOpenTelemetry.get(), opentelemetryOptions);
    }

    OpenTelemetryHttpServerFilter(final OpenTelemetry openTelemetry, final OpenTelemetryOptions opentelemetryOptions) {
        SpanNameExtractor<HttpRequestMetaData> serverSpanNameExtractor =
                HttpSpanNameExtractor.create(ServiceTalkHttpAttributesGetter.SERVER_INSTANCE);
        InstrumenterBuilder<HttpRequestMetaData, HttpResponseMetaData> serverInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, serverSpanNameExtractor);
        serverInstrumenterBuilder.setSpanStatusExtractor(ServicetalkSpanStatusExtractor.INSTANCE);

        serverInstrumenterBuilder
                .addAttributesExtractor(HttpServerAttributesExtractor
                        .builder(ServiceTalkHttpAttributesGetter.SERVER_INSTANCE,
                                ServiceTalkNetAttributesGetter.SERVER_INSTANCE)
                        .setCapturedRequestHeaders(opentelemetryOptions.capturedRequestHeaders())
                        .setCapturedResponseHeaders(opentelemetryOptions.capturedResponseHeaders())
                        .build());
        if (opentelemetryOptions.enableMetrics()) {
            serverInstrumenterBuilder.addOperationMetrics(HttpServerMetrics.get());
        }
        instrumenter =
                serverInstrumenterBuilder.buildServerInstrumenter(RequestHeadersPropagatorGetter.INSTANCE);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
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
        final Context parentContext = Context.root();
        if (!instrumenter.shouldStart(parentContext, request)) {
            return delegate.handle(ctx, request, responseFactory);
        }
        final Context context = instrumenter.start(parentContext, request);
        try (Scope unused = context.makeCurrent()) {
            System.err.println("Initiated context: " + Span.current().getSpanContext());
            final ScopeTrackerV2 tracker = new ScopeTrackerV2(context, request, instrumenter);
            try {
                Single<StreamingHttpResponse> response = delegate.handle(ctx, request, responseFactory);
                // We attach this before the response resolves, because this has give the request time to
                // traverse the entire pipeline. That means our transformation will happen as the last layer
                // TODO: this is a concurrency bug: we've given away our request but are now modifying it 'later'
                //  which could happen concurrently with some other modifications.
                response = response.beforeFinally(() -> request.transformMessageBody(body ->
                        transformBody(body, context).afterFinally(tracker::requestComplete)));
                return withContext(tracker.track(response), context);
            } catch (Throwable t) {
                tracker.onError(t);
                return Single.failed(t);
            }
        }
    }
}
