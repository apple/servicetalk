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

package io.servicetalk.opentelemetry.grpc;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionInfo;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;

import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

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
public final class OpenTelemetryGrpcLifecycleObserver
    implements GrpcLifecycleObserver {

    // copied from PeerIncubatingAttributes
    private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");

    static final String INSTRUMENTATION_SCOPE_NAME = "io.servicetalk.grpc";
    private final Instrumenter<GrpcRequestInfo, GrpcTelemetryStatus> instrumenter;

    /**
     * Create a new instance.
     *
     * @param filterType the type of filter
     */
    public OpenTelemetryGrpcLifecycleObserver(FilterType filterType) {
        this(GlobalOpenTelemetry.get(), new OpenTelemetryOptions.Builder().build(), filterType);
    }

    /**
     * Create a new instance.
     *
     * @param opentelemetryOptions options for building the filter.
     * @param filterType           the type of filter
     */
    public OpenTelemetryGrpcLifecycleObserver(OpenTelemetryOptions opentelemetryOptions, FilterType filterType) {
        this(GlobalOpenTelemetry.get(), opentelemetryOptions, filterType);
    }

    /**
     * Create a new instance.
     *
     * @param openTelemetry        the {@link OpenTelemetry}.
     * @param opentelemetryOptions options for building the filter.
     * @param filterType           the type of filter
     */
    public OpenTelemetryGrpcLifecycleObserver(OpenTelemetry openTelemetry,
                                              OpenTelemetryOptions opentelemetryOptions,
                                              FilterType filterType) {
        if (filterType == FilterType.CLIENT) {
            SpanNameExtractor<GrpcRequestInfo> clientSpanNameExtractor =
                RpcSpanNameExtractor.create(ServicetalkGrpcAttributesGetter.INSTANCE);
            InstrumenterBuilder<GrpcRequestInfo, GrpcTelemetryStatus> clientInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, clientSpanNameExtractor);
            clientInstrumenterBuilder.setSpanStatusExtractor(ServicetalkSpanStatusExtractor.INSTANCE);

            clientInstrumenterBuilder
                .addAttributesExtractor(RpcClientAttributesExtractor
                    .create(ServicetalkGrpcAttributesGetter.INSTANCE))
                .addAttributesExtractor(
                    NetworkAttributesExtractor.create(ServicetalkNetClientAttributesGetter.INSTANCE));
            if (opentelemetryOptions.enableMetrics()) {
                clientInstrumenterBuilder.addOperationMetrics(RpcClientMetrics.get());
            }
            if (!opentelemetryOptions.getComponentName().isEmpty()) {
                clientInstrumenterBuilder.addAttributesExtractor(
                    AttributesExtractor.constant(PEER_SERVICE, opentelemetryOptions.getComponentName().trim()));
            }
            instrumenter =
                clientInstrumenterBuilder.buildClientInstrumenter(RequestHeadersPropagatorSetter.INSTANCE);
        } else {
            SpanNameExtractor<GrpcRequestInfo> clientSpanNameExtractor =
                RpcSpanNameExtractor.create(ServicetalkGrpcAttributesGetter.INSTANCE);
            InstrumenterBuilder<GrpcRequestInfo, GrpcTelemetryStatus> serverInstrumenterBuilder =
                Instrumenter.builder(openTelemetry, INSTRUMENTATION_SCOPE_NAME, clientSpanNameExtractor);
            serverInstrumenterBuilder.setSpanStatusExtractor(ServicetalkSpanStatusExtractor.INSTANCE);

            serverInstrumenterBuilder
                .addAttributesExtractor(RpcServerAttributesExtractor
                    .create(ServicetalkGrpcAttributesGetter.INSTANCE))
                .addAttributesExtractor(
                    NetworkAttributesExtractor.create(ServicetalkNetServerAttributesGetter.INSTANCE))
                .addAttributesExtractor(
                    new GrpcAttributesExtractor(
                        ServicetalkGrpcAttributesGetter.INSTANCE,
                        opentelemetryOptions.capturedRequestHeaders(),
                        opentelemetryOptions.capturedResponseHeaders()));
            if (opentelemetryOptions.enableMetrics()) {
                serverInstrumenterBuilder.addOperationMetrics(RpcServerMetrics.get());
            }
            instrumenter =
                serverInstrumenterBuilder.buildServerInstrumenter(RequestHeadersPropagatorGetter.INSTANCE);
        }
    }

    @Override
    public GrpcExchangeObserver onNewExchange() {
        return new OpenTelemetryGrpcLifecycle(instrumenter);
    }

    static final class OpenTelemetryGrpcLifecycle implements GrpcExchangeObserver,
        GrpcRequestObserver, GrpcResponseObserver {
        @Nullable
        private ConnectionInfo connectionInfo;
        @Nullable
        private Context context;
        @Nullable
        private Scope scope;

        @Nullable
        private GrpcRequestInfo grpcRequestInfo;
        private final GrpcTelemetryStatus.Builder statusBuilder = GrpcTelemetryStatus.newBuilder();

        private final Instrumenter<GrpcRequestInfo, GrpcTelemetryStatus> instrumenter;

        OpenTelemetryGrpcLifecycle(Instrumenter<GrpcRequestInfo, GrpcTelemetryStatus> instrumenter) {
            this.instrumenter = instrumenter;
        }

        @Override
        public void onConnectionSelected(ConnectionInfo info) {
            this.connectionInfo = info;
        }

        @Override
        public GrpcRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
            grpcRequestInfo = new GrpcRequestInfo(requestMetaData, connectionInfo);
            Context parentContext = Context.current();
            if (!instrumenter.shouldStart(parentContext, grpcRequestInfo)) {
                return this;
            }

            context = instrumenter.start(parentContext, grpcRequestInfo);
            scope = context.makeCurrent();
            return this;
        }

        @Override
        public GrpcResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
            this.statusBuilder.setResponseMetaData(responseMetaData);
            return this;
        }

        @Override
        public void onResponseData(Buffer data) {
        }

        @Override
        public void onResponseTrailers(HttpHeaders trailers) {
        }

        @Override
        public void onResponseComplete() {
        }

        @Override
        public void onResponseError(Throwable cause) {
            statusBuilder.setError(cause);
        }

        @Override
        public void onResponseCancel() {
            statusBuilder.setGrpcStatus(GrpcStatus.fromCodeValue(GrpcStatusCode.CANCELLED.value()));
        }

        @Override
        public void onExchangeFinally() {
            GrpcTelemetryStatus telemetryStatus = statusBuilder.build();
            instrumenter.end(Objects.requireNonNull(context),
                Objects.requireNonNull(grpcRequestInfo), telemetryStatus,
                telemetryStatus.getError());
            close();
        }

        @Override
        public void onRequestData(Buffer data) {
        }

        @Override
        public void onRequestTrailers(HttpHeaders trailers) {
        }

        @Override
        public void onRequestComplete() {
        }

        @Override
        public void onRequestError(Throwable cause) {
            statusBuilder.setError(cause);
        }

        @Override
        public void onRequestCancel() {
            statusBuilder.setGrpcStatus(GrpcStatus.fromCodeValue(GrpcStatusCode.CANCELLED.value()));
        }

        @Override
        public void onGrpcStatus(GrpcStatus status) {
            this.statusBuilder.setGrpcStatus(status);
        }

        private void close() {
            if (scope != null) {
                scope.close();
                scope = null;
            }
        }
    }
}
