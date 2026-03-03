/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentelemetry.client;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcHeaderValues;
import io.servicetalk.grpc.api.GrpcSerializationProvider;
import io.servicetalk.grpc.protobuf.ProtoBufSerializationProviderBuilder;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslClientAuthMode;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;

final class MockOtlpCollector implements AutoCloseable {

    enum SecurityMode {
        CLEARTEXT,
        TLS,
        MUTUAL_TLS
    }

    enum ProtocolMode {
        HTTP,
        GRPC
    }

    @Nullable
    private ServerContext serverContext;
    private final Queue<ReceivedRequest> receivedRequests = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger failFirstNRequests;
    private final HttpResponseStatus failureStatus;
    @Nullable
    private final Duration responseDelay;

    private MockOtlpCollector(int failFirstNRequests,
                              HttpResponseStatus failureStatus,
                              @Nullable Duration responseDelay) {
        this.failFirstNRequests = new AtomicInteger(failFirstNRequests);
        this.failureStatus = failureStatus;
        this.responseDelay = responseDelay;
    }

    int getPort() {
        InetSocketAddress address = (InetSocketAddress) serverContext.listenAddress();
        return address.getPort();
    }

    String getHostname() {
        InetSocketAddress address = (InetSocketAddress) serverContext.listenAddress();
        return address.getHostString();
    }

    HostAndPort getAddress() {
        return HostAndPort.of(getHostname(), getPort());
    }

    int getRequestCount() {
        return requestCount.get();
    }

    List<ReceivedRequest> getReceivedRequests() {
        return new ArrayList<>(receivedRequests);
    }

    void reset() {
        receivedRequests.clear();
        requestCount.set(0);
        failFirstNRequests.set(0);
    }

    @Override
    public void close() throws Exception {
        serverContext.close();
    }

    static final class ReceivedRequest {
        private final String path;
        private final HttpHeaders headers;
        private final byte[] payload;
        private final boolean isGrpc;
        @Nullable
        private final GrpcFrameMetadata grpcMetadata;
        @Nullable
        private final ExportTraceServiceRequest grpcMessage;

        ReceivedRequest(String path, HttpHeaders headers, byte[] payload) {
            this.path = path;
            this.headers = headers;
            this.payload = payload;
            this.isGrpc = false;
            this.grpcMetadata = null;
            this.grpcMessage = null;
        }

        ReceivedRequest(String path, HttpHeaders headers, byte[] payload,
                        boolean isGrpc, GrpcFrameMetadata grpcMetadata,
                        ExportTraceServiceRequest grpcMessage) {
            this.path = path;
            this.headers = headers;
            this.payload = payload;
            this.isGrpc = isGrpc;
            this.grpcMetadata = grpcMetadata;
            this.grpcMessage = grpcMessage;
        }

        public String getPath() {
            return path;
        }

        public HttpHeaders getHeaders() {
            return headers;
        }

        public byte[] getPayload() {
            return payload;
        }

        public boolean isGrpc() {
            return isGrpc;
        }

        @Nullable
        public ExportTraceServiceRequest getGrpcMessage() {
            return grpcMessage;
        }

        @Nullable
        public GrpcFrameMetadata getGrpcMetadata() {
            return grpcMetadata;
        }
    }

    static final class GrpcFrameMetadata {
        private final boolean compressed;
        private final int messageLength;

        GrpcFrameMetadata(boolean compressed, int messageLength) {
            this.compressed = compressed;
            this.messageLength = messageLength;
        }

        public boolean isCompressed() {
            return compressed;
        }

        public int getMessageLength() {
            return messageLength;
        }
    }

    static final class Builder {
        private SecurityMode securityMode = SecurityMode.CLEARTEXT;
        private ProtocolMode protocolMode = ProtocolMode.HTTP;
        private int failFirstNRequests;
        private HttpResponseStatus failureStatus = SERVICE_UNAVAILABLE;
        @Nullable
        private Duration responseDelay;

        Builder securityMode(SecurityMode securityMode) {
            this.securityMode = securityMode;
            return this;
        }

        Builder protocolMode(ProtocolMode protocolMode) {
            this.protocolMode = protocolMode;
            return this;
        }

        Builder failFirstNRequests(int count, HttpResponseStatus status) {
            this.failFirstNRequests = count;
            this.failureStatus = status;
            return this;
        }

        Builder responseDelay(Duration delay) {
            this.responseDelay = delay;
            return this;
        }

        MockOtlpCollector build() throws Exception {
            HttpServerBuilder serverBuilder = HttpServers.forAddress(new InetSocketAddress("localhost", 0));

            // Configure for HTTP/1.1 or HTTP/2 (for gRPC)
            if (protocolMode == ProtocolMode.GRPC) {
                serverBuilder.protocols(HttpProtocolConfigs.h2Default());
            } else {
                serverBuilder.protocols(HttpProtocolConfigs.h1Default());
            }

            // Configure SSL/TLS
            if (securityMode != SecurityMode.CLEARTEXT) {
                ServerSslConfigBuilder sslConfigBuilder = new ServerSslConfigBuilder(
                        DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey);

                if (securityMode == SecurityMode.MUTUAL_TLS) {
                    // Require client certificates
                    sslConfigBuilder.trustManager(DefaultTestCerts::loadClientCAPem)
                            .clientAuthMode(SslClientAuthMode.REQUIRE);
                }

                serverBuilder.sslConfig(sslConfigBuilder.build());
            }

            // Create the collector first so the handler and the returned instance are the same object.
            MockOtlpCollector collector = new MockOtlpCollector(failFirstNRequests, failureStatus, responseDelay);

            ServerContext serverContext = serverBuilder.listenAndAwait(
                    new OtlpRequestHandler(collector, protocolMode));

            collector.serverContext = serverContext;
            return collector;
        }
    }

