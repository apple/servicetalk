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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslClientAuthMode;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;

final class MockOtlpCollector implements AutoCloseable {

    enum SecurityMode {
        CLEARTEXT,
        TLS,
        MUTUAL_TLS
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

        ReceivedRequest(String path, HttpHeaders headers, byte[] payload) {
            this.path = path;
            this.headers = headers;
            this.payload = payload;
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
    }

    static final class Builder {
        private SecurityMode securityMode = SecurityMode.CLEARTEXT;
        private int failFirstNRequests;
        private HttpResponseStatus failureStatus = SERVICE_UNAVAILABLE;
        @Nullable
        private Duration responseDelay;

        Builder securityMode(SecurityMode securityMode) {
            this.securityMode = securityMode;
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

            // Configure for HTTP/1.1
            serverBuilder.protocols(HttpProtocolConfigs.h1Default());

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

            ServerContext serverContext = serverBuilder.listenStreamingAndAwait(
                    new OtlpRequestHandler(collector));

            collector.serverContext = serverContext;
            return collector;
        }
    }

    private static final class OtlpRequestHandler implements StreamingHttpService {
        private final MockOtlpCollector collector;

        OtlpRequestHandler(MockOtlpCollector collector) {
            this.collector = collector;
        }

        @Override
        public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                     StreamingHttpRequest request,
                                                     StreamingHttpResponseFactory factory) {
            // Check if we should fail this request
            if (collector.failFirstNRequests.getAndDecrement() > 0) {
                return succeeded(factory.newResponse(collector.failureStatus));
            }

            // Add delay if configured
            if (collector.responseDelay != null) {
                // Delay the response
                return Single.<StreamingHttpResponse>never()
                        .timeout(collector.responseDelay)
                        .onErrorResume(t -> handleRequest(ctx, request, factory));
            } else {
                return handleRequest(ctx, request, factory);
            }
        }

        private Single<StreamingHttpResponse> handleRequest(HttpServiceContext ctx,
                                                           StreamingHttpRequest request,
                                                           StreamingHttpResponseFactory factory) {
            // Collect the request payload
            return request.payloadBody()
                    .collect(() -> ctx.executionContext().bufferAllocator().newBuffer(),
                            (accumulated, chunk) -> {
                                accumulated.writeBytes(chunk);
                                return accumulated;
                            })
                    .map(payload -> {
                        // Capture the request
                        byte[] payloadBytes = new byte[payload.readableBytes()];
                        payload.getBytes(payload.readerIndex(), payloadBytes);

                        ReceivedRequest captured = new ReceivedRequest(
                                request.path(),
                                request.headers(),
                                payloadBytes
                        );

                        collector.receivedRequests.add(captured);
                        collector.requestCount.incrementAndGet();

                        // Create success response
                        StreamingHttpResponse response = factory.ok();
                        response.headers().set(CONTENT_TYPE, "application/x-protobuf");
                        return response;
                    });
        }
    }
}
