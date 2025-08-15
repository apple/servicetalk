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
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.api.CharSequences.equalsIgnoreCaseLower;
import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.INSTRUMENTATION_SCOPE_NAME;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.PEER_SERVICE;
import static io.servicetalk.opentelemetry.http.AbstractOpenTelemetryFilter.withContext;

/**
 * Helper class that encapsulates gRPC-specific OpenTelemetry instrumentation logic.
 * <p>
 * This helper handles the creation of gRPC instrumenters and provides methods to track gRPC
 * requests with proper span lifecycle management and gRPC semantic conventions.
 */
final class GrpcInstrumentationHelper extends InstrumentationHelper {

    private static final CharSequence GRPC_CONTENT_TYPE = newAsciiString("application/grpc");

    private final Instrumenter<RequestInfo, GrpcTelemetryStatus> instrumenter;
    private final boolean isClient;

    private GrpcInstrumentationHelper(boolean isClient, Instrumenter<RequestInfo, GrpcTelemetryStatus> instrumenter,
                                      boolean ignoreSpanSuppression) {
        super(instrumenter, ignoreSpanSuppression);
        this.instrumenter = instrumenter;
        this.isClient = isClient;
    }

    /**
     * Tracks a gRPC request using gRPC-specific OpenTelemetry instrumentation.
     *
     * @param requestHandler function to execute the actual request
     * @param requestInfo the gRPC request and connection info
     * @param parentContext the currently active context
     * @return instrumented response single
     */
    @Override
    Single<StreamingHttpResponse> doTrackRequest(
            Function<StreamingHttpRequest, Single<StreamingHttpResponse>> requestHandler,
            RequestInfo requestInfo,
            Context parentContext) {
        final Context context = instrumenter.start(parentContext, requestInfo);
        try (Scope unused = context.makeCurrent()) {
            final GrpcScopeTracker tracker = isClient ?
                    GrpcScopeTracker.client(context, requestInfo, instrumenter) :
                    GrpcScopeTracker.server(context, requestInfo, instrumenter);
            try {
                Single<StreamingHttpResponse> response =
                        requestHandler.apply(requestInfo.request());
                return withContext(tracker.track(response), context);
            } catch (Throwable t) {
                tracker.onError(t);
                return Single.failed(t);
            }
        }
    }

    /**
     * Creates a gRPC server instrumentation helper.
     *
     * @param builder OpenTelemetryHttpServiceFilter configuration options
     * @return server instrumentation helper
     */
    static GrpcInstrumentationHelper forServer(OpenTelemetryHttpServiceFilter.Builder builder) {
        OpenTelemetry openTelemetry = builder.openTelemetry;
        SpanNameExtractor<RequestInfo> serverSpanNameExtractor = GrpcSpanNameExtractor.INSTANCE;
        InstrumenterBuilder<RequestInfo, GrpcTelemetryStatus> serverInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, serverSpanNameExtractor);
        serverInstrumenterBuilder
                .setSpanStatusExtractor(GrpcSpanStatusExtractor.SERVER_INSTANCE)
                .addAttributesExtractor(new GrpcServerAttributesExtractor(builder));
        if (builder.enableMetrics) {
            serverInstrumenterBuilder.addOperationMetrics(HttpServerMetrics.get());
        }

        Instrumenter<RequestInfo, GrpcTelemetryStatus> instrumenter =
                serverInstrumenterBuilder.buildServerInstrumenter(
                        RequestHeadersPropagatorGetter.INSTANCE);

        return new GrpcInstrumentationHelper(false, instrumenter, builder.ignoreSpanSuppression);
    }

    /**
     * Creates a gRPC client instrumentation helper.
     *
     * @param builder OpenTelemetry configuration options
     * @return client instrumentation helper
     */
    static GrpcInstrumentationHelper forClient(OpenTelemetryHttpRequesterFilter.Builder builder) {
        OpenTelemetry openTelemetry = builder.openTelemetry;
        SpanNameExtractor<RequestInfo> clientSpanNameExtractor = GrpcSpanNameExtractor.INSTANCE;
        InstrumenterBuilder<RequestInfo, GrpcTelemetryStatus> clientInstrumenterBuilder =
                Instrumenter.builder(
                        openTelemetry, INSTRUMENTATION_SCOPE_NAME, clientSpanNameExtractor);
        clientInstrumenterBuilder
                .setSpanStatusExtractor(GrpcSpanStatusExtractor.CLIENT_INSTANCE)
                .addAttributesExtractor(new DeferredGrpcClientAttributesExtractor(builder));

        if (builder.enableMetrics) {
            clientInstrumenterBuilder.addOperationMetrics(HttpClientMetrics.get());
        }
        String componentName = builder.componentName;
        if (!componentName.isEmpty()) {
            clientInstrumenterBuilder.addAttributesExtractor(
                    AttributesExtractor.constant(PEER_SERVICE, componentName));
        }

        Instrumenter<RequestInfo, GrpcTelemetryStatus> instrumenter =
                clientInstrumenterBuilder.buildClientInstrumenter(
                        RequestHeadersPropagatorSetter.INSTANCE);

        return new GrpcInstrumentationHelper(true, instrumenter, builder.ignoreSpanSuppression);
    }

    /**
     * Determines if a request should be treated as a gRPC request.
     * <p>
     * A request is considered gRPC if the Content-Type header starts with "application/grpc"
     *
     * @param request the HTTP request to examine
     * @return true if this should be treated as a gRPC request, false for HTTP
     */
    boolean isGrpcRequest(StreamingHttpRequest request) {
        CharSequence contentType = request.headers().get(CONTENT_TYPE);
        return contentType != null && startsWithPrefix(contentType);
    }

    private static boolean startsWithPrefix(CharSequence charSequence) {
        int seqLength = charSequence.length();
        if (seqLength < GRPC_CONTENT_TYPE.length()) {
            return false;
        }
        if (seqLength == GRPC_CONTENT_TYPE.length()) {
            return contentEqualsIgnoreCase(GRPC_CONTENT_TYPE, charSequence);
        }
        // Start at the end since many content types start with 'application/' so we're more likely
        // to be able to abort early by checking that the prefix ends with 'grpc'.
        for (int i = GRPC_CONTENT_TYPE.length() - 1; i >= 0; i--) {
            if (!equalsIgnoreCaseLower(charSequence.charAt(i), GRPC_CONTENT_TYPE.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class DeferredGrpcClientAttributesExtractor
            implements AttributesExtractor<RequestInfo, GrpcTelemetryStatus> {

        private final AttributesExtractor<RequestInfo, GrpcTelemetryStatus> delegate;

        DeferredGrpcClientAttributesExtractor(OpenTelemetryHttpRequesterFilter.Builder builder) {
            this.delegate = new GrpcClientAttributesExtractor(builder);
        }

        @Override
        public void onStart(AttributesBuilder attributes,
                            Context parentContext,
                            RequestInfo requestInfo) {
            // noop: we will defer this until the `onEnd` call.
        }

        @Override
        public void onEnd(AttributesBuilder attributes,
                          Context context,
                          RequestInfo requestInfo,
                          @Nullable GrpcTelemetryStatus telemetryStatus,
                          @Nullable Throwable error) {
            delegate.onStart(attributes, context, requestInfo);
            delegate.onEnd(attributes, context, requestInfo, telemetryStatus, error);
        }
    }
}
