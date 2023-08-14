/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HttpServicePayloadDiscardWatchdogTest {

    @Test
    void cleansPayloadBodyIfDiscardedInFilter() throws Exception {
        final AtomicBoolean payloadSubscribed = new AtomicBoolean(false);
        try (HttpServerContext serverContext = HttpServers
                .forPort(0)
                .appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        return delegate()
                                .handle(ctx, request, responseFactory)
                                .beforeOnSuccess(res -> res.transformPayloadBody(payload -> Publisher.empty()));
                    }
                })
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    final Publisher<Buffer> buffer = Publisher
                            .from(ctx.executionContext().bufferAllocator().fromUtf8("Hello, World!"))
                            .beforeOnSubscribe(subscription -> payloadSubscribed.set(true));
                    return Single.succeeded(responseFactory.ok().payloadBody(buffer));
                })) {
            try (BlockingHttpClient client = HttpClients.forSingleAddress(
                    HostAndPort.of((InetSocketAddress) serverContext.listenAddress())).buildBlocking()) {
                HttpResponse response = client.request(client.get("/"));
                assertEquals(0, response.payloadBody().readableBytes());
            }

            assertTrue(payloadSubscribed.get());
        }
    }

    @Test
    void cleansPayloadBodyIfErrorInFilter() throws Exception {
        final AtomicBoolean payloadSubscribed = new AtomicBoolean(false);
        try (HttpServerContext serverContext = HttpServers
                .forPort(0)
                .appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        return delegate()
                                .handle(ctx, request, responseFactory)
                                .map(res -> {
                                    throw new IllegalStateException("I don't like this response!");
                                });
                    };
                })
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    final Publisher<Buffer> buffer = Publisher
                            .from(ctx.executionContext().bufferAllocator().fromUtf8("Hello, World!"))
                            .beforeOnSubscribe(subscription -> payloadSubscribed.set(true));
                    return Single.succeeded(responseFactory.ok().payloadBody(buffer));
                })) {
            try (BlockingHttpClient client = HttpClients.forSingleAddress(
                    HostAndPort.of((InetSocketAddress) serverContext.listenAddress())).buildBlocking()) {
                HttpResponse response = client.request(client.get("/"));
                assertEquals(0, response.payloadBody().readableBytes());
            }

            assertTrue(payloadSubscribed.get());
        }
    }
}