    private static final class OtlpRequestHandler implements HttpService {
        private static final int GRPC_HEADER_LENGTH = 5;
        private final MockOtlpCollector collector;
        private final ProtocolMode protocolMode;

        OtlpRequestHandler(MockOtlpCollector collector, ProtocolMode protocolMode) {
            this.collector = collector;
            this.protocolMode = protocolMode;
        }

        @Override
        public Single<HttpResponse> handle(HttpServiceContext ctx,
                                           HttpRequest request,
                                           HttpResponseFactory factory) {
            // Check if we should fail this request
            if (collector.failFirstNRequests.getAndDecrement() > 0) {
                return succeeded(factory.newResponse(collector.failureStatus));
            }

            // Add delay if configured
            if (collector.responseDelay != null) {
                // Delay the response
                return ctx.executionContext().executor().timer(collector.responseDelay)
                        .concat(handleRequest(ctx, request, factory));
            } else {
                return handleRequest(ctx, request, factory);
            }
        }

        private Single<HttpResponse> handleRequest(HttpServiceContext ctx,
                                                           HttpRequest request,
                                                           HttpResponseFactory factory) {
            Buffer payload = request.payloadBody();
            byte[] payloadBytes = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), payloadBytes);

            ReceivedRequest captured;
            if (protocolMode == ProtocolMode.GRPC) {
                // Parse gRPC frame to extract the message
                captured = parseGrpcRequest(ctx, request, payload, payloadBytes);
            } else {
                captured = new ReceivedRequest(
                        request.path(),
                        request.headers(),
                        payloadBytes
                );
            }

            collector.receivedRequests.add(captured);
            collector.requestCount.incrementAndGet();

            // Create success response
            HttpResponse response = factory.ok();
            if (protocolMode == ProtocolMode.GRPC) {
                // gRPC trailers-only response (no body) with grpc-status in headers
                response.headers().set(CONTENT_TYPE, GrpcHeaderValues.APPLICATION_GRPC);
                response.headers().set("grpc-status", "0"); // 0 = OK (sent in headers for trailers-only)
            } else {
                response.headers().set(CONTENT_TYPE, "application/x-protobuf");
            }
            return succeeded(response);
        }

        private ReceivedRequest parseGrpcRequest(HttpServiceContext ctx,
                                                 HttpRequestMetaData request,
                                                 Buffer payload,
                                                 byte[] payloadBytes) {
            try {
                if (payload.readableBytes() < GRPC_HEADER_LENGTH) {
                    throw new IllegalStateException("Not enough data for grpc message: " + payload.readableBytes());
                }
                // First do a lightweight frame header validation to extract metadata
                byte compressionFlag = payload.getByte(payload.readerIndex());
                if (compressionFlag != 0x0 && compressionFlag != 0x1) {
                    throw new IllegalArgumentException("Compression flag must be 0 or 1 but was: " + compressionFlag);
                }
                boolean compressed = compressionFlag == 0x1;
                int frameLength = payload.getInt(payload.readerIndex() + 1);
                if (frameLength < 0) {
                    throw new IllegalArgumentException("Message-Length invalid: " + frameLength);
                } else if (frameLength != payload.readableBytes() - 5) {
                    throw new IllegalStateException("Invalid message size. Expected " + frameLength);
                }

                GrpcFrameMetadata metadata = new GrpcFrameMetadata(compressed, frameLength);

                // Now validate using ServiceTalk's actual gRPC deserialization
                // This will throw if the framing doesn't match what ServiceTalk expects
                io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest validated =
                        validateAndDecodeGrpcFrame(
                                payloadBytes,
                                io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest.parser(),
                                ctx.executionContext().bufferAllocator());
                return new ReceivedRequest(
                        request.path(),
                        request.headers(),
                        payloadBytes,
                        true,
                        metadata,
                        validated
                );
            } catch (Exception e) {
                // If ServiceTalk's gRPC deserialization fails, the framing is invalid
                throw new RuntimeException("Failed to validate gRPC frame using ServiceTalk's deserialization - " +
                        "framing is invalid", e);
            }
        }

        private static <T extends MessageLite> T validateAndDecodeGrpcFrame(
                byte[] payload,
                Parser<T> parser,
                BufferAllocator allocator) throws Exception {
            @SuppressWarnings("unchecked")
            Class<T> messageClass = (Class<T>) parser.parseFrom(new byte[0]).getClass();
            GrpcSerializationProvider grpcSerializationProvider = new ProtoBufSerializationProviderBuilder()
                    .registerMessageType(messageClass, parser)
                    .build();
            HttpDeserializer<T> httpDeserializer = grpcSerializationProvider.deserializerFor(identity(), messageClass);
            Buffer buffer = allocator.wrap(payload);
            return httpDeserializer.deserialize(DefaultHttpHeadersFactory.INSTANCE.newHeaders(), buffer);
        }
    }
}
