/*
 * Copyright © 2018, 2020, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNever;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.immediate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpAuthConnectionFactoryClientTest {

    @RegisterExtension
    static final ExecutionContextExtension CTX =
        immediate();

    @Nullable
    private StreamingHttpClient client;
    @Nullable
    private ServerContext serverContext;

    @AfterEach
    void teardown() throws Exception {
        if (client != null) {
            client.closeAsync().toFuture().get();
        }
        if (serverContext != null) {
            serverContext.closeAsync().toFuture().get();
        }
    }

    @Test
    void simulateAuth() throws Exception {
        serverContext = forAddress(localAddress(0))
            .ioExecutor(CTX.ioExecutor())
            .executionStrategy(offloadNever())
            .listenStreamingAndAwait((ctx, request, factory) -> succeeded(newTestResponse(factory)));

        client = forSingleAddress(serverHostAndPort(serverContext))
            .appendConnectionFactoryFilter(TestHttpAuthConnectionFactory::new)
            .ioExecutor(CTX.ioExecutor())
            .executionStrategy(offloadNever())
            .buildStreaming();

        StreamingHttpResponse response = client.request(newTestRequest(client, "/foo")).toFuture().get();
        assertEquals(OK, response.status());
    }

    private static final class TestHttpAuthConnectionFactory<ResolvedAddress,
            C extends FilterableStreamingHttpConnection>
            extends DelegatingConnectionFactory<ResolvedAddress, C> {

        TestHttpAuthConnectionFactory(final ConnectionFactory<ResolvedAddress, C> delegate) {
            super(delegate);
        }

        @Override
        public Single<C> newConnection(
                final ResolvedAddress resolvedAddress, @Nullable final TransportObserver observer) {
            return super.newConnection(resolvedAddress, observer).flatMap(cnx ->
                    cnx.request(newTestRequest(cnx, "/auth"))
                            .onErrorResume(cause -> {
                                cnx.closeAsync().subscribe();
                                return failed(new IllegalStateException(
                                        "failed auth"));
                            })
                            .flatMap(response -> {
                                if (OK.equals(response.status())) {
                                    // In this test we have not enabled pipelining so we drain this response before
                                    // indicating the connection is usable.
                                    return response.messageBody()
                                            .ignoreElements()
                                            .concat(succeeded(cnx));
                                }
                                cnx.closeAsync().subscribe();
                                return failed(new IllegalStateException(
                                        "failed auth"));
                            })
            );
        }
    }

    private static StreamingHttpRequest newTestRequest(StreamingHttpRequestFactory factory, String requestTarget) {
        StreamingHttpRequest req = factory.get(requestTarget);
        req.headers().set(CONTENT_LENGTH, ZERO);
        return req;
    }

    private static StreamingHttpResponse newTestResponse(StreamingHttpResponseFactory factory) {
        StreamingHttpResponse resp = factory.ok();
        resp.headers().set(CONTENT_LENGTH, ZERO);
        return resp;
    }
}
