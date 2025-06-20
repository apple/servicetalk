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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.api.TransportObservers;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.opentelemetry.http.Singletons.INSTRUMENTATION_SCOPE_NAME;

final class ConnectionInstrumenter {

    static final String CONNECTION_SPAN_NAME = "connection_setup";
    static final String TLS_SPAN_NAME = "tls_setup";

    static final AttributeKey<String> NETWORK_PEER_ADDRESS_KEY = AttributeKey.stringKey("network.peer.address");
    static final AttributeKey<Long> NETWORK_PEER_PORT_KEY = AttributeKey.longKey("network.peer.port");

    // This needs to be the 'last' filter so it captures the entire connection establishment chain.
    static final class ConnectionFactoryFilterImpl<ResolvedAddress, C extends ListenableAsyncCloseable>
            implements ConnectionFactoryFilter<ResolvedAddress, C> {

        private final Tracer tracer;

        ConnectionFactoryFilterImpl(OpenTelemetry openTelemetry) {
            this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME);
        }

        @Override
        public ConnectionFactory<ResolvedAddress, C> create(ConnectionFactory<ResolvedAddress, C> original) {

            return new DelegatingConnectionFactory<ResolvedAddress, C>(original) {
                @Override
                public Single<C> newConnection(ResolvedAddress resolvedAddress, @Nullable ContextMap context,
                                               @Nullable TransportObserver observer) {
                    Context current = Context.current();
                    if (!Span.fromContext(current).getSpanContext().isValid()) {
                        return delegate().newConnection(resolvedAddress, context, observer);
                    }
                    Span span = createSpan(current);
                    setIpAndPort(span, resolvedAddress);
                    Context nextContext = span.storeInContext(current);
                    try (Scope ignored = nextContext.makeCurrent()) {
                        TransportObserver tracingTransportObserver = new TracingTransportObserver(tracer, nextContext);
                        TransportObserver transportObserver = observer == null ? tracingTransportObserver :
                                TransportObservers.combine(observer, tracingTransportObserver);
                        Single<C> delegateResult = delegate().newConnection(resolvedAddress, context,
                                transportObserver);
                        return Singletons.withContext(
                                delegateResult.beforeFinally(new TerminalSignalConsumer() {
                                    @Override
                                    public void onComplete() {
                                        span.end();
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        span.recordException(throwable);
                                        span.end();
                                    }

                                    @Override
                                    public void cancel() {
                                        // TODO: how to signal cancel
                                        span.end();
                                    }
                                }), nextContext);
                    }
                }
            };
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            return ConnectExecutionStrategy.offloadNone();
        }

        private Span createSpan(Context context) {
            return tracer
                    .spanBuilder(CONNECTION_SPAN_NAME)
                    .setSpanKind(SpanKind.INTERNAL)
                    .setParent(context)
                    .startSpan();
        }

        private static void setIpAndPort(Span span, Object resolvedAddress) {
            if (resolvedAddress instanceof InetSocketAddress) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) resolvedAddress;
                span.setAttribute(NETWORK_PEER_ADDRESS_KEY, inetSocketAddress.getAddress().getHostAddress());
                span.setAttribute(NETWORK_PEER_PORT_KEY, inetSocketAddress.getPort());
            }
        }
    }

    private ConnectionInstrumenter() {
        // noop: no instances.
    }

    private static final class TracingTransportObserver implements TransportObserver {

        private final Tracer tracer;
        private final Context context;

        TracingTransportObserver(Tracer tracer, Context context) {
            this.tracer = tracer;
            this.context = context;
        }

        @Override
        public ConnectionObserver onNewConnection(@Nullable Object localAddress, Object remoteAddress) {
            return new TracingConnectionObserver(tracer, context);
        }
    }

    private static final class TracingConnectionObserver implements ConnectionObserver {

        private final Tracer tracer;
        private final Context context;

        TracingConnectionObserver(Tracer tracer, Context context) {
            this.tracer = tracer;
            this.context = context;
        }

        @Override
        public void onDataRead(int size) {
        }

        @Override
        public void onDataWrite(int size) {
        }

        @Override
        public void onFlush() {
        }

        @Override
        public DataObserver connectionEstablished(ConnectionInfo info) {
            return NoopDataObserver.INSTANCE;
        }

        @Override
        public MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info) {
            return NoopMultiplexObserver.INSTANCE;
        }

        @Override
        public void connectionClosed(Throwable error) {
        }

        @Override
        public void connectionClosed() {
        }

        @Override
        public SecurityHandshakeObserver onSecurityHandshake(SslConfig sslConfig) {
            return new TracingSecurityHandshakeObserver(tracer, context);
        }
    }

    private static final class TracingSecurityHandshakeObserver
            implements ConnectionObserver.SecurityHandshakeObserver {

        private final Span span;

        TracingSecurityHandshakeObserver(Tracer tracer, Context context) {
            span = tracer.spanBuilder(TLS_SPAN_NAME)
                    .setParent(context)
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
        }

        @Override
        public void handshakeFailed(Throwable cause) {
            span.recordException(cause);
            span.end();
        }

        @Override
        public void handshakeComplete(SSLSession sslSession) {
            addAttributes(sslSession);
            span.end();
        }

        private static void addAttributes(SSLSession session) {
            // TODO: what attributes should we extract?
            assert session != null;
        }
    }

    private static final class NoopMultiplexObserver implements ConnectionObserver.MultiplexedObserver {

        static final NoopMultiplexObserver INSTANCE = new NoopMultiplexObserver();

        @Override
        public ConnectionObserver.StreamObserver onNewStream() {
            return NoopStreamObserver.INSTANCE;
        }

        private static final class NoopStreamObserver implements ConnectionObserver.StreamObserver {

            static final NoopStreamObserver INSTANCE = new NoopStreamObserver();

            @Override
            public void streamIdAssigned(long streamId) {
            }

            @Override
            public ConnectionObserver.DataObserver streamEstablished() {
                return NoopDataObserver.INSTANCE;
            }

            @Override
            public void streamClosed(Throwable error) {
            }

            @Override
            public void streamClosed() {
            }
        }
    }

    private static final class NoopDataObserver implements ConnectionObserver.DataObserver {

        static final NoopDataObserver INSTANCE = new NoopDataObserver();

        @Override
        public ConnectionObserver.ReadObserver onNewRead() {
            return NoopReadObserver.INSTANCE;
        }

        @Override
        public ConnectionObserver.WriteObserver onNewWrite() {
            return NoopWriteObserver.INSTANCE;
        }

        private static final class NoopWriteObserver implements ConnectionObserver.WriteObserver {

            static final NoopWriteObserver INSTANCE = new NoopWriteObserver();

            @Override
            public void requestedToWrite(long n) {
            }

            @Override
            public void itemReceived(@Nullable Object item) {
            }

            @Override
            public void onFlushRequest() {
            }

            @Override
            public void itemWritten(@Nullable Object item) {
            }

            @Override
            public void itemFlushed() {
            }

            @Override
            public void writeFailed(Throwable cause) {
            }

            @Override
            public void writeComplete() {
            }

            @Override
            public void writeCancelled() {
            }
        }

        private static final class NoopReadObserver implements ConnectionObserver.ReadObserver {

            static final NoopReadObserver INSTANCE = new NoopReadObserver();

            @Override
            public void requestedToRead(long n) {
            }

            @Override
            public void itemRead(@Nullable Object item) {
            }

            @Override
            public void readFailed(Throwable cause) {
            }

            @Override
            public void readComplete() {
            }

            @Override
            public void readCancelled() {
            }
        }
    }
}
